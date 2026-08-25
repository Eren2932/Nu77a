package auth

import (
	"testing"
	"time"

	"github.com/google/uuid"
)

func TestAccessTokenRoundTrip(t *testing.T) {
	svc := NewTokenService("test-secret-that-is-long-enough-000", time.Minute, time.Hour)
	userID, sessionID := uuid.New(), uuid.New()

	token, expiresAt, err := svc.NewAccessToken(userID, sessionID)
	if err != nil {
		t.Fatalf("issue token: %v", err)
	}
	if !expiresAt.After(time.Now()) {
		t.Fatalf("expiry must be in the future, got %v", expiresAt)
	}

	gotUser, gotSession, err := svc.ParseAccessToken(token)
	if err != nil {
		t.Fatalf("parse token: %v", err)
	}
	if gotUser != userID || gotSession != sessionID {
		t.Fatalf("claims mismatch: %v/%v", gotUser, gotSession)
	}
}

func TestAccessTokenRejectsForeignSecret(t *testing.T) {
	issuer := NewTokenService("secret-one-secret-one-secret-one-1", time.Minute, time.Hour)
	verifier := NewTokenService("secret-two-secret-two-secret-two-2", time.Minute, time.Hour)

	token, _, err := issuer.NewAccessToken(uuid.New(), uuid.New())
	if err != nil {
		t.Fatalf("issue token: %v", err)
	}
	if _, _, err := verifier.ParseAccessToken(token); err == nil {
		t.Fatal("expected a token signed with another secret to be rejected")
	}
}

func TestRefreshTokenIsHashedNotStored(t *testing.T) {
	token, hash, err := NewRefreshToken()
	if err != nil {
		t.Fatalf("new refresh token: %v", err)
	}
	if token == hash {
		t.Fatal("stored hash must differ from the token handed to the client")
	}
	if HashToken(token) != hash {
		t.Fatal("hash must be reproducible from the token")
	}
}

func TestRecoveryCodeNormalization(t *testing.T) {
	code, err := NewRecoveryCode()
	if err != nil {
		t.Fatalf("new recovery code: %v", err)
	}
	if NormalizeRecoveryCode(code) != NormalizeRecoveryCode("  "+code+"  ") {
		t.Fatal("normalization must ignore surrounding whitespace")
	}
	if NormalizeRecoveryCode("nuva-o0i1") != NormalizeRecoveryCode("NVVA-0011") {
		t.Fatalf("lookalike characters must fold together")
	}
}

func TestPasswordHashing(t *testing.T) {
	if _, err := HashSecret("short"); err == nil {
		t.Fatal("expected short passwords to be rejected")
	}
	hash, err := HashSecret("correct horse battery")
	if err != nil {
		t.Fatalf("hash: %v", err)
	}
	if err := VerifySecret(hash, "correct horse battery"); err != nil {
		t.Fatalf("verify valid password: %v", err)
	}
	if err := VerifySecret(hash, "wrong password"); err == nil {
		t.Fatal("expected wrong password to fail")
	}
}
