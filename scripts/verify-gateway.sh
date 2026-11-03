#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

credential_file="${1:-${LUNARA_GATEWAY_URL_FILE:-/docker/lunara/lunara-app-headerauth.url}}"
public_host="${LUNARA_PUBLIC_HOST:-lunara.3li361.de}"
resolve_ip="${LUNARA_GATEWAY_RESOLVE_IP:-}"
allow_untrusted_tls="${LUNARA_GATEWAY_ALLOW_UNTRUSTED_TLS:-false}"

if ! command -v python3 >/dev/null 2>&1; then
  echo "Missing tool: python3" >&2
  exit 1
fi

case "${allow_untrusted_tls}" in
  false)
    ;;
  true)
    echo "WARNING: TLS certificate verification is disabled for this gateway test only." >&2
    ;;
  *)
    echo "LUNARA_GATEWAY_ALLOW_UNTRUSTED_TLS accepts only true or false." >&2
    exit 2
    ;;
esac

if ! python3 - "${credential_file}" "${public_host}" "${resolve_ip}" "${allow_untrusted_tls}" <<'PYTHON'
import base64
import http.client
import ipaddress
import json
import os
import socket
import ssl
import stat
import sys
from urllib.parse import unquote, urlsplit


class VerificationError(Exception):
    pass


def safe_excepthook(exception_type, exception, traceback):
    if issubclass(exception_type, VerificationError):
        print(f"Gateway verification: {exception}", file=sys.stderr)
    else:
        print("Gateway verification: internal verification flow error.", file=sys.stderr)


sys.excepthook = safe_excepthook


def fail(message):
    raise VerificationError(message)


credential_path, expected_host, resolve_ip, allow_untrusted_tls = sys.argv[1:]
if not os.path.isabs(credential_path):
    fail("The gateway file path must be absolute.")
try:
    file_stat = os.lstat(credential_path)
except OSError:
    fail("The gateway file is not readable.")
if (
    not stat.S_ISREG(file_stat.st_mode)
    or stat.S_ISLNK(file_stat.st_mode)
    or file_stat.st_uid != os.geteuid()
    or stat.S_IMODE(file_stat.st_mode) != 0o600
):
    fail("The gateway file must be a regular file owned by the current user with mode 0600.")
try:
    with open(credential_path, "r", encoding="ascii", newline="") as credential_stream:
        raw_url = credential_stream.read(2049)
except (OSError, UnicodeError):
    fail("The gateway file could not be read safely.")
if len(raw_url) > 2048 or "\r" in raw_url or raw_url.count("\n") != 1 or not raw_url.endswith("\n"):
    fail("The gateway file must contain exactly one short ASCII URL.")
gateway_url = raw_url[:-1]
del raw_url
try:
    parsed = urlsplit(gateway_url)
except ValueError:
    fail("The gateway file does not contain a supported URL.")
del gateway_url
if (
    parsed.scheme != "https"
    or parsed.hostname != expected_host
    or parsed.port not in (None, 443)
    or parsed.path not in ("", "/")
    or parsed.query
    or parsed.fragment
    or parsed.username is None
    or parsed.password is None
):
    fail("The gateway file does not contain a canonical Lunara HTTPS URL.")
username = unquote(parsed.username)
password = unquote(parsed.password)
if (
    username != "lunara-native-app"
    or not (20 <= len(password) <= 255)
    or not password.isascii()
    or not password.isalnum()
):
    fail("The gateway file does not contain the expected technical app credentials.")
del parsed

if resolve_ip:
    try:
        canonical_ip = str(ipaddress.ip_address(resolve_ip))
    except ValueError:
        fail("LUNARA_GATEWAY_RESOLVE_IP is not a canonical IP address.")
    if canonical_ip != resolve_ip or "%" in resolve_ip:
        fail("LUNARA_GATEWAY_RESOLVE_IP is not a canonical IP address.")

tls_context = ssl.create_default_context()
tls_context.minimum_version = ssl.TLSVersion.TLSv1_2
if allow_untrusted_tls == "true":
    tls_context.check_hostname = False
    tls_context.verify_mode = ssl.CERT_NONE


