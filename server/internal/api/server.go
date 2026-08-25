package api

import (
	"context"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"

	"github.com/nuva/server/internal/auth"
	"github.com/nuva/server/internal/config"
	"github.com/nuva/server/internal/store"
	"github.com/nuva/server/internal/ws"
)

// APIVersion is part of every path. Old clients must keep working after a
// deploy, so breaking changes go to /v2, never into /v1.
const APIVersion = "v1"

type Server struct {
	cfg    config.Config
	db     *store.Store
	tokens *auth.TokenService
	hub    *ws.Hub
	build  BuildInfo
}

type BuildInfo struct {
	Version   string `json:"version"`
	Commit    string `json:"commit"`
	BuiltAt   string `json:"built_at"`
	StartedAt string `json:"started_at"`
}

func NewServer(cfg config.Config, db *store.Store, tokens *auth.TokenService, hub *ws.Hub, build BuildInfo) *Server {
	build.StartedAt = time.Now().UTC().Format(time.RFC3339)
	return &Server{cfg: cfg, db: db, tokens: tokens, hub: hub, build: build}
}

func (s *Server) Router() http.Handler {
	r := chi.NewRouter()

	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(middleware.Recoverer)
	r.Use(requestLogger)
	r.Use(securityHeaders)
	r.Use(middleware.Timeout(30 * time.Second))
	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   s.cfg.AllowedOrigins,
		AllowedMethods:   []string{"GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"Accept", "Authorization", "Content-Type", "X-Nuva-Client"},
		ExposedHeaders:   []string{"X-Request-Id"},
		AllowCredentials: false,
		MaxAge:           300,
	}))

	// Liveness: the process is up. Readiness: the process can serve traffic.
	r.Get("/healthz", s.handleHealth)
	r.Get("/readyz", s.handleReady)

	r.Route("/"+APIVersion, func(r chi.Router) {
		r.Get("/meta", s.handleMeta)

		r.Route("/auth", func(r chi.Router) {
			r.Post("/register", s.handleRegister)
			r.Post("/login", s.handleLogin)
			r.Post("/refresh", s.handleRefresh)
			r.Post("/logout", s.handleLogout)
			r.Post("/recover", s.handleRecover)
		})

		r.Group(func(r chi.Router) {
			r.Use(s.RequireAuth)
			r.Get("/me", s.handleGetMe)
			r.Patch("/me", s.handlePatchMe)
			r.Get("/users/{username}", s.handleGetUserByUsername)

			// Media is authenticated on both sides: uploads cost disk, and a
			// voice note is as private as the message that carries it.
			r.Post("/media", s.handleUploadMedia)
			r.Get("/media/{id}", s.handleGetMedia)

			r.Get("/ws", s.handleWebSocket)
		})
	})

	r.NotFound(func(w http.ResponseWriter, r *http.Request) {
		writeError(w, http.StatusNotFound, "not_found", "unknown endpoint")
	})
	r.MethodNotAllowed(func(w http.ResponseWriter, r *http.Request) {
		writeError(w, http.StatusMethodNotAllowed, "method_not_allowed", "method not allowed for this endpoint")
	})

	return r
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{"status": "ok"})
}

func (s *Server) handleReady(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)
	defer cancel()

	if err := s.db.Ping(ctx); err != nil {
		writeError(w, http.StatusServiceUnavailable, "db_unavailable", "database is not reachable")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"status": "ready"})
}

func (s *Server) handleMeta(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"api_version":         APIVersion,
		"build":               s.build,
		"min_supported_app":   1,
		"max_upload_bytes":    s.cfg.MaxUploadBytes,
		"voice_max_seconds":   0, // 0 = no limit, unlike the Firebase days
		"online_users":        s.hub.OnlineCount(),
	})
}
