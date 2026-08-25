package store

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
)

// Attachment is a stored blob. The bytes themselves live on disk under
// Config.MediaDir; this row is the only thing that knows where.
type Attachment struct {
	ID          uuid.UUID `json:"id"`
	OwnerID     uuid.UUID `json:"owner_id"`
	SHA256      string    `json:"sha256"`
	Kind        string    `json:"kind"`
	Mime        string    `json:"mime"`
	Bytes       int64     `json:"bytes"`
	DurationMs  int32     `json:"duration_ms"`
	Waveform    []int16   `json:"waveform"`
	StoragePath string    `json:"-"`
	CreatedAt   time.Time `json:"created_at"`
}

const attachmentColumns = `id, owner_id, sha256, kind, mime, bytes,
	duration_ms, waveform, storage_path, created_at`

func scanAttachment(row pgx.Row) (Attachment, error) {
	var a Attachment
	var owner *uuid.UUID
	err := row.Scan(&a.ID, &owner, &a.SHA256, &a.Kind, &a.Mime, &a.Bytes,
		&a.DurationMs, &a.Waveform, &a.StoragePath, &a.CreatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return a, ErrNotFound
	}
	if owner != nil {
		a.OwnerID = *owner
	}
	if a.Waveform == nil {
		a.Waveform = []int16{}
	}
	return a, err
}

type CreateAttachmentParams struct {
	OwnerID     uuid.UUID
	SHA256      string
	Kind        string
	Mime        string
	Bytes       int64
	DurationMs  int32
	Waveform    []int16
	StoragePath string
}

// PutAttachment inserts the blob, or returns the existing row when these exact
// bytes are already stored. The caller can then delete its temp file.
//
// Returns reused=true when nothing was inserted, so the handler knows not to
// keep the file it just wrote.
func (s *Store) PutAttachment(ctx context.Context, p CreateAttachmentParams) (a Attachment, reused bool, err error) {
	if p.Waveform == nil {
		p.Waveform = []int16{}
	}

	row := s.Pool.QueryRow(ctx, `
		INSERT INTO attachments
			(id, owner_id, sha256, kind, mime, bytes, duration_ms, waveform, storage_path)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
		ON CONFLICT (sha256) DO NOTHING
		RETURNING `+attachmentColumns,
		uuid.New(), p.OwnerID, p.SHA256, p.Kind, p.Mime, p.Bytes,
		p.DurationMs, p.Waveform, p.StoragePath)

	a, err = scanAttachment(row)
	if err == nil {
		return a, false, nil
	}
	if !errors.Is(err, ErrNotFound) {
		return Attachment{}, false, err
	}

	// ON CONFLICT DO NOTHING returned no row: the blob already exists.
	a, err = s.AttachmentBySHA256(ctx, p.SHA256)
	return a, true, err
}

func (s *Store) AttachmentByID(ctx context.Context, id uuid.UUID) (Attachment, error) {
	return scanAttachment(s.Pool.QueryRow(ctx,
		`SELECT `+attachmentColumns+` FROM attachments WHERE id = $1`, id))
}

func (s *Store) AttachmentBySHA256(ctx context.Context, sum string) (Attachment, error) {
	return scanAttachment(s.Pool.QueryRow(ctx,
		`SELECT `+attachmentColumns+` FROM attachments WHERE sha256 = $1`, sum))
}
