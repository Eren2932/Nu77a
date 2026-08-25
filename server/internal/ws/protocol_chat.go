package ws

// Frame types added in 4.0 for voice notes and reactions.
//
// They live in their own file, next to the sprint-0 protocol, so the chat
// vocabulary can grow without every merge touching the same block. The string
// values are the wire contract: they are mirrored by the Kotlin client in
// data/remote/Dto.kt and must never be renamed without a matching /v2.
const (
	// TypeSendVoice is inbound. Its payload carries an attachment_id that the
	// client has already uploaded through POST /v1/media; the audio itself
	// never crosses the socket.
	TypeSendVoice = "send_voice"

	// TypeReactionAdd and TypeReactionRemove are inbound and idempotent: a
	// double tap on a flaky connection is normal traffic, not an error.
	TypeReactionAdd    = "reaction_add"
	TypeReactionRemove = "reaction_remove"

	// TypeReactionRelay is outbound and always carries the full tally for a
	// message, never a delta, so a client that slept through a frame still
	// converges on the correct counts.
	TypeReactionRelay = "reaction_relay"
)
