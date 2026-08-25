package api

import (
	"context"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/go-chi/chi/v5/middleware"
	"github.com/google/uuid"
)

type ctxKey string

const (
	ctxKeyUserID    ctxKey = "nuva.user_id"
	ctxKeySessionID ctxKey = "nuva.session_id"
)

func UserIDFrom(ctx context.Context) (uuid.UUID, bool) {
	id, ok := ctx.Value(ctxKeyUserID).(uuid.UUID)
	return id, ok
}

func SessionIDFrom(ctx context.Context) (uuid.UUID, bool) {
	id, ok := ctx.Value(ctxKeySessionID).(uuid.UUID)
	return id, ok
}

// bearerToken accepts the token from the Authorization header, or from the
// query string for WebSocket upgrades where headers cannot be set by the
// browser API.
func bearerToken(r *http.Request) string {
	h := r.Header.Get("Authorization")
	if after, ok := cutPrefixFold(h, "bearer "); ok {
		return strings.TrimSpace(after)
	}
	return strings.TrimSpace(r.URL.Query().Get("access_token"))
}

func cutPrefixFold(s, prefix string) (string, bool) {
	if len(s) >= len(prefix) && strings.EqualFold(s[:len(prefix)], prefix) {
		return s[len(prefix):], true
	}
	return s, false
}

// RequireAuth rejects the request unless it carries a valid access token.
func (s *Server) RequireAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		raw := bearerToken(r)
		if raw == "" {
			writeError(w, http.StatusUnauthorized, "missing_token", "authorization token is required")
			return
		}
		userID, sessionID, err := s.tokens.ParseAccessToken(raw)
		if err != nil {
			writeError(w, http.StatusUnauthorized, "invalid_token", "token is invalid or expired")
			return
		}
		ctx := context.WithValue(r.Context(), ctxKeyUserID, userID)
		ctx = context.WithValue(ctx, ctxKeySessionID, sessionID)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// requestLogger logs one structured line per request. No bodies, no tokens.
func requestLogger(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		ww := middleware.NewWrapResponseWriter(w, r.ProtoMajor)
		next.ServeHTTP(ww, r)

		slog.Info("http",
			"method", r.Method,
			"path", r.URL.Path,
			"status", ww.Status(),
			"bytes", ww.BytesWritten(),
			"duration_ms", time.Since(start).Milliseconds(),
			"request_id", middleware.GetReqID(r.Context()),
		)
	})
}

func securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("X-Frame-Options", "DENY")
		w.Header().Set("Referrer-Policy", "no-referrer")
		next.ServeHTTP(w, r)
	})
}
