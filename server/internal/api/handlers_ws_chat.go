package api

import (
	"context"
	"encoding/json"
	"errors"
	"strings"

	"github.com/google/uuid"

	"github.com/nuva/server/internal/store"
	"github.com/nuva/server/internal/ws"
)

// Inbound payloads. Each is validated before it touches the database: a socket
// is an unauthenticated-shaped channel even when the connection is authorised.

type sendTextPayload struct {
	ConversationID string `json:"conversation_id"`
	ClientID       string `json:"client_id"`
	Body           string `json:"body"`
}

type sendVoicePayload struct {
	ConversationID string `json:"conversation_id"`
	ClientID       string `json:"client_id"`
	// Id returned by POST /v1/media. The audio itself never crosses this socket.
	AttachmentID string `json:"attachment_id"`
}

type reactionPayload struct {
	MessageID string `json:"message_id"`
	Emoji     string `json:"emoji"`
}

const (
	maxBodyRunes  = 4096
	maxEmojiRunes = 8
)

func (s *Server) handleSendText(ctx context.Context, client *ws.Client, env ws.Envelope) {
	var p sendTextPayload
	if !s.decodeFrame(client, env, &p) {
		return
	}

	body := strings.TrimSpace(p.Body)
	if body == "" {
		s.frameError(client, env, "empty_body", "a text message cannot be empty")
		return
	}
	if len([]rune(body)) > maxBodyRunes {
		s.frameError(client, env, "body_too_long", "a message must be at most 4096 characters")
		return
	}

	s.persistAndRelay(ctx, client, env, store.CreateMessageParams{
		ClientID: p.ClientID,
		Kind:     "text",
		Body:     body,
	}, p.ConversationID)
}

func (s *Server) handleSendVoice(ctx context.Context, client *ws.Client, env ws.Envelope) {
	var p sendVoicePayload
	if !s.decodeFrame(client, env, &p) {
		return
	}

	attachmentID, err := uuid.Parse(p.AttachmentID)
	if err != nil {
		s.frameError(client, env, "invalid_attachment", "attachment_id is not a uuid")
		return
	}

	att, err := s.db.AttachmentByID(ctx, attachmentID)
	if errors.Is(err, store.ErrNotFound) {
		s.frameError(client, env, "attachment_not_found", "upload the audio before sending it")
		return
	}
	if err != nil {
		s.frameError(client, env, "internal_error", "could not load the attachment")
		return
	}
	if att.Kind != "voice" {
		s.frameError(client, env, "wrong_attachment_kind", "this attachment is not a voice note")
		return
	}
	// Only the uploader may attach it, so a guessed id cannot be re-sent by
	// someone who never had the audio.
	if att.OwnerID != client.UserID {
		s.frameError(client, env, "not_your_attachment", "this attachment belongs to someone else")
		return
	}

	s.persistAndRelay(ctx, client, env, store.CreateMessageParams{
		ClientID:     p.ClientID,
		Kind:         "voice",
		Body:         "",
		AttachmentID: &attachmentID,
	}, p.ConversationID)
}

