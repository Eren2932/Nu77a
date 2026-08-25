package store

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
)

type Session struct {
	ID         uuid.UUID
	UserID     uuid.UUID
	DeviceName string
	Platform   string
	CreatedAt  time.Time
	ExpiresAt  time.Time
}

type CreateSessionParams struct {
	UserID     uuid.UUID
	TokenHash  string
	DeviceName string
	Platform   string
	ExpiresAt  time.Time
}

func (s *Store) CreateSession(ctx context.Context, p CreateSessionParams) (Session, error) {
	var sess Session
	err := s.Pool.QueryRow(ctx, `
		INSERT INTO sessions (id, user_id, token_hash, device_name, platform, expires_at)
		VALUES ($1, $2, $3, $4, $5, $6)
		RETURNING id, user_id, device_name, platform, created_at, expires_at`,
		uuid.New(), p.UserID, p.TokenHash, p.DeviceName, defaultPlatform(p.Platform), p.ExpiresAt,
	).Scan(&sess.ID, &sess.UserID, &sess.DeviceName, &sess.Platform, &sess.CreatedAt, &sess.ExpiresAt)
	return sess, err
}

// SessionByTokenHash returns a live (not revoked, not expired) session.
func (s *Store) SessionByTokenHash(ctx context.Context, tokenHash string) (Session, error) {
	var sess Session
	err := s.Pool.QueryRow(ctx, `
		SELECT id, user_id, device_name, platform, created_at, expires_at
		FROM sessions
		WHERE token_hash = $1 AND revoked_at IS NULL AND expires_at > now()`,
		tokenHash,
	).Scan(&sess.ID, &sess.UserID, &sess.DeviceName, &sess.Platform, &sess.CreatedAt, &sess.ExpiresAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return sess, ErrNotFound
	}
	return sess, err
}

// RotateSession revokes the old refresh token and issues a new one atomically.
func (s *Store) RotateSession(ctx context.Context, oldHash, newHash string, expiresAt time.Time) (Session, error) {
	var sess Session
	err := s.Pool.QueryRow(ctx, `
		UPDATE sessions
		SET token_hash = $2, expires_at = $3, last_used_at = now()
		WHERE token_hash = $1 AND revoked_at IS NULL AND expires_at > now()
		RETURNING id, user_id, device_name, platform, created_at, expires_at`,
		oldHash, newHash, expiresAt,
	).Scan(&sess.ID, &sess.UserID, &sess.DeviceName, &sess.Platform, &sess.CreatedAt, &sess.ExpiresAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return sess, ErrNotFound
	}
	return sess, err
}

func (s *Store) RevokeSessionByTokenHash(ctx context.Context, tokenHash string) error {
	_, err := s.Pool.Exec(ctx,
		`UPDATE sessions SET revoked_at = now() WHERE token_hash = $1 AND revoked_at IS NULL`, tokenHash)
	return err
}

func (s *Store) RevokeAllSessions(ctx context.Context, userID uuid.UUID) error {
	_, err := s.Pool.Exec(ctx,
		`UPDATE sessions SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL`, userID)
	return err
}

func (s *Store) DeleteExpiredSessions(ctx context.Context) (int64, error) {
	tag, err := s.Pool.Exec(ctx,
		`DELETE FROM sessions WHERE expires_at < now() - interval '30 days'`)
	if err != nil {
		return 0, err
	}
	return tag.RowsAffected(), nil
}

func defaultPlatform(p string) string {
	if p == "" {
		return "android"
	}
	return p
}
