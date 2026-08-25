#!/usr/bin/env bash
# Fills the empty secrets in .env with strong random values.
# Safe to run twice: existing non-empty values are left alone.

set -euo pipefail

cd "$(dirname "$0")/.."

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "created .env from .env.example"
fi

random() { LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c "$1"; }

fill() {
  local key="$1" length="$2"
  local current
  current="$(grep -E "^${key}=" .env | head -n1 | cut -d= -f2- || true)"
  if [[ -n "$current" ]]; then
    echo "  $key already set, leaving it alone"
    return
  fi
  local value
  value="$(random "$length")"
  # Portable in-place edit (GNU and BSD sed behave differently with -i).
  awk -v k="$key" -v v="$value" \
    'BEGIN{FS=OFS="="} $1==k{print k"="v; next} {print}' .env > .env.tmp
  mv .env.tmp .env
  echo "  $key generated ($length chars)"
}

echo "generating secrets in .env"
fill NUVA_JWT_SECRET 48
fill POSTGRES_PASSWORD 32

echo
echo "done. Remaining values to review by hand in .env:"
echo "  NUVA_DOMAIN         (your domain, e.g. api.nuva.club)"
echo "  NUVA_ACME_EMAIL     (for Let's Encrypt expiry notices)"
echo "  NUVA_ALLOWED_ORIGINS"
