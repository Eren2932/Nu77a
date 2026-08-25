package store

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
)

type User struct {
	ID           uuid.UUID  `json:"id"`
	Username     string     `json:"username"`
	DisplayName  string     `json:"display_name"`
	Bio          string     `json:"bio"`
	AvatarURL    string     `json:"avatar_url"`
	CreatedAt    time.Time  `json:"created_at"`
	LastSeenAt   *time.Time `json:"last_seen_at,omitempty"`
	PasswordHash string     `json:"-"`
	RecoveryHash string     `json:"-"`
}

type CreateUserParams struct {
	Username     string
	DisplayName  string
	PasswordHash string
	RecoveryHash string
}

const userColumns = `id, username, display_name, bio, avatar_url,
	created_at, last_seen_at, password_hash, recovery_hash`

func scanUser(row pgx.Row) (User, error) {
	var u User
	err := row.Scan(&u.ID, &u.Username, &u.DisplayName, &u.Bio, &u.AvatarURL,
		&u.CreatedAt, &u.LastSeenAt, &u.PasswordHash, &u.RecoveryHash)
	if errors.Is(err, pgx.ErrNoRows) {
		return u, ErrNotFound
	}
	return u, err
}

func (s *Store) CreateUser(ctx context.Context, p CreateUserParams) (User, error) {
	id := uuid.New()
	row := s.Pool.QueryRow(ctx, `
		INSERT INTO users (id, username, username_lower, display_name, password_hash, recovery_hash)
		VALUES ($1, $2, $3, $4, $5, $6)
		RETURNING `+userColumns,
		id, p.Username, strings.ToLower(p.Username), p.DisplayName, p.PasswordHash, p.RecoveryHash)

	u, err := scanUser(row)
	if err != nil && isUniqueViolation(err) {
		return User{}, ErrConflict
	}
	return u, err
}

func (s *Store) UserByUsername(ctx context.Context, username string) (User, error) {
	return scanUser(s.Pool.QueryRow(ctx,
		`SELECT `+userColumns+` FROM users WHERE username_lower = $1`,
		strings.ToLower(strings.TrimSpace(username))))
}

func (s *Store) UserByID(ctx context.Context, id uuid.UUID) (User, error) {
	return scanUser(s.Pool.QueryRow(ctx,
		`SELECT `+userColumns+` FROM users WHERE id = $1`, id))
}

type UpdateProfileParams struct {
	DisplayName *string
	Bio         *string
	AvatarURL   *string
}

func (s *Store) UpdateProfile(ctx context.Context, id uuid.UUID, p UpdateProfileParams) (User, error) {
	return scanUser(s.Pool.QueryRow(ctx, `
		UPDATE users SET
			display_name = COALESCE($2, display_name),
			bio          = COALESCE($3, bio),
			avatar_url   = COALESCE($4, avatar_url),
			updated_at   = now()
		WHERE id = $1
		RETURNING `+userColumns,
		id, p.DisplayName, p.Bio, p.AvatarURL))
}

func (s *Store) TouchLastSeen(ctx context.Context, id uuid.UUID) error {
	_, err := s.Pool.Exec(ctx, `UPDATE users SET last_seen_at = now() WHERE id = $1`, id)
	return err
}

func (s *Store) SetPasswordHash(ctx context.Context, id uuid.UUID, hash string) error {
	_, err := s.Pool.Exec(ctx,
		`UPDATE users SET password_hash = $2, updated_at = now() WHERE id = $1`, id, hash)
	return err
}
