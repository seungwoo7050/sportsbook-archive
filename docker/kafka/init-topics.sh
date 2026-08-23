#!/bin/sh
set -eu

BOOTSTRAP=${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}
MANIFEST=${TOPIC_MANIFEST:-/opt/sportsbook/topics.manifest}
BIN=/opt/kafka/bin
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT INT TERM

fail() {
  printf 'topic-init: %s\n' "$1" >&2
  exit 1
}

awk -F'|' '
  NF && $1 !~ /^#/ {
    if (NF != 4 || $2 !~ /^[0-9]+$/ || $3 !~ /^[0-9]+$/) exit 2
    if (seen[$1]++) exit 3
    print
  }
' "$MANIFEST" >"$WORK/manifest" || fail "invalid manifest"
cut -d'|' -f1 "$WORK/manifest" >"$WORK/names"
"$BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" --list >"$WORK/existing"

while IFS= read -r topic; do
  case "$topic" in
    ""|__*) ;;
    *) grep -Fqx "$topic" "$WORK/names" || fail "undeclared topic exists: $topic" ;;
  esac
done <"$WORK/existing"

: >"$WORK/missing"
while IFS='|' read -r topic partitions replication retention; do
  if ! grep -Fqx "$topic" "$WORK/existing"; then
    printf '%s|%s|%s|%s\n' "$topic" "$partitions" "$replication" "$retention" \
      >>"$WORK/missing"
    continue
  fi

  "$BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" --describe --topic "$topic" \
    >"$WORK/describe"
  actual_partitions=$(sed -n 's/.*PartitionCount: \([0-9][0-9]*\).*/\1/p' "$WORK/describe" | head -n 1)
  actual_replication=$(sed -n 's/.*ReplicationFactor: \([0-9][0-9]*\).*/\1/p' "$WORK/describe" | head -n 1)
  [ "$actual_partitions" = "$partitions" ] || fail "$topic partition mismatch"
  [ "$actual_replication" = "$replication" ] || fail "$topic replication mismatch"

  if [ "$retention" != "-" ]; then
    "$BIN/kafka-configs.sh" --bootstrap-server "$BOOTSTRAP" --entity-type topics \
      --entity-name "$topic" --describe >"$WORK/config"
    actual_retention=$(sed -n 's/.*retention.ms=\([0-9][0-9]*\).*/\1/p' "$WORK/config" | head -n 1)
    [ -n "$actual_retention" ] || fail "$topic retention is not explicit"
    [ "$actual_retention" -ge "$retention" ] || fail "$topic retention is too short"
  fi
done <"$WORK/manifest"

while IFS='|' read -r topic partitions replication retention; do
  set -- --bootstrap-server "$BOOTSTRAP" --create --topic "$topic" \
    --partitions "$partitions" --replication-factor "$replication"
  if [ "$retention" != "-" ]; then
    set -- "$@" --config "retention.ms=$retention"
  fi
  "$BIN/kafka-topics.sh" "$@"
done <"$WORK/missing"
