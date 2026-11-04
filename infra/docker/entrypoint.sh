#!/bin/sh
# Lunara container entrypoint.
#
# Environment variables always win – even when set to an empty value – so
# deployments that manage their own secrets behave exactly as before.
#
# For the one-file installation (examples/docker-compose.yml) a volume is
# mounted at /secrets. The "init-secrets" command creates all secrets there
# once (mode 0600); Lunara, the migration and PostgreSQL only read them.
set -eu
umask 077

SECRETS_DIR="${LUNARA_SECRETS_DIR:-/secrets}"
NAMES="postgres_password app_master_key auth_signing_key refresh_token_pepper bootstrap_token"

generate() {
  case "$1" in
    app_master_key) openssl rand -base64 32 | tr -d '\n' ;;
    postgres_password | bootstrap_token) openssl rand -hex 24 ;;
    *) openssl rand -base64 48 | tr -d '\n' ;;
  esac
}

init_secrets() {
  if [ ! -d "$SECRETS_DIR" ] || [ ! -w "$SECRETS_DIR" ]; then
    echo "lunara: $SECRETS_DIR is not a writable volume" >&2
    exit 1
  fi
  # Only one initializer at a time; a second one waits for the first.
  waited=0
  until mkdir "$SECRETS_DIR/.lock" 2>/dev/null; do
    # A lock older than two minutes was left by an aborted initializer.
    if [ -n "$(find "$SECRETS_DIR/.lock" -maxdepth 0 -mmin +2 2>/dev/null)" ]; then
      rmdir "$SECRETS_DIR/.lock" 2>/dev/null || true
      continue
    fi
    waited=$((waited + 1))
    if [ "$waited" -ge 60 ]; then
      echo "lunara: $SECRETS_DIR/.lock is held by another initializer (remove it if none is running)" >&2
      exit 1
    fi
    sleep 1
  done
  trap 'rmdir "$SECRETS_DIR/.lock" 2>/dev/null || true' EXIT
  trap 'exit 1' INT TERM
  present=0
  for name in $NAMES; do
    [ -s "$SECRETS_DIR/$name" ] && present=$((present + 1))
  done
  if [ "$present" -eq 5 ]; then
    echo "lunara: secrets are ready" >&2
    exit 0
  fi
  if [ "$present" -ne 0 ]; then
    # Never replace only some keys: new keys cannot read existing data.
    echo "lunara: the secrets volume is incomplete ($present of 5 files)." >&2
    echo "lunara: restore it from your backup instead of creating new keys." >&2
    exit 1
  fi
  for name in $NAMES; do
    tmp="$(mktemp "$SECRETS_DIR/.$name.XXXXXX")"
    generate "$name" > "$tmp"
    [ -s "$tmp" ] || { echo "lunara: could not create $name" >&2; exit 1; }
    mv "$tmp" "$SECRETS_DIR/$name"
  done
  echo "lunara: created new secrets in the $SECRETS_DIR volume – back it up together with the database" >&2
  exit 0
}

# load VAR file: a set variable wins (even if empty); otherwise read the volume.
load() {
  eval "is_set=\${$1+x}"
  [ -n "$is_set" ] && return 0
  [ -s "$SECRETS_DIR/$2" ] || return 0
  value="$(cat "$SECRETS_DIR/$2")"
  export "$1=$value"
}

if [ "${1:-}" = "init-secrets" ]; then
  init_secrets
fi

load POSTGRES_PASSWORD postgres_password
load APP_MASTER_KEY app_master_key
load AUTH_SIGNING_KEY auth_signing_key
load REFRESH_TOKEN_PEPPER refresh_token_pepper
load BOOTSTRAP_TOKEN bootstrap_token

if [ -z "${DATABASE_URL+x}" ] && [ -n "${POSTGRES_PASSWORD:-}" ]; then
  case "${POSTGRES_USER:-lunara}${POSTGRES_PASSWORD}" in
    *[!A-Za-z0-9_-]*)
      echo "lunara: set DATABASE_URL yourself when the database user or password contains special characters" >&2
      exit 1 ;;
  esac
  export DATABASE_URL="postgresql://${POSTGRES_USER:-lunara}:${POSTGRES_PASSWORD}@${POSTGRES_HOST:-postgres}:5432/${POSTGRES_DB:-lunara}?schema=public"
fi

# Print the one-time setup code only while the server still needs its first
# account, and only for codes that live in the secrets volume.
if [ "${1:-}" = "node" ] && [ -s "$SECRETS_DIR/bootstrap_token" ] \
  && [ "${BOOTSTRAP_TOKEN:-}" = "$(cat "$SECRETS_DIR/bootstrap_token")" ]; then
  node -e '
    const port = process.env.PORT || "3000";
    const deadline = Date.now() + 120000;
    const check = () => fetch(`http://127.0.0.1:${port}/api/v1/meta`)
      .then((r) => { if (!r.ok) throw new Error(String(r.status)); return r.json(); })
      .then((meta) => {
        if (!meta.setupRequired) return;
        console.error(`
========================================================================
  Lunara setup code (only needed once, for the very first account):

      ${process.env.BOOTSTRAP_TOKEN}

  Open ${process.env.PUBLIC_BASE_URL || "your Lunara address"}, choose "My own server"
  and create your account.
========================================================================
`);
      })
      .catch(() => { if (Date.now() < deadline) setTimeout(check, 2000); });
    setTimeout(check, 2000);
  ' &
fi

exec "$@"
