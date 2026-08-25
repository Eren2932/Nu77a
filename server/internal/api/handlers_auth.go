package api

import (
	"errors"
	"net/http"
	"regexp"
	"strings"
	"time"

	"github.com/nuva/server/internal/auth"
	"github.com/nuva/server/internal/store"
)

var usernameRe = regexp.MustCompile(`^[a-zA-Z0-9_]{3,24}$`)

type authResponse struct {
	User         store.User `json:"user"`
	AccessToken  string     `json:"access_token"`
	RefreshToken string     `json:"refresh_token"`
	ExpiresAt    time.Time  `json:"expires_at"`
	RecoveryCode string     `json:"recovery_code,omitempty"`
}

type registerRequest struct {
	Username    string `json:"username"`
	DisplayName string `json:"display_name"`
	Password    string `json:"password"`
	DeviceName  string `json:"device_name"`
	Platform    string `json:"platform"`
}

func (s *Server) handleRegister(w http.ResponseWriter, r *http.Request) {
	var req registerRequest
	if !decodeJSON(w, r, &req) {
		return
	}

	req.Username = strings.TrimSpace(req.Username)
	req.DisplayName = strings.TrimSpace(req.DisplayName)
	if req.DisplayName == "" {
		req.DisplayName = req.Username
	}

	if !usernameRe.MatchString(req.Username) {
		writeError(w, http.StatusUnprocessableEntity, "invalid_username",
			"username must be 3-24 characters: latin letters, digits or underscore")
		return
	}
	if len([]rune(req.DisplayName)) > 48 {
		writeError(w, http.StatusUnprocessableEntity, "invalid_display_name", "display name is too long")
		return
	}

	passwordHash, err := auth.HashSecret(req.Password)
	if err != nil {
		writeError(w, http.StatusUnprocessableEntity, "weak_password",
			"password must be at least 8 characters and at most 72 bytes")
		return
	}

	recoveryCode, err := auth.NewRecoveryCode()
	if err != nil {
		s.internalError(w, "generate recovery code", err)
		return
	}
	recoveryHash, err := auth.HashSecret(auth.NormalizeRecoveryCode(recoveryCode))
	if err != nil {
		s.internalError(w, "hash recovery code", err)
		return
	}

	user, err := s.db.CreateUser(r.Context(), store.CreateUserParams{
		Username:     req.Username,
		DisplayName:  req.DisplayName,
		PasswordHash: passwordHash,
		RecoveryHash: recoveryHash,
	})
	if errors.Is(err, store.ErrConflict) {
		writeError(w, http.StatusConflict, "username_taken", "this username is already taken")
		return
	}
	if err != nil {
		s.internalError(w, "create user", err)
		return
	}

	resp, err := s.issueSession(r, user, req.DeviceName, req.Platform)
	if err != nil {
		s.internalError(w, "issue session", err)
		return
	}
	// The only moment the recovery code ever leaves the server.
	resp.RecoveryCode = recoveryCode
	writeJSON(w, http.StatusCreated, resp)
}

type loginRequest struct {
	Username   string `json:"username"`
	Password   string `json:"password"`
	DeviceName string `json:"device_name"`
	Platform   string `json:"platform"`
}

func (s *Server) handleLogin(w http.ResponseWriter, r *http.Request) {
	var req loginRequest
	if !decodeJSON(w, r, &req) {
		return
	}

	user, err := s.db.UserByUsername(r.Context(), req.Username)
	if errors.Is(err, store.ErrNotFound) {
		// Same answer as a wrong password: no username enumeration.
		writeError(w, http.StatusUnauthorized, "invalid_credentials", "wrong username or password")
		return
	}
	if err != nil {
		s.internalError(w, "lookup user", err)
		return
	}
	if err := auth.VerifySecret(user.PasswordHash, req.Password); err != nil {
		writeError(w, http.StatusUnauthorized, "invalid_credentials", "wrong username or password")
		return
	}

	resp, err := s.issueSession(r, user, req.DeviceName, req.Platform)
	if err != nil {
		s.internalError(w, "issue session", err)
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

type refreshRequest struct {
	RefreshToken string `json:"refresh_token"`
}

func (s *Server) handleRefresh(w http.ResponseWriter, r *http.Request) {
	var req refreshRequest
	if !decodeJSON(w, r, &req) {
		return
	}
	if req.RefreshToken == "" {
		writeError(w, http.StatusBadRequest, "missing_refresh_token", "refresh_token is required")
		return
	}

	newToken, newHash, err := auth.NewRefreshToken()
	if err != nil {
		s.internalError(w, "generate refresh token", err)
		return
	}

	// Rotation: one refresh token can be spent exactly once.
	sess, err := s.db.RotateSession(r.Context(),
		auth.HashToken(req.RefreshToken), newHash, time.Now().Add(s.tokens.RefreshTTL()))
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusUnauthorized, "invalid_refresh_token", "session expired, please sign in again")
		return
	}
	if err != nil {
		s.internalError(w, "rotate session", err)
		return
	}

	user, err := s.db.UserByID(r.Context(), sess.UserID)
	if err != nil {
		s.internalError(w, "load user", err)
		return
	}
	access, expiresAt, err := s.tokens.NewAccessToken(user.ID, sess.ID)
	if err != nil {
		s.internalError(w, "sign access token", err)
		return
	}

	writeJSON(w, http.StatusOK, authResponse{
		User:         user,
		AccessToken:  access,
		RefreshToken: newToken,
		ExpiresAt:    expiresAt,
	})
}

