package store

import (
	"context"

	"github.com/google/uuid"
)

// MemberIDs returns every user in a conversation. This is the fan-out list for
// anything realtime: a new message, a reaction, a typing indicator.
func (s *Store) MemberIDs(ctx context.Context, conversationID uuid.UUID) ([]uuid.UUID, error) {
	rows, err := s.Pool.Query(ctx,
		`SELECT user_id FROM conversation_members WHERE conversation_id = $1`, conversationID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := make([]uuid.UUID, 0, 8)
	for rows.Next() {
		var id uuid.UUID
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		out = append(out, id)
	}
	return out, rows.Err()
}

// IsMember gates every conversation-scoped action. Checked on the server for
// every frame: a client that lies about its conversation id gets nothing.
func (s *Store) IsMember(ctx context.Context, conversationID, userID uuid.UUID) (bool, error) {
	var exists bool
	err := s.Pool.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1 FROM conversation_members
			WHERE conversation_id = $1 AND user_id = $2
		)`, conversationID, userID).Scan(&exists)
	return exists, err
}

// ConversationOfMessage is what a reaction frame needs: reactions carry only a
// message id, but the fan-out list is per conversation.
func (s *Store) ConversationOfMessage(ctx context.Context, messageID uuid.UUID) (uuid.UUID, error) {
	var id uuid.UUID
	err := s.Pool.QueryRow(ctx,
		`SELECT conversation_id FROM messages WHERE id = $1`, messageID).Scan(&id)
	if err != nil {
		return uuid.Nil, mapNoRows(err)
	}
	return id, nil
}
