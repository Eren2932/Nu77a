package auth

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

const (
	issuer      = "nuva"
	audienceApp = "nuva-app"
)

var ErrInvalidToken = errors.New("invalid token")

type Claims struct {
	jwt.RegisteredClaims
	SessionID string `json:"sid"`
}

type TokenService struct {
	secret     []byte
	accessTTL  time.Duration
	refreshTTL time.Duration
}

func NewTokenService(secret string, accessTTL, refreshTTL time.Duration) *TokenService {
	return &TokenService{secret: []byte(secret), accessTTL: accessTTL, refreshTTL: refreshTTL}
}

func (t *TokenService) AccessTTL() time.Duration  { return t.accessTTL }
func (t *TokenService) RefreshTTL() time.Duration { return t.refreshTTL }

// NewAccessToken issues a short-lived JWT bound to a session id.
func (t *TokenService) NewAccessToken(userID, sessionID uuid.UUID) (string, time.Time, error) {
	now := time.Now()
	expiresAt := now.Add(t.accessTTL)

	claims := Claims{
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    issuer,
			Subject:   userID.String(),
			Audience:  jwt.ClaimStrings{audienceApp},
			IssuedAt:  jwt.NewNumericDate(now),
			NotBefore: jwt.NewNumericDate(now.Add(-30 * time.Second)),
			ExpiresAt: jwt.NewNumericDate(expiresAt),
			ID:        uuid.NewString(),
		},
		SessionID: sessionID.String(),
	}

	signed, err := jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString(t.secret)
	if err != nil {
		return "", time.Time{}, fmt.Errorf("sign access token: %w", err)
	}
	return signed, expiresAt, nil
}

// ParseAccessToken validates signature, expiry, issuer and audience.
func (t *TokenService) ParseAccessToken(raw string) (userID uuid.UUID, sessionID uuid.UUID, err error) {
	claims := &Claims{}
	_, err = jwt.ParseWithClaims(raw, claims, func(token *jwt.Token) (any, error) {
		if token.Method.Alg() != jwt.SigningMethodHS256.Alg() {
			return nil, fmt.Errorf("unexpected signing method %q", token.Method.Alg())
		}
		return t.secret, nil
	},
		jwt.WithIssuer(issuer),
		jwt.WithAudience(audienceApp),
		jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}),
	)
	if err != nil {
		return uuid.Nil, uuid.Nil, ErrInvalidToken
	}

	userID, err = uuid.Parse(claims.Subject)
	if err != nil {
		return uuid.Nil, uuid.Nil, ErrInvalidToken
	}
	sessionID, err = uuid.Parse(claims.SessionID)
	if err != nil {
		return uuid.Nil, uuid.Nil, ErrInvalidToken
	}
	return userID, sessionID, nil
}

// NewRefreshToken returns an opaque random token. Only its SHA-256 hash is
// ever stored, so a database leak does not hand out live sessions.
func NewRefreshToken() (token string, hash string, err error) {
	buf := make([]byte, 32)
	if _, err = rand.Read(buf); err != nil {
		return "", "", fmt.Errorf("read random: %w", err)
	}
	token = base64.RawURLEncoding.EncodeToString(buf)
	return token, HashToken(token), nil
}

func HashToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}
