package ws

import "encoding/json"

// Envelope is the single frame format for the realtime channel. Every message
// in both directions is one of these, so the client has one decoder.
type Envelope struct {
	Type    string          `json:"type"`
	ID      string          `json:"id,omitempty"`
	Payload json.RawMessage `json:"payload,omitempty"`
}

// Client -> server
const (
	TypePing      = "ping"
	TypeEcho      = "echo"
	TypeTyping    = "typing"
	TypeReadUpTo  = "read_up_to"
	TypeSendText  = "send_text"
)

// Server -> client
const (
	TypeHello        = "hello"
	TypePong         = "pong"
	TypeEchoReply    = "echo_reply"
	TypeError        = "error"
	TypeMessageNew   = "message_new"
	TypePresence     = "presence"
	TypeTypingRelay  = "typing_relay"
)

func NewEnvelope(msgType, id string, payload any) (Envelope, error) {
	env := Envelope{Type: msgType, ID: id}
	if payload == nil {
		return env, nil
	}
	raw, err := json.Marshal(payload)
	if err != nil {
		return env, err
	}
	env.Payload = raw
	return env, nil
}

func ErrorEnvelope(id, code, message string) Envelope {
	env, _ := NewEnvelope(TypeError, id, map[string]string{
		"code":    code,
		"message": message,
	})
	return env
}