class ResolvedHTTPSConnection(http.client.HTTPSConnection):
    def connect(self):
        target = resolve_ip or self.host
        plain_socket = socket.create_connection((target, self.port), self.timeout)
        self.sock = self._context.wrap_socket(plain_socket, server_hostname=self.host)


def request(path, authenticated):
    connection = ResolvedHTTPSConnection(
        expected_host,
        443,
        timeout=15,
        context=tls_context,
    )
    headers = {
        "Accept": "application/json",
        "Host": expected_host,
        "User-Agent": "Lunara-Gateway-Verifier/1",
    }
    if authenticated:
        basic_value = base64.b64encode(f"{username}:{password}".encode("ascii")).decode("ascii")
        headers["Authorization"] = f"Basic {basic_value}"
    try:
        connection.request("GET", path, headers=headers)
        response = connection.getresponse()
        body = response.read(2_097_153)
        if len(body) > 2_097_152:
            fail("A gateway response exceeds the safe verification size limit.")
        return response.status, response.getheader("Location"), body
    except (OSError, ssl.SSLError, http.client.HTTPException):
        fail("TLS, DNS, or connection error during gateway verification.")
    finally:
        connection.close()


meta_status, _, meta_body = request("/api/v1/meta", authenticated=True)
try:
    meta = json.loads(meta_body)
except (UnicodeError, json.JSONDecodeError):
    meta = None
if (
    meta_status != 200
    or not isinstance(meta, dict)
    or meta.get("apiVersion") != "v1"
    or not isinstance(meta.get("name"), str)
    or not isinstance(meta.get("setupRequired"), bool)
    or not isinstance(meta.get("registrationEnabled"), bool)
):
    fail("Header authentication did not return a valid Lunara metadata response.")
del meta_body, meta

# Native clients need one non-sensitive discovery endpoint before they know whether
# Authentik is enabled. It must stay cache-free and expose no account information.
public_meta_status, _, public_meta_body = request("/api/v1/meta", authenticated=False)
try:
    public_meta = json.loads(public_meta_body)
except (UnicodeError, json.JSONDecodeError):
    public_meta = None
if (
    public_meta_status != 200
    or not isinstance(public_meta, dict)
    or public_meta.get("apiVersion") != "v1"
    or not isinstance(public_meta.get("authenticationMethods"), list)
):
    fail("The intentionally public metadata discovery endpoint does not return a valid, non-sensitive contract.")
del public_meta_body, public_meta

docs_status, _, docs_body = request("/api-docs-json", authenticated=True)
try:
    docs = json.loads(docs_body)
except (UnicodeError, json.JSONDecodeError):
    docs = None
if (
    docs_status != 200
    or not isinstance(docs, dict)
    or not isinstance(docs.get("openapi"), str)
    or not isinstance(docs.get("info"), dict)
    or not isinstance(docs["info"].get("title"), str)
):
    fail("Header authentication did not return a valid protected Lunara OpenAPI response.")
del docs_body, docs

for protected_path in (
    "/",
    "/health",
    "/api-docs-json",
    "/api/v1/auth/login",
    "/api/v1/auth/oidc/login",
):
    status_code, redirect_location, body = request(protected_path, authenticated=False)
    if status_code not in (302, 303, 307, 308) or not redirect_location:
        fail("At least one Lunara path is accessible without Authentik credentials.")
    try:
        redirect = urlsplit(redirect_location)
    except ValueError:
        fail("Authentik returned an invalid redirect target.")
    if redirect.username is not None or redirect.password is not None:
        fail("Authentik returned an unsafe redirect target.")
    if redirect.scheme and redirect.scheme != "https":
        fail("Authentik did not redirect to HTTPS.")
    del body, redirect

# Discard credentials before emitting the non-sensitive result line.
username = ""
password = ""
print(
    "Gateway verification succeeded: technical app access and public metadata discovery "
    "work; all other verified Lunara paths require Authentik."
)
PYTHON
then
  echo "Gateway verification failed; credentials will not be printed." >&2
  exit 1
fi