func (s *Server) handleLogout(w http.ResponseWriter, r *http.Request) {
	var req refreshRequest
	if !decodeJSON(w, r, &req) {
		return
	}
	if req.RefreshToken != "" {
		if err := s.db.RevokeSessionByTokenHash(r.Context(), auth.HashToken(req.RefreshToken)); err != nil {
			s.internalError(w, "revoke session", err)
			return
		}
	}
	writeJSON(w, http.StatusOK, map[string]any{"status": "logged_out"})
}

type recoverRequest struct {
	Username     string `json:"username"`
	RecoveryCode string `json:"recovery_code"`
	NewPassword  string `json:"new_password"`
}

// handleRecover lets a user set a new password using the recovery code they
// saved at registration. Every existing session is revoked afterwards.
func (s *Server) handleRecover(w http.ResponseWriter, r *http.Request) {
	var req recoverRequest
	if !decodeJSON(w, r, &req) {
		return
	}

	user, err := s.db.UserByUsername(r.Context(), req.Username)
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusUnauthorized, "invalid_recovery", "wrong username or recovery code")
		return
	}
	if err != nil {
		s.internalError(w, "lookup user", err)
		return
	}
	if err := auth.VerifySecret(user.RecoveryHash, auth.NormalizeRecoveryCode(req.RecoveryCode)); err != nil {
		writeError(w, http.StatusUnauthorized, "invalid_recovery", "wrong username or recovery code")
		return
	}

	newHash, err := auth.HashSecret(req.NewPassword)
	if err != nil {
		writeError(w, http.StatusUnprocessableEntity, "weak_password", "password must be at least 8 characters")
		return
	}
	if err := s.db.SetPasswordHash(r.Context(), user.ID, newHash); err != nil {
		s.internalError(w, "set password", err)
		return
	}
	if err := s.db.RevokeAllSessions(r.Context(), user.ID); err != nil {
		s.internalError(w, "revoke sessions", err)
		return
	}
	s.hub.DisconnectUser(user.ID)

	writeJSON(w, http.StatusOK, map[string]any{"status": "password_changed"})
}

// issueSession creates a refresh session plus a matching access token.
func (s *Server) issueSession(r *http.Request, user store.User, deviceName, platform string) (authResponse, error) {
	refreshToken, refreshHash, err := auth.NewRefreshToken()
	if err != nil {
		return authResponse{}, err
	}

	sess, err := s.db.CreateSession(r.Context(), store.CreateSessionParams{
		UserID:     user.ID,
		TokenHash:  refreshHash,
		DeviceName: truncate(strings.TrimSpace(deviceName), 64),
		Platform:   truncate(strings.TrimSpace(platform), 16),
		ExpiresAt:  time.Now().Add(s.tokens.RefreshTTL()),
	})
	if err != nil {
		return authResponse{}, err
	}

	access, expiresAt, err := s.tokens.NewAccessToken(user.ID, sess.ID)
	if err != nil {
		return authResponse{}, err
	}

	return authResponse{
		User:         user,
		AccessToken:  access,
		RefreshToken: refreshToken,
		ExpiresAt:    expiresAt,
	}, nil
}

func truncate(s string, max int) string {
	runes := []rune(s)
	if len(runes) <= max {
		return s
	}
	return string(runes[:max])
}
