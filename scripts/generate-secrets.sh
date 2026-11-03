#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /absoluter/pfad/.env" >&2
  exit 2
fi

target="$1"
if [[ "${target}" != /* ]]; then
  echo "Only an absolute destination path is allowed." >&2
  exit 2
fi
if [[ -e "${target}" ]]; then
  echo "The destination file already exists; it will not be overwritten: ${target}" >&2
  exit 1
fi

install -d -m 0700 -- "$(dirname -- "${target}")"
# URL-safe because Compose embeds this value in a PostgreSQL connection URI.
postgres_password="$(openssl rand -hex 32)"
master_key="$(openssl rand -base64 32 | tr -d '\n')"
signing_key="$(openssl rand -base64 48 | tr -d '\n')"
refresh_pepper="$(openssl rand -base64 48 | tr -d '\n')"
bootstrap_token="$(openssl rand -base64 32 | tr -d '\n')"

sed \
  -e "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=${postgres_password}|" \
  -e "s|^APP_MASTER_KEY=.*|APP_MASTER_KEY=${master_key}|" \
  -e "s|^AUTH_SIGNING_KEY=.*|AUTH_SIGNING_KEY=${signing_key}|" \
  -e "s|^REFRESH_TOKEN_PEPPER=.*|REFRESH_TOKEN_PEPPER=${refresh_pepper}|" \
  -e "s|^BOOTSTRAP_TOKEN=.*|BOOTSTRAP_TOKEN=${bootstrap_token}|" \
  "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)/.env.example" > "${target}"
chmod 0600 -- "${target}"
unset postgres_password master_key signing_key refresh_pepper bootstrap_token
echo "Secret file created with mode 0600: ${target}"
echo "Use BOOTSTRAP_TOKEN and APP_MASTER_KEY only locally from the file; never commit them."
