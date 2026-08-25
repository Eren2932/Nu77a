-- 0002_media_reactions.sql — voice messages, attachments and reactions.
-- Never edit an applied migration: add a new numbered file instead.

-- Content-addressed blobs. One row per distinct set of bytes, shared by every
-- message that references it, so forwarding a voice note stores nothing new.
CREATE TABLE attachments (
    id           UUID PRIMARY KEY,
    owner_id     UUID        REFERENCES users(id) ON DELETE SET NULL,
    sha256       TEXT        NOT NULL,
    kind         TEXT        NOT NULL CHECK (kind IN ('voice', 'image', 'video', 'file')),
    mime         TEXT        NOT NULL,
    bytes        BIGINT      NOT NULL CHECK (bytes > 0),

    -- Voice only. Duration is authoritative for the UI: the client must not
    -- have to download the audio to know how wide to draw the bubble.
    duration_ms  INTEGER     NOT NULL DEFAULT 0 CHECK (duration_ms >= 0),

    -- Coarse amplitude envelope, 0..100 per bar, produced by the recorder.
    -- Deliberately NOT computed server-side: decoding audio in Go would drag
    -- in a codec dependency and turn every upload into CPU work.
    waveform     SMALLINT[]  NOT NULL DEFAULT '{}',

    storage_path TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Dedupe key. Two people uploading identical bytes reuse one row and one file.
CREATE UNIQUE INDEX attachments_sha256_idx ON attachments(sha256);
CREATE INDEX attachments_owner_idx ON attachments(owner_id);

-- messages.kind already exists and has no CHECK, so 'voice' needs no change
-- there. Only the link to the blob is new.
ALTER TABLE messages ADD COLUMN attachment_id UUID REFERENCES attachments(id) ON DELETE SET NULL;

CREATE INDEX messages_attachment_idx ON messages(attachment_id) WHERE attachment_id IS NOT NULL;

-- One row per (message, user, emoji). The emoji is part of the key so a single
-- user can leave several different reactions on one message, the way Discord
-- and current Telegram both behave.
CREATE TABLE reactions (
    message_id UUID        NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji      TEXT        NOT NULL CHECK (char_length(emoji) BETWEEN 1 AND 16),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id, emoji)
);

CREATE INDEX reactions_message_idx ON reactions(message_id);
