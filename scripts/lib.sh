#!/usr/bin/env bash
# Shared helpers for demo and smoke scripts. Requires curl, jq, openssl.
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
KEYCLOAK="${KEYCLOAK:-http://localhost:8180}"
REALM="${REALM:-workflow-platform}"

log() { printf '\033[1;34m==>\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31mFAIL:\033[0m %s\n' "$*" >&2; exit 1; }

# token <user> <password>: password grant through the dev-only workflow-cli client
token() {
  curl -fsS -X POST "$KEYCLOAK/realms/$REALM/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=workflow-cli -d "username=$1" -d "password=$2" | jq -r .access_token
}

# api <method> <path> [json-body]: authenticated call through the gateway, prints the response body
api() {
  local method=$1 path=$2 body=${3:-}
  local args=(-sS -X "$method" "$GATEWAY$path" -H "Authorization: Bearer $TOKEN" -w '\n%{http_code}')
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' -d "$body")
  local out code
  out=$(curl "${args[@]}")
  code=${out##*$'\n'}
  out=${out%$'\n'*}
  if [[ "$code" -ge 400 ]]; then
    echo "$out" >&2
    fail "$method $path returned HTTP $code"
  fi
  echo "$out"
}

# api_try <method> <path>: like api but prints nothing and returns non-zero on HTTP errors (for polling)
api_try() {
  curl -fsS -X "$1" "$GATEWAY$2" -H "Authorization: Bearer $TOKEN" 2>/dev/null
}

# signed_webhook <url> <secret> <json-body> [delivery-id]
signed_webhook() {
  local url=$1 secret=$2 body=$3 delivery=${4:-$(uuidgen 2>/dev/null || date +%s%N)}
  local ts sig
  ts=$(date +%s)
  sig="sha256=$(printf '%s.%s' "$ts" "$body" | openssl dgst -sha256 -hmac "$secret" -hex | awk '{print $NF}')"
  curl -sS -X POST "$url" -H 'Content-Type: application/json' -H "X-Webhook-Timestamp: $ts" \
    -H "X-Webhook-Signature: $sig" -H "X-Webhook-Delivery-Id: $delivery" -d "$body" -w '\n%{http_code}'
}

# wait_for_execution <id> [timeout-seconds]: polls until a terminal status, prints the execution JSON
wait_for_execution() {
  local id=$1 timeout=${2:-60} start status json
  start=$(date +%s)
  while true; do
    json=$(api GET "/api/v1/executions/$id")
    status=$(jq -r .status <<<"$json")
    case "$status" in COMPLETED|FAILED|CANCELLED|TIMED_OUT) echo "$json"; return 0 ;; esac
    (( $(date +%s) - start > timeout )) && { echo "$json"; fail "execution $id still $status after ${timeout}s"; }
    sleep 1
  done
}

print_nodes() {
  jq -r '.nodes[] | "  \(.nodeId | .[0:12] | . + (" " * (12 - length)))  \(.status | . + (" " * (10 - length)))  attempts=\(.attemptCount)  \(if .branch then "branch=" + .branch else "" end)"' <<<"$1"
}