// persistAndRelay is the shared tail of send_text and send_voice: check
// membership, write the row, then fan out to everyone in the conversation.
func (s *Server) persistAndRelay(
	ctx context.Context,
	client *ws.Client,
	env ws.Envelope,
	params store.CreateMessageParams,
	rawConversationID string,
) {
	convoID, err := uuid.Parse(rawConversationID)
	if err != nil {
		s.frameError(client, env, "invalid_conversation", "conversation_id is not a uuid")
		return
	}
	if strings.TrimSpace(params.ClientID) == "" {
		s.frameError(client, env, "missing_client_id", "client_id is required so retries are safe")
		return
	}

	member, err := s.db.IsMember(ctx, convoID, client.UserID)
	if err != nil {
		s.frameError(client, env, "internal_error", "could not verify membership")
		return
	}
	if !member {
		// Same answer as a conversation that does not exist: never confirm the
		// existence of a room the caller is not in.
		s.frameError(client, env, "conversation_not_found", "no such conversation")
		return
	}

	params.ConversationID = convoID
	params.SenderID = client.UserID

	msg, err := s.db.CreateMessage(ctx, params)
	if errors.Is(err, store.ErrNotFound) {
		s.frameError(client, env, "conversation_not_found", "no such conversation")
		return
	}
	if err != nil {
		s.frameError(client, env, "internal_error", "could not store the message")
		return
	}

	members, err := s.db.MemberIDs(ctx, convoID)
	if err != nil {
		s.frameError(client, env, "internal_error", "could not resolve recipients")
		return
	}

	out, err := ws.NewEnvelope(ws.TypeMessageNew, env.ID, msg)
	if err != nil {
		s.frameError(client, env, "internal_error", "could not encode the message")
		return
	}
	// The sender is in `members`, so the ack and the broadcast are the same
	// frame. One code path means the sender's copy can never drift from what
	// everyone else received.
	_ = s.hub.SendJSONToUsers(members, out)
}

func (s *Server) handleReaction(ctx context.Context, client *ws.Client, env ws.Envelope, add bool) {
	var p reactionPayload
	if !s.decodeFrame(client, env, &p) {
		return
	}

	messageID, err := uuid.Parse(p.MessageID)
	if err != nil {
		s.frameError(client, env, "invalid_message", "message_id is not a uuid")
		return
	}

	emoji := strings.TrimSpace(p.Emoji)
	if emoji == "" || len([]rune(emoji)) > maxEmojiRunes {
		s.frameError(client, env, "invalid_emoji", "emoji must be 1-8 characters")
		return
	}

	convoID, err := s.db.ConversationOfMessage(ctx, messageID)
	if errors.Is(err, store.ErrNotFound) {
		s.frameError(client, env, "message_not_found", "no such message")
		return
	}
	if err != nil {
		s.frameError(client, env, "internal_error", "could not load the message")
		return
	}

	member, err := s.db.IsMember(ctx, convoID, client.UserID)
	if err != nil || !member {
		s.frameError(client, env, "message_not_found", "no such message")
		return
	}

	if add {
		err = s.db.AddReaction(ctx, messageID, client.UserID, emoji)
	} else {
		err = s.db.RemoveReaction(ctx, messageID, client.UserID, emoji)
	}
	if err != nil {
		s.frameError(client, env, "internal_error", "could not save the reaction")
		return
	}

	members, err := s.db.MemberIDs(ctx, convoID)
	if err != nil {
		s.frameError(client, env, "internal_error", "could not resolve recipients")
		return
	}

	// The relay carries the full tally, not a delta. A client that missed a
	// frame while backgrounded still ends up with the correct counts, which is
	// much cheaper than a reconciliation protocol.
	for _, uid := range members {
		tallies, err := s.db.ReactionsFor(ctx, messageID, uid)
		if err != nil {
			continue
		}
		frame, err := ws.NewEnvelope(ws.TypeReactionRelay, "", map[string]any{
			"message_id":      messageID.String(),
			"conversation_id": convoID.String(),
			"reactions":       tallies,
		})
		if err != nil {
			continue
		}
		_ = s.hub.SendJSONToUsers([]uuid.UUID{uid}, frame)
	}
}

func (s *Server) decodeFrame(client *ws.Client, env ws.Envelope, dst any) bool {
	if len(env.Payload) == 0 {
		s.frameError(client, env, "missing_payload", "this frame requires a payload")
		return false
	}
	if err := json.Unmarshal(env.Payload, dst); err != nil {
		s.frameError(client, env, "invalid_payload", "payload is not valid JSON for this type")
		return false
	}
	return true
}

func (s *Server) frameError(client *ws.Client, env ws.Envelope, code, message string) {
	s.sendRaw(client, ws.ErrorEnvelope(env.ID, code, message))
}
