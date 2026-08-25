package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

// Config holds every runtime setting of the server.
// Rule of the project: nothing is read from os.Getenv outside this file.
type Config struct {
	Env            string
	Port           int
	DatabaseURL    string
	JWTSecret      string
	AccessTTL      time.Duration
	RefreshTTL     time.Duration
	AllowedOrigins []string
	MediaDir       string
	MaxUploadBytes int64
}

func (c Config) IsProd() bool { return c.Env == "production" }

// Load reads the environment and fails loudly when something required is
// missing. Better a crash on boot than a mystery 500 in production.
func Load() (Config, error) {
	var missing []string

	cfg := Config{
		Env:            str("NUVA_ENV", "development"),
		Port:           num("NUVA_PORT", 8080),
		DatabaseURL:    str("NUVA_DATABASE_URL", ""),
		JWTSecret:      str("NUVA_JWT_SECRET", ""),
		AccessTTL:      dur("NUVA_ACCESS_TTL", 15*time.Minute),
		RefreshTTL:     dur("NUVA_REFRESH_TTL", 60*24*time.Hour),
		AllowedOrigins: list("NUVA_ALLOWED_ORIGINS", "*"),
		MediaDir:       str("NUVA_MEDIA_DIR", "/data/media"),
		MaxUploadBytes: int64(num("NUVA_MAX_UPLOAD_MB", 100)) * 1024 * 1024,
	}

	if cfg.DatabaseURL == "" {
		missing = append(missing, "NUVA_DATABASE_URL")
	}
	if cfg.JWTSecret == "" {
		missing = append(missing, "NUVA_JWT_SECRET")
	}
	if len(missing) > 0 {
		return cfg, fmt.Errorf("missing required env vars: %s", strings.Join(missing, ", "))
	}
	if cfg.IsProd() && len(cfg.JWTSecret) < 32 {
		return cfg, fmt.Errorf("NUVA_JWT_SECRET must be at least 32 chars in production")
	}
	return cfg, nil
}

func str(key, def string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return def
}

func num(key string, def int) int {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return def
}

func dur(key string, def time.Duration) time.Duration {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		if d, err := time.ParseDuration(v); err == nil {
			return d
		}
	}
	return def
}

func list(key, def string) []string {
	raw := str(key, def)
	parts := strings.Split(raw, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}
