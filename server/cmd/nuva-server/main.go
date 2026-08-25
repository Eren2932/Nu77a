// Nuva API server.
//
// One binary, one process, no hidden state. Everything it needs comes from the
// environment (see .env.example) and it refuses to start if anything is
// missing.
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/nuva/server/internal/api"
	"github.com/nuva/server/internal/auth"
	"github.com/nuva/server/internal/config"
	"github.com/nuva/server/internal/store"
	"github.com/nuva/server/internal/ws"
)

// Injected at build time by the CI workflow via -ldflags.
var (
	version = "dev"
	commit  = "none"
	builtAt = "unknown"
)

func main() {
	if err := run(); err != nil {
		slog.Error("fatal", "err", err)
		os.Exit(1)
	}
}

func run() error {
	cfg, err := config.Load()
	if err != nil {
		// Logger is not configured yet, so print plainly and die.
		return fmt.Errorf("configuration error: %w", err)
	}
	setupLogger(cfg)

	slog.Info("starting nuva server",
		"version", version, "commit", commit, "env", cfg.Env, "port", cfg.Port)

	rootCtx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	db, err := store.Open(rootCtx, cfg.DatabaseURL)
	if err != nil {
		return err
	}
	defer db.Close()

	if err := db.WaitForDatabase(rootCtx, 30, 2*time.Second); err != nil {
		return err
	}
	applied, err := db.Migrate(rootCtx)
	if err != nil {
		return fmt.Errorf("migrations failed: %w", err)
	}
	if len(applied) > 0 {
		slog.Info("migrations applied", "files", applied)
	} else {
		slog.Info("database schema up to date")
	}

	if err := os.MkdirAll(cfg.MediaDir, 0o755); err != nil {
		return fmt.Errorf("create media dir %s: %w", cfg.MediaDir, err)
	}

	tokens := auth.NewTokenService(cfg.JWTSecret, cfg.AccessTTL, cfg.RefreshTTL)
	hub := ws.NewHub()

	server := api.NewServer(cfg, db, tokens, hub, api.BuildInfo{
		Version: version, Commit: commit, BuiltAt: builtAt,
	})

	httpServer := &http.Server{
		Addr:              fmt.Sprintf(":%d", cfg.Port),
		Handler:           server.Router(),
		ReadHeaderTimeout: 10 * time.Second,
		// No WriteTimeout: it would kill long-lived WebSocket connections.
		IdleTimeout: 120 * time.Second,
	}

	go startSessionJanitor(rootCtx, db)

	errCh := make(chan error, 1)
	go func() {
		slog.Info("http server listening", "addr", httpServer.Addr)
		if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			errCh <- err
		}
	}()

	select {
	case err := <-errCh:
		return err
	case <-rootCtx.Done():
		slog.Info("shutdown signal received")
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	hub.CloseAll()
	if err := httpServer.Shutdown(shutdownCtx); err != nil {
		return fmt.Errorf("graceful shutdown: %w", err)
	}
	slog.Info("bye")
	return nil
}

func setupLogger(cfg config.Config) {
	level := slog.LevelDebug
	if cfg.IsProd() {
		level = slog.LevelInfo
	}
	handler := slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: level})
	slog.SetDefault(slog.New(handler))
}

// startSessionJanitor drops long-expired refresh sessions once a day.
func startSessionJanitor(ctx context.Context, db *store.Store) {
	ticker := time.NewTicker(24 * time.Hour)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			n, err := db.DeleteExpiredSessions(ctx)
			if err != nil {
				slog.Warn("session cleanup failed", "err", err)
				continue
			}
			if n > 0 {
				slog.Info("expired sessions removed", "count", n)
			}
		}
	}
}
