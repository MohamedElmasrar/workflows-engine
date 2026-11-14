#!/usr/bin/env bash
# Reproducible failure demonstration against the compose environment:
#   1. worker killed (SIGKILL) while executing a node -> task redelivered or re-dispatched -> valid terminal state
#   2. execution engine killed while a durable DELAY timer is pending -> timer fires after restart
#   3. a NodeExecutionCompleted message is re-published to Kafka -> no duplicate state transition
source "$(dirname "$0")/lib.sh"
cd "$(dirname "$0")/.."

TOKEN=$(token demo demo)

wait_healthy() {
  for _ in $(seq 1 90); do
    [[ $(docker compose ps --format '{{.Health}}' "$1") == healthy ]] && return 0
    sleep 2
  done
  fail "$1 did not become healthy"
}

create_and_publish() { # <name> <definition-json>
  local wf id
  wf=$(api POST /api/v1/workflows "$(jq -n --arg n "$1" --argjson d "$2" '{name: $n, definition: $d}')")
  id=$(jq -r .id <<<"$wf")
  api POST "/api/v1/workflows/$id/publish" > /dev/null
  echo "$id"
}

start_run() { # <workflow-id>; retries while the published version propagates to the engine
  for _ in $(seq 1 20); do
    if out=$(curl -fsS -X POST "$GATEWAY/api/v1/workflows/$1/executions" -H "Authorization: Bearer $TOKEN" \
        -H 'Content-Type: application/json' -d '{"input":{"source":"failure-demo"}}' 2>/dev/null); then
      jq -r .id <<<"$out"; return 0
    fi
    sleep 1
  done
  fail "could not start execution"
}

node_status() { api GET "/api/v1/executions/$1" | jq -r --arg n "$2" '.nodes[] | select(.nodeId == $n) | .status'; }

# ---------------------------------------------------------------- 1. worker crash
log "Scenario 1: worker crash while a node is running"
DEF='{"schemaVersion":1,"nodes":[
 {"id":"start","type":"MANUAL_TRIGGER","name":"Start","config":{}},
 {"id":"slow","type":"HTTP_REQUEST","name":"Slow call (8 s)","timeoutSeconds":30,"config":{"method":"POST","url":"http://mock-api:8080/slow","body":{"run":"{{ execution.id }}"}}},
 {"id":"done","type":"LOG","name":"Done","config":{"message":"slow call returned {{ nodes.slow.output.status }}"}}],
 "edges":[{"id":"e1","source":"start","target":"slow"},{"id":"e2","source":"slow","target":"done"}]}'
WF=$(create_and_publish "Failure demo: worker crash" "$DEF")
EX=$(start_run "$WF")
log "Execution $EX started; waiting for node 'slow' to be RUNNING"
for _ in $(seq 1 30); do [[ $(node_status "$EX" slow) == RUNNING ]] && break; sleep 0.5; done
log "Killing worker-service (SIGKILL) while the HTTP call is in flight"
docker compose kill -s SIGKILL worker-service > /dev/null
sleep 2
log "Persisted state while no worker is alive:"
print_nodes "$(api GET "/api/v1/executions/$EX")"
log "Restarting worker-service"
docker compose start worker-service > /dev/null
wait_healthy worker-service
RESULT=$(wait_for_execution "$EX" 180)
print_nodes "$RESULT"
log "Attempts of 'slow':"
jq -r '.nodes[] | select(.nodeId=="slow") | .attempts[] | "  #\(.number) \(.status) worker=\(.workerId // "-") error=\(.errorCode // "-")"' <<<"$RESULT"
[[ $(jq -r .status <<<"$RESULT") == COMPLETED ]] || fail "execution did not complete after worker recovery"
log "Scenario 1 OK: execution reached COMPLETED after the worker crash"

# ---------------------------------------------------------------- 2. engine crash during a durable timer
log "Scenario 2: execution engine killed while a DELAY timer is pending"
DEF='{"schemaVersion":1,"nodes":[
 {"id":"start","type":"MANUAL_TRIGGER","name":"Start","config":{}},
 {"id":"wait","type":"DELAY","name":"Wait 20 s","config":{"durationSeconds":20}},
 {"id":"after","type":"LOG","name":"After delay","config":{"message":"resumed"}}],
 "edges":[{"id":"e1","source":"start","target":"wait"},{"id":"e2","source":"wait","target":"after"}]}'
WF=$(create_and_publish "Failure demo: engine restart" "$DEF")
EX=$(start_run "$WF")
log "Execution $EX: delay node is $(node_status "$EX" wait); killing execution-engine"
docker compose kill -s SIGKILL execution-engine > /dev/null
sleep 5
docker compose start execution-engine > /dev/null
wait_healthy execution-engine
RESULT=$(wait_for_execution "$EX" 120)
print_nodes "$RESULT"
[[ $(jq -r .status <<<"$RESULT") == COMPLETED ]] || fail "execution did not complete after engine restart"
log "Scenario 2 OK: the persisted timer fired after the restart"

# ---------------------------------------------------------------- 3. duplicate message injection
log "Scenario 3: re-publishing a NodeExecutionCompleted event for execution $EX"
BEFORE=$(api GET "/api/v1/executions/$EX/events" | jq length)
DUP_BEFORE=$(curl -s localhost:8082/actuator/prometheus | awk '/^workflow_messages_duplicate_total/ {s+=$2} END {print s+0}')
RECORD=$(docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic execution.node-results.v1 --from-beginning --timeout-ms 15000 --property print.key=true 2>/dev/null \
  | grep "$EX" | grep NodeExecutionCompleted | tail -1)
[[ -n "$RECORD" ]] || fail "could not find the original result record"
printf '%s\n' "$RECORD" | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic execution.node-results.v1 --property parse.key=true 2>/dev/null
sleep 5
AFTER=$(api GET "/api/v1/executions/$EX/events" | jq length)
DUP_AFTER=$(curl -s localhost:8082/actuator/prometheus | awk '/^workflow_messages_duplicate_total/ {s+=$2} END {print s+0}')
log "History entries before=$BEFORE after=$AFTER; duplicate-message counter $DUP_BEFORE -> $DUP_AFTER"
[[ "$BEFORE" == "$AFTER" ]] || fail "duplicate message changed the execution history"
log "Scenario 3 OK: the duplicate was recognised by event id and ignored"
log "All failure scenarios passed"
