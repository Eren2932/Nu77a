package api

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"time"

	"github.com/coder/websocket"

	"github.com/nuva/server/internal/ws"
)

const (
	// The client must send a ping (or anything) within this window.
	wsReadTimeout  = 90 * time.Second
	wsWriteTimeout = 10 * time.Second
	wsMaxFrameSize = 1 << 20 // 1 MiB; media goes over HTTP, not the socket
)

func (s *Server) handleWebSocket(w http.ResponseWriter, r *http.Request) {
	userID, ok := UserIDFrom(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "invalid_token", "token is invalid")
		return
	}

	opts := &websocket.AcceptOptions{
		CompressionMode: websocket.CompressionDisabled,
	}
	// A native app sends no Origin header; a browser does. In development we
	// accept anything, in production only our own origins.
	if s.cfg.IsProd() {
		opts.OriginPatterns = s.cfg.AllowedOrigins
	} else {
		opts.InsecureSkipVerify = true
	}

	conn, err := websocket.Accept(w, r, opts)
	if err != nil {
		slog.Warn("ws accept failed", "err", err, "user_id", userID)
		return
	}
	defer conn.CloseNow()
	conn.SetReadLimit(wsMaxFrameSize)

	client := ws.NewClient(userID)
	s.hub.Register(client)
	defer s.hub.Unregister(client)

	if err := s.db.TouchLastSeen(r.Context(), userID); err != nil {
		slog.Warn("touch last seen", "err", err, "user_id", userID)
	}

	ctx, cancel := context.WithCancel(r.Context())
	defer cancel()

	// Writer: the only goroutine allowed to write to this socket.
	go func() {
		for {
			select {
			case <-ctx.Done():
				return
			case <-client.Closed():
				_ = conn.Close(websocket.StatusNormalClosure, "server closed session")
				return
			case payload := <-client.Outbound():
				writeCtx, writeCancel := context.WithTimeout(ctx, wsWriteTimeout)
				err := conn.Write(writeCtx, websocket.MessageText, payload)
				writeCancel()
				if err != nil {
					cancel()
					return
				}
			}
		}
	}()

	s.sendEnvelope(client, ws.TypeHello, "", map[string]any{
		"user_id":        userID.String(),
		"server_time":    time.Now().UTC().Format(time.RFC3339),
		"heartbeat_secs": 30,
		"api_version":    APIVersion,
	})

	// Reader loop: runs on this goroutine until the client goes away.
	for {
		readCtx, readCancel := context.WithTimeout(ctx, wsReadTimeout)
		msgType, data, err := conn.Read(readCtx)
		readCancel()

		if err != nil {
			if !errors.Is(err, context.Canceled) &&
				websocket.CloseStatus(err) == -1 {
				slog.Debug("ws read ended", "err", err, "user_id", userID)
			}
			return
		}
		if msgType != websocket.MessageText {
			continue
		}

		var env ws.Envelope
		if err := json.Unmarshal(data, &env); err != nil {
			s.sendRaw(client, ws.ErrorEnvelope("", "bad_frame", "frame is not a valid Nuva envelope"))
			continue
		}
		s.dispatch(ctx, client, env)
	}
}

// dispatch handles one inbound frame. Sprint 0 knows ping and echo; sprint 2
// adds send_text, typing and read_up_to on top of the same switch.
func (s *Server) dispatch(ctx context.Context, client *ws.Client, env ws.Envelope) {
	switch env.Type {
	case ws.TypePing:
		s.sendEnvelope(client, ws.TypePong, env.ID, map[string]any{
			"server_time": time.Now().UTC().UnixMilli(),
		})

	case ws.TypeEcho:
		s.sendRaw(client, ws.Envelope{
			Type:    ws.TypeEchoReply,
			ID:      env.ID,
			Payload: env.Payload,
		})

	case ws.TypeSendText:
		s.handleSendText(ctx, client, env)

	case ws.TypeSendVoice:
		s.handleSendVoice(ctx, client, env)

	case ws.TypeReactionAdd:
		s.handleReaction(ctx, client, env, true)

	case ws.TypeReactionRemove:
		s.handleReaction(ctx, client, env, false)

	default:
		s.sendRaw(client, ws.ErrorEnvelope(env.ID, "unknown_type",
			"this message type is not supported by the server yet: "+env.Type))
	}
}

func (s *Server) sendEnvelope(client *ws.Client, msgType, id string, payload any) {
	env, err := ws.NewEnvelope(msgType, id, payload)
	if err != nil {
		slog.Error("build envelope", "type", msgType, "err", err)
		return
	}
	s.sendRaw(client, env)
}

func (s *Server) sendRaw(client *ws.Client, env ws.Envelope) {
	raw, err := json.Marshal(env)
	if err != nil {
		slog.Error("marshal envelope", "type", env.Type, "err", err)
		return
	}
	client.Send(raw)
}
