package store

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
)

// Message is one row of a conversation. A voice note is a Message with
// Kind == "voice" and an Attachment hanging off it; there is no separate
// "voice message" type, so every feature that works on messages (reply,
// forward, delete, reactions) works on voice for free.
type Message struct {
	ID             uuid.UUID   `json:"id"`
	ConversationID uuid.UUID   `json:"conversation_id"`
	SenderID       uuid.UUID   `json:"sender_id"`
	Seq            int64       `json:"seq"`
	ClientID       string      `json:"client_id"`
	Kind           string      `json:"kind"`
	Body           string      `json:"body"`
	AttachmentID   *uuid.UUID  `json:"attachment_id,omitempty"`
	Attachment     *Attachment `json:"attachment,omitempty"`
	CreatedAt      time.Time   `json:"created_at"`
	EditedAt       *time.Time  `json:"edited_at,omitempty"`
}

type CreateMessageParams struct {
	ConversationID uuid.UUID
	SenderID       uuid.UUID
	ClientID       string
	Kind           string
	Body           string
	AttachmentID   *uuid.UUID
}

func mapNoRows(err error) error {
	if errors.Is(err, pgx.ErrNoRows) {
		return ErrNotFound
	}
	return err
}

// CreateMessage assigns the next per-conversation sequence number and inserts
// the row.
//
// The conversation row is locked FOR UPDATE first, which serialises concurrent
// senders. Without it two clients can read the same max(seq) and collide on
// the UNIQUE (conversation_id, seq) index. Doing it in one statement with a
// sub-select would have the same race, just harder to see.
//
// Resending the same client_id returns the existing row instead of a duplicate,
// so a retry after a dropped socket is safe.
func (s *Store) CreateMessage(ctx context.Context, p CreateMessageParams) (Message, error) {
	tx, err := s.Pool.Begin(ctx)
	if err != nil {
		return Message{}, err
	}
	defer func() { _ = tx.Rollback(ctx) }()

	var locked uuid.UUID
	err = tx.QueryRow(ctx,
		`SELECT id FROM conversations WHERE id = $1 FOR UPDATE`, p.ConversationID).Scan(&locked)
	if err != nil {
		return Message{}, mapNoRows(err)
	}

	var next int64
	if err := tx.QueryRow(ctx,
		`SELECT coalesce(max(seq), 0) + 1 FROM messages WHERE conversation_id = $1`,
		p.ConversationID).Scan(&next); err != nil {
		return Message{}, err
	}

	var m Message
	err = tx.QueryRow(ctx, `
		INSERT INTO messages
			(id, conversation_id, sender_id, seq, client_id, kind, body, attachment_id)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
		ON CONFLICT (conversation_id, sender_id, client_id) DO NOTHING
		RETURNING id, conversation_id, sender_id, seq, client_id, kind, body,
		          attachment_id, created_at, edited_at`,
		uuid.New(), p.ConversationID, p.SenderID, next, p.ClientID, p.Kind, p.Body, p.AttachmentID,
	).Scan(&m.ID, &m.ConversationID, &m.SenderID, &m.Seq, &m.ClientID, &m.Kind, &m.Body,
		&m.AttachmentID, &m.CreatedAt, &m.EditedAt)

	if errors.Is(err, pgx.ErrNoRows) {
		// Same client_id as before: this is a retry, not a new message.
		if err := tx.Commit(ctx); err != nil {
			return Message{}, err
		}
		return s.MessageByClientID(ctx, p.ConversationID, p.SenderID, p.ClientID)
	}
	if err != nil {
		return Message{}, err
	}

	if _, err := tx.Exec(ctx,
		`UPDATE conversations SET updated_at = now() WHERE id = $1`, p.ConversationID); err != nil {
		return Message{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return Message{}, err
	}
	return s.hydrate(ctx, m)
}

const messageColumns = `id, conversation_id, sender_id, seq, client_id, kind, body,
	attachment_id, created_at, edited_at`

func scanMessage(row pgx.Row) (Message, error) {
	var m Message
	err := row.Scan(&m.ID, &m.ConversationID, &m.SenderID, &m.Seq, &m.ClientID,
		&m.Kind, &m.Body, &m.AttachmentID, &m.CreatedAt, &m.EditedAt)
	return m, mapNoRows(err)
}

func (s *Store) MessageByClientID(ctx context.Context, convoID, senderID uuid.UUID, clientID string) (Message, error) {
	m, err := scanMessage(s.Pool.QueryRow(ctx,
		`SELECT `+messageColumns+` FROM messages
		 WHERE conversation_id = $1 AND sender_id = $2 AND client_id = $3`,
		convoID, senderID, clientID))
	if err != nil {
		return m, err
	}
	return s.hydrate(ctx, m)
}

// Messages returns one page of history, newest first. `before` is a seq
// cursor; pass 0 for the most recent page.
func (s *Store) Messages(ctx context.Context, convoID uuid.UUID, before int64, limit int) ([]Message, error) {
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	if before <= 0 {
		before = 1 << 62
	}

	rows, err := s.Pool.Query(ctx,
		`SELECT `+messageColumns+` FROM messages
		 WHERE conversation_id = $1 AND seq < $2 AND deleted_at IS NULL
		 ORDER BY seq DESC LIMIT $3`,
		convoID, before, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := make([]Message, 0, limit)
	for rows.Next() {
		var m Message
		if err := rows.Scan(&m.ID, &m.ConversationID, &m.SenderID, &m.Seq, &m.ClientID,
			&m.Kind, &m.Body, &m.AttachmentID, &m.CreatedAt, &m.EditedAt); err != nil {
			return nil, err
		}
		out = append(out, m)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}

	for i := range out {
		hydrated, err := s.hydrate(ctx, out[i])
		if err != nil {
			return nil, err
		}
		out[i] = hydrated
	}
	return out, nil
}

// hydrate fills in the attachment so the client never has to make a second
// call just to learn how long a voice note is.
func (s *Store) hydrate(ctx context.Context, m Message) (Message, error) {
	if m.AttachmentID == nil {
		return m, nil
	}
	att, err := s.AttachmentByID(ctx, *m.AttachmentID)
	if errors.Is(err, ErrNotFound) {
		return m, nil
	}
	if err != nil {
		return m, err
	}
	m.Attachment = &att
	return m, nil
}
