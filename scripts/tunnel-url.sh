#!/usr/bin/env bash
# Prints the public address of the running quick tunnel - the string you type
# into the Nuva app on your phone.
#
#   ./scripts/tunnel-url.sh
set -euo pipefail

if ! docker compose ps --services --status running | grep -qx "tunnel-quick"; then
    echo "tunnel-quick is not running. Start it with:" >&2
    echo "  docker compose --profile tunnel up -d --build" >&2
    exit 1
fi

for _ in $(seq 1 30); do
    url="$(docker compose logs tunnel-quick 2>/dev/null \
        | grep -Eo 'https://[a-z0-9-]+\.trycloudflare\.com' \
        | tail -n 1 || true)"
    if [ -n "${url}" ]; then
        echo "${url}"
        echo
        echo "Paste that into the Nuva app on the 'Choose your server' screen."
        echo "It changes every time the tunnel restarts - that is expected."
        exit 0
    fi
    sleep 2
done

echo "No tunnel URL in the logs yet. Check: docker compose logs tunnel-quick" >&2
exit 1
