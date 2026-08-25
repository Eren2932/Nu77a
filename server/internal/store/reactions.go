package store

import (
	"context"

	"github.com/google/uuid"
)

// Reaction is one emoji left by one user on one message.
type Reaction struct {
	MessageID uuid.UUID `json:"message_id"`
	UserID    uuid.UUID `json:"user_id"`
	Emoji     string    `json:"emoji"`
}

// ReactionTally is the collapsed form the UI actually draws: one pill per
// emoji, with a count and whether the current viewer is part of it.
type ReactionTally struct {
	Emoji string      `json:"emoji"`
	Count int         `json:"count"`
	Mine  bool        `json:"mine"`
	Users []uuid.UUID `json:"users"`
}

// AddReaction is idempotent: reacting twice with the same emoji is a no-op
// rather than an error, because a double tap on a laggy connection is normal.
func (s *Store) AddReaction(ctx context.Context, messageID, userID uuid.UUID, emoji string) error {
	_, err := s.Pool.Exec(ctx, `
		INSERT INTO reactions (message_id, user_id, emoji)
		VALUES ($1, $2, $3)
		ON CONFLICT (message_id, user_id, emoji) DO NOTHING`,
		messageID, userID, emoji)
	return err
}

// RemoveReaction is also idempotent, for the same reason.
func (s *Store) RemoveReaction(ctx context.Context, messageID, userID uuid.UUID, emoji string) error {
	_, err := s.Pool.Exec(ctx,
		`DELETE FROM reactions WHERE message_id = $1 AND user_id = $2 AND emoji = $3`,
		messageID, userID, emoji)
	return err
}

// ReactionsFor returns the tallies for one message, most popular first, with
// `Mine` resolved for viewerID.
func (s *Store) ReactionsFor(ctx context.Context, messageID, viewerID uuid.UUID) ([]ReactionTally, error) {
	rows, err := s.Pool.Query(ctx, `
		SELECT emoji,
		       count(*)::int                                AS count,
		       bool_or(user_id = $2)                        AS mine,
		       array_agg(user_id ORDER BY created_at)       AS users
		FROM reactions
		WHERE message_id = $1
		GROUP BY emoji
		ORDER BY count(*) DESC, min(created_at) ASC`,
		messageID, viewerID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := make([]ReactionTally, 0, 8)
	for rows.Next() {
		var t ReactionTally
		if err := rows.Scan(&t.Emoji, &t.Count, &t.Mine, &t.Users); err != nil {
			return nil, err
		}
		out = append(out, t)
	}
	return out, rows.Err()
}
