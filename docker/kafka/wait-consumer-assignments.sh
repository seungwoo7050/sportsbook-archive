#!/bin/sh
set -eu

BOOTSTRAP=${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}
CONSUMER_GROUPS=${KAFKA_CONSUMER_GROUPS:-/opt/kafka/bin/kafka-consumer-groups.sh}
TIMEOUT=${ASSIGNMENT_TIMEOUT_SECONDS:-180}
POLL=${ASSIGNMENT_POLL_SECONDS:-2}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT INT TERM

cat >"$WORK/expected" <<'EOF'
bet.resolution.revised.v1:0
bet.resolution.revised.v1:1
bet.resolution.revised.v1:2
bet.settled.v1:0
bet.settled.v1:1
bet.settled.v1:2
bet.voided.v1:0
bet.voided.v1:1
bet.voided.v1:2
EOF

group_ready() {
  group=$1
  "$CONSUMER_GROUPS" --bootstrap-server "$BOOTSTRAP" --describe --group "$group" \
    >"$WORK/$group.out" 2>/dev/null || return 1
  awk -v group="$group" '
    $1 == group && $7 != "-" { print $2 ":" $3 }
  ' "$WORK/$group.out" | sort -u >"$WORK/$group.actual"
  cmp -s "$WORK/expected" "$WORK/$group.actual"
}

deadline=$(( $(date +%s) + TIMEOUT ))
while [ "$(date +%s)" -lt "$deadline" ]; do
  if group_ready gateway-bets && group_ready betting-resolution; then
    printf 'consumer-assignment: gateway-bets=9 betting-resolution=9\n'
    exit 0
  fi
  sleep "$POLL"
done

printf 'consumer-assignment: timed out waiting for exact active assignments\n' >&2
exit 1
