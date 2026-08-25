-- 0001_init.sql — base schema for Nuva.
-- Never edit an applied migration: add a new numbered file instead.

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    username        TEXT        NOT NULL,
    username_lower  TEXT        NOT NULL UNIQUE,
    display_name    TEXT        NOT NULL,
    bio             TEXT        NOT NULL DEFAULT '',
    avatar_url      TEXT        NOT NULL DEFAULT '',
    password_hash   TEXT        NOT NULL,
    recovery_hash   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ
);

CREATE TABLE sessions (
    id            UUID PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash    TEXT        NOT NULL UNIQUE,
    device_name   TEXT        NOT NULL DEFAULT '',
    platform      TEXT        NOT NULL DEFAULT 'android',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked_at    TIMESTAMPTZ
);

CREATE INDEX sessions_user_idx ON sessions(user_id);

-- Conversations and messages land in sprint 2, but the tables exist now so
-- the client contract does not shift under us later.
CREATE TABLE conversations (
    id          UUID PRIMARY KEY,
    kind        TEXT        NOT NULL CHECK (kind IN ('direct', 'group')),
    title       TEXT        NOT NULL DEFAULT '',
    avatar_url  TEXT        NOT NULL DEFAULT '',
    created_by  UUID        REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conversation_members (
    conversation_id UUID        NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            TEXT        NOT NULL DEFAULT 'member' CHECK (role IN ('owner', 'admin', 'member')),
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_read_seq   BIGINT      NOT NULL DEFAULT 0,
    muted           BOOLEAN     NOT NULL DEFAULT false,
    PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX conversation_members_user_idx ON conversation_members(user_id);

CREATE TABLE messages (
    id              UUID PRIMARY KEY,
    conversation_id UUID        NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id       UUID        REFERENCES users(id) ON DELETE SET NULL,
    seq             BIGINT      NOT NULL,
    client_id       TEXT        NOT NULL,
    kind            TEXT        NOT NULL DEFAULT 'text',
    body            TEXT        NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at       TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    UNIQUE (conversation_id, seq),
    UNIQUE (conversation_id, sender_id, client_id)
);

CREATE INDEX messages_conversation_seq_idx ON messages(conversation_id, seq DESC);
