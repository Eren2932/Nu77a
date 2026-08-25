package api

import (
	"errors"
	"log/slog"
	"net/http"
	"strings"

	"github.com/go-chi/chi/v5"
	"github.com/nuva/server/internal/store"
)

func (s *Server) handleGetMe(w http.ResponseWriter, r *http.Request) {
	userID, ok := UserIDFrom(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "invalid_token", "token is invalid")
		return
	}
	user, err := s.db.UserByID(r.Context(), userID)
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusUnauthorized, "user_gone", "this account no longer exists")
		return
	}
	if err != nil {
		s.internalError(w, "load me", err)
		return
	}
	writeJSON(w, http.StatusOK, user)
}

type patchMeRequest struct {
	DisplayName *string `json:"display_name"`
	Bio         *string `json:"bio"`
	AvatarURL   *string `json:"avatar_url"`
}

func (s *Server) handlePatchMe(w http.ResponseWriter, r *http.Request) {
	userID, ok := UserIDFrom(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "invalid_token", "token is invalid")
		return
	}

	var req patchMeRequest
	if !decodeJSON(w, r, &req) {
		return
	}

	if req.DisplayName != nil {
		trimmed := strings.TrimSpace(*req.DisplayName)
		if trimmed == "" || len([]rune(trimmed)) > 48 {
			writeError(w, http.StatusUnprocessableEntity, "invalid_display_name",
				"display name must be 1-48 characters")
			return
		}
		req.DisplayName = &trimmed
	}
	if req.Bio != nil && len([]rune(*req.Bio)) > 280 {
		writeError(w, http.StatusUnprocessableEntity, "invalid_bio", "bio must be at most 280 characters")
		return
	}

	user, err := s.db.UpdateProfile(r.Context(), userID, store.UpdateProfileParams{
		DisplayName: req.DisplayName,
		Bio:         req.Bio,
		AvatarURL:   req.AvatarURL,
	})
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusUnauthorized, "user_gone", "this account no longer exists")
		return
	}
	if err != nil {
		s.internalError(w, "update profile", err)
		return
	}
	writeJSON(w, http.StatusOK, user)
}

// publicUser is what other people are allowed to see about a user.
type publicUser struct {
	ID          string `json:"id"`
	Username    string `json:"username"`
	DisplayName string `json:"display_name"`
	Bio         string `json:"bio"`
	AvatarURL   string `json:"avatar_url"`
	Online      bool   `json:"online"`
}

func (s *Server) handleGetUserByUsername(w http.ResponseWriter, r *http.Request) {
	username := chi.URLParam(r, "username")
	user, err := s.db.UserByUsername(r.Context(), username)
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, "user_not_found", "no user with this username")
		return
	}
	if err != nil {
		s.internalError(w, "lookup user", err)
		return
	}
	writeJSON(w, http.StatusOK, publicUser{
		ID:          user.ID.String(),
		Username:    user.Username,
		DisplayName: user.DisplayName,
		Bio:         user.Bio,
		AvatarURL:   user.AvatarURL,
		Online:      s.hub.IsOnline(user.ID),
	})
}

// internalError logs the real cause and returns an opaque message to the
// client. Internal details never travel to the app.
func (s *Server) internalError(w http.ResponseWriter, action string, err error) {
	slog.Error("internal error", "action", action, "err", err)
	writeError(w, http.StatusInternalServerError, "internal_error", "something went wrong on our side")
}
