#!/usr/bin/env bash
# Creates the ONE release keystore for Nuva.
#
# Run this exactly once, ever. Every future release must be signed with this
# same key, otherwise Android refuses to install the update over the old
# version and users lose their local data.
#
# After running:
#   1. Back up android/nuva-release.jks to at least two offline places.
#   2. Add the four GitHub secrets printed at the end.
#   3. Never commit the .jks or keystore.properties (both are git-ignored).

set -euo pipefail

KEYSTORE_PATH="${KEYSTORE_PATH:-android/nuva-release.jks}"
KEY_ALIAS="${KEY_ALIAS:-nuva}"
VALIDITY_DAYS="${VALIDITY_DAYS:-14600}"   # 40 years
DNAME="${DNAME:-CN=Nuva, OU=Nuva, O=Nuva, L=Unknown, S=Unknown, C=RU}"

if ! command -v keytool >/dev/null 2>&1; then
  echo "error: keytool not found. Install a JDK 17 first (apt install openjdk-17-jdk)." >&2
  exit 1
fi

if [[ -f "$KEYSTORE_PATH" ]]; then
  echo "refusing to overwrite existing keystore: $KEYSTORE_PATH" >&2
  echo "if you really lost the passwords, you must publish under a new applicationId." >&2
  exit 1
fi

read -rsp "Keystore password (min 6 chars, write it down): " STORE_PASS; echo
read -rsp "Repeat keystore password: " STORE_PASS2; echo
if [[ "$STORE_PASS" != "$STORE_PASS2" ]]; then
  echo "passwords do not match" >&2
  exit 1
fi
if [[ ${#STORE_PASS} -lt 6 ]]; then
  echo "password must be at least 6 characters" >&2
  exit 1
fi

mkdir -p "$(dirname "$KEYSTORE_PATH")"

keytool -genkeypair \
  -alias "$KEY_ALIAS" \
  -keyalg RSA -keysize 4096 \
  -validity "$VALIDITY_DAYS" \
  -keystore "$KEYSTORE_PATH" \
  -storetype PKCS12 \
  -storepass "$STORE_PASS" \
  -keypass "$STORE_PASS" \
  -dname "$DNAME"

cat > android/keystore.properties <<PROPS
# Local signing config. Git-ignored. Do not share.
NUVA_KEYSTORE_PATH=$(basename "$KEYSTORE_PATH")
NUVA_KEYSTORE_PASSWORD=$STORE_PASS
NUVA_KEY_ALIAS=$KEY_ALIAS
NUVA_KEY_PASSWORD=$STORE_PASS
PROPS
chmod 600 android/keystore.properties

echo
echo "Keystore created: $KEYSTORE_PATH"
echo "SHA-256 fingerprint (publish this so users can verify your APKs):"
keytool -list -v -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASS" -alias "$KEY_ALIAS" \
  | grep -i "SHA256:" || true

echo
echo "==> Now add these four GitHub repository secrets"
echo "    (Settings -> Secrets and variables -> Actions -> New repository secret):"
echo
echo "  NUVA_KEYSTORE_BASE64   <- the single line printed below"
echo "  NUVA_KEYSTORE_PASSWORD <- the password you just typed"
echo "  NUVA_KEY_ALIAS         <- $KEY_ALIAS"
echo "  NUVA_KEY_PASSWORD      <- the password you just typed"
echo
echo "----- NUVA_KEYSTORE_BASE64 (copy the whole line) -----"
base64 -w 0 "$KEYSTORE_PATH" 2>/dev/null || base64 "$KEYSTORE_PATH" | tr -d '\n'
echo
echo "------------------------------------------------------"
echo
echo "BACK UP $KEYSTORE_PATH NOW. Losing it means you can never update Nuva again."
