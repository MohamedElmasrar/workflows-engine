#!/usr/bin/env bash
# End-to-end smoke test against a running `docker compose up` environment:
# Webhook → Transform → HTTP Request → Condition → Notification, high-risk and low-risk branches.
source "$(dirname "$0")/lib.sh"

log "Authenticating as demo/demo"
TOKEN=$(token demo demo)
[[ -n "$TOKEN" && "$TOKEN" != null ]] || fail "could not obtain a token"
api GET /api/v1/me | jq -c .

log "Creating the demo workflow"
DEFINITION=$(cat "$(dirname "$0")/order-webhook-demo.json")
WF=$(api POST /api/v1/workflows "$(jq -n --argjson d "$DEFINITION" '{name: "Order fraud check (smoke)", description: "Webhook → Transform → HTTP → Condition → Notification", definition: $d}')")
WF_ID=$(jq -r .id <<<"$WF")
log "Workflow $WF_ID; validating"
api POST "/api/v1/workflows/$WF_ID/validate" | jq -e '.valid == true' > /dev/null || fail "definition invalid"

log "Publishing version 1"
api POST "/api/v1/workflows/$WF_ID/publish" | jq -c '{version, publishedAt}'

log "Waiting for the webhook registration"
for _ in $(seq 1 30); do
  URL=$(api_try GET "/api/v1/workflows/$WF_ID/triggers" | jq -r '.webhook.url // empty' || true)
  [[ -n "$URL" ]] && break
  sleep 1
done
[[ -n "${URL:-}" ]] || fail "webhook was not registered"
SECRET=$(api POST "/api/v1/workflows/$WF_ID/triggers/webhook/secret" | jq -r .secret)
log "Webhook URL: ${URL%/*}/<token>"

run_order() {
  local body=$1 expect_branch=$2
  local response code execution_id execution
  response=$(signed_webhook "$URL" "$SECRET" "$body")
  code=${response##*$'\n'}
  [[ "$code" == 202 ]] || fail "webhook returned $code: ${response%$'\n'*}"
  execution_id=$(jq -r .executionId <<<"${response%$'\n'*}")
  log "Execution $execution_id accepted; waiting for completion"
  sleep 1
  execution=$(wait_for_execution "$execution_id" 60)
  print_nodes "$execution"
  [[ $(jq -r .status <<<"$execution") == COMPLETED ]] || fail "execution did not complete: $(jq -c '{status, errorCode, errorMessage}' <<<"$execution")"
  [[ $(jq -r '.nodes[] | select(.nodeId == "risky") | .branch' <<<"$execution") == "$expect_branch" ]] || fail "unexpected branch"
}

log "High-risk order (amount 250) should alert the fraud team"
run_order '{"orderId":"A-1001","amount":250,"customerEmail":"ada@example.com"}' true
log "Low-risk order (amount 40) should confirm to the customer"
run_order '{"orderId":"A-1002","amount":40,"customerEmail":"bob@example.com"}' false

log "Invalid signature is rejected"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$URL" -H 'Content-Type: application/json' \
  -H "X-Webhook-Timestamp: $(date +%s)" -H 'X-Webhook-Signature: sha256=00' -d '{"orderId":"x","amount":1,"customerEmail":"a@b.co"}')
[[ "$code" == 401 ]] || fail "expected 401, got $code"

log "Mailpit received $(curl -s http://localhost:8025/api/v1/messages | jq .total) message(s)"
log "Smoke test passed"
