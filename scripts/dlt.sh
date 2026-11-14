#!/usr/bin/env bash
# Inspect or replay dead-letter topics in the compose environment.
#   scripts/dlt.sh list                       # message counts per DLT
#   scripts/dlt.sh show <topic> [max]         # print DLT records with the failure headers
#   scripts/dlt.sh replay <topic> [max]       # re-publish DLT records to the original topic (consumer group dlt-replay)
# <topic> is the original topic, e.g. execution.node-results.v1. Consumers are idempotent, so replaying a record
# that was in fact processed is harmless; fix the root cause before replaying.
set -euo pipefail
cd "$(dirname "$0")/.."
K="docker compose exec -T kafka /opt/kafka/bin"
BS="--bootstrap-server localhost:9092"

case "${1:-list}" in
  list)
    for t in $($K/kafka-topics.sh $BS --list | grep '\.dlt$'); do
      n=$($K/kafka-get-offsets.sh $BS --topic "$t" | awk -F: '{s+=$3} END {print s+0}')
      printf '%-40s %s\n' "$t" "$n"
    done ;;
  show)
    $K/kafka-console-consumer.sh $BS --topic "$2.dlt" --from-beginning --max-messages "${3:-10}" --timeout-ms 10000 \
      --property print.key=true --property print.headers=true 2>/dev/null || true ;;
  replay)
    records=$($K/kafka-console-consumer.sh $BS --topic "$2.dlt" --group dlt-replay --from-beginning --max-messages "${3:-100}" \
      --timeout-ms 10000 --property print.key=true --property key.separator=$'\t' 2>/dev/null || true)
    [[ -z "$records" ]] && { echo "nothing to replay"; exit 0; }
    printf '%s\n' "$records" | $K/kafka-console-producer.sh $BS --topic "$2" --property parse.key=true \
      --property key.separator=$'\t'
    echo "replayed $(printf '%s\n' "$records" | wc -l) record(s) to $2" ;;
  *) echo "usage: $0 list|show <topic> [max]|replay <topic> [max]" >&2; exit 2 ;;
esac
