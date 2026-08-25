package auth

import (
	"errors"
	"fmt"
	"unicode/utf8"

	"golang.org/x/crypto/bcrypt"
)

// bcrypt silently truncates input at 72 bytes, so we reject longer secrets
// instead of pretending they were fully checked.
const maxSecretBytes = 72

var (
	ErrSecretTooShort = errors.New("secret too short")
	ErrSecretTooLong  = errors.New("secret too long")
	ErrBadCredentials = errors.New("invalid credentials")
)

func HashSecret(secret string) (string, error) {
	if utf8.RuneCountInString(secret) < 8 {
		return "", ErrSecretTooShort
	}
	if len(secret) > maxSecretBytes {
		return "", ErrSecretTooLong
	}
	h, err := bcrypt.GenerateFromPassword([]byte(secret), 12)
	if err != nil {
		return "", fmt.Errorf("hash secret: %w", err)
	}
	return string(h), nil
}

func VerifySecret(hash, secret string) error {
	if len(secret) > maxSecretBytes {
		return ErrBadCredentials
	}
	if err := bcrypt.CompareHashAndPassword([]byte(hash), []byte(secret)); err != nil {
		return ErrBadCredentials
	}
	return nil
}
