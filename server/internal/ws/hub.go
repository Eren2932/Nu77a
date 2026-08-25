// Package ws holds the realtime layer: a single in-process hub that keeps one
// entry per connected client. When we outgrow one server instance we put Redis
// pub/sub behind the same Hub interface and nothing above it changes.
package ws

import (
	"encoding/json"
	"log/slog"
	"sync"

	"github.com/google/uuid"
)

// sendBuffer is how many messages we hold for a slow client before we give up
// on it. A stalled phone must never block the whole server.
const sendBuffer = 64

type Client struct {
	ID     uuid.UUID
	UserID uuid.UUID
	send   chan []byte
	close  chan struct{}
	once   sync.Once
}

func NewClient(userID uuid.UUID) *Client {
	return &Client{
		ID:     uuid.New(),
		UserID: userID,
		send:   make(chan []byte, sendBuffer),
		close:  make(chan struct{}),
	}
}

// Outbound is the queue the writer goroutine reads from.
func (c *Client) Outbound() <-chan []byte { return c.send }

// Closed is signalled when the server wants this connection gone.
func (c *Client) Closed() <-chan struct{} { return c.close }

func (c *Client) Kill() {
	c.once.Do(func() { close(c.close) })
}

// enqueue never blocks: a client that cannot keep up gets disconnected and
// will resync over HTTP on reconnect.
func (c *Client) enqueue(payload []byte) bool {
	select {
	case c.send <- payload:
		return true
	default:
		slog.Warn("ws client send buffer full, dropping connection",
			"user_id", c.UserID, "client_id", c.ID)
		c.Kill()
		return false
	}
}

// Send delivers a payload to this one connection only.
func (c *Client) Send(payload []byte) bool { return c.enqueue(payload) }

type Hub struct {
	mu      sync.RWMutex
	clients map[uuid.UUID]map[*Client]struct{}
}

func NewHub() *Hub {
	return &Hub{clients: make(map[uuid.UUID]map[*Client]struct{})}
}

func (h *Hub) Register(c *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()

	if _, ok := h.clients[c.UserID]; !ok {
		h.clients[c.UserID] = make(map[*Client]struct{})
	}
	h.clients[c.UserID][c] = struct{}{}
}

func (h *Hub) Unregister(c *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()

	if set, ok := h.clients[c.UserID]; ok {
		delete(set, c)
		if len(set) == 0 {
			delete(h.clients, c.UserID)
		}
	}
	c.Kill()
}

// SendToUser fans a payload out to every device of one user and reports how
// many connections accepted it.
func (h *Hub) SendToUser(userID uuid.UUID, payload []byte) int {
	h.mu.RLock()
	targets := make([]*Client, 0, 4)
	for c := range h.clients[userID] {
		targets = append(targets, c)
	}
	h.mu.RUnlock()

	delivered := 0
	for _, c := range targets {
		if c.enqueue(payload) {
			delivered++
		}
	}
	return delivered
}

// SendJSONToUsers marshals once and delivers to many users.
func (h *Hub) SendJSONToUsers(userIDs []uuid.UUID, v any) error {
	payload, err := json.Marshal(v)
	if err != nil {
		return err
	}
	for _, id := range userIDs {
		h.SendToUser(id, payload)
	}
	return nil
}

func (h *Hub) DisconnectUser(userID uuid.UUID) {
	h.mu.RLock()
	targets := make([]*Client, 0, 4)
	for c := range h.clients[userID] {
		targets = append(targets, c)
	}
	h.mu.RUnlock()

	for _, c := range targets {
		c.Kill()
	}
}

func (h *Hub) IsOnline(userID uuid.UUID) bool {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.clients[userID]) > 0
}

// OnlineCount counts distinct users, not sockets.
func (h *Hub) OnlineCount() int {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.clients)
}

func (h *Hub) ConnectionCount() int {
	h.mu.RLock()
	defer h.mu.RUnlock()

	total := 0
	for _, set := range h.clients {
		total += len(set)
	}
	return total
}

// CloseAll is used on graceful shutdown.
func (h *Hub) CloseAll() {
	h.mu.Lock()
	defer h.mu.Unlock()

	for _, set := range h.clients {
		for c := range set {
			c.Kill()
		}
	}
	h.clients = make(map[uuid.UUID]map[*Client]struct{})
}
