#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
ROOT=$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)
SOURCE_ROOT=${1:-$ROOT/.runtime/sources}
MAVEN_REPO=${2:-$ROOT/.runtime/m2/repository}
OUTPUT=${3:-$ROOT/.runtime/fixtures}
PENDING=

fail() {
  printf 'fixture-stage: %s\n' "$1" >&2
  exit 1
}

cleanup() {
  local status=$?
  [[ -z $PENDING || ! -f $PENDING ]] || rm -f "$PENDING"
  exit "$status"
}
trap cleanup EXIT INT TERM

[[ -d $SOURCE_ROOT && ! -L $SOURCE_ROOT ]] || fail "source root is not materialized"
SOURCE_ROOT=$(cd "$SOURCE_ROOT" && pwd -P)
[[ -d $MAVEN_REPO && ! -L $MAVEN_REPO ]] || fail "isolated Maven repository is missing"
MAVEN_REPO=$(cd "$MAVEN_REPO" && pwd -P)
[[ -d $OUTPUT && ! -L $OUTPUT ]] || fail "output directory is not owned"
OUTPUT=$(cd "$OUTPUT" && pwd -P)
SHARED=$SOURCE_ROOT/shared
[[ -f $SHARED/.git && $(git -C "$SHARED" rev-parse HEAD) == \
  f9de6bc1e533761ab4bb1454d8d4ab8175cdf001 ]] || fail "shared source mismatch"
! git -C "$SHARED" symbolic-ref -q HEAD >/dev/null || fail "shared source is attached"
[[ -z $(git -C "$SHARED" status --porcelain) ]] || fail "shared source is dirty"

JAVA_BIN=${JAVA_HOME:+$JAVA_HOME/bin/}java
JAVAC_BIN=${JAVA_HOME:+$JAVA_HOME/bin/}javac
JAVA_MAJOR=$($JAVA_BIN -version 2>&1 | awk -F'[."]' '/version/ {print $2; exit}')
JAVAC_MAJOR=$($JAVAC_BIN -version 2>&1 | awk '{print $2; exit}' | cut -d. -f1)
[[ $JAVA_MAJOR == 17 && $JAVAC_MAJOR == 17 ]] || fail "Java 17 JDK is required"

RUNNER=${MAVEN_RUNNER:-$SHARED/mvnw}
[[ -x $RUNNER ]] || fail "Maven runner is not executable"
RUNNER=$(cd "$(dirname "$RUNNER")" && pwd -P)/$(basename "$RUNNER")
"$RUNNER" -B -f "$ROOT/fixtures/avro-publisher/pom.xml" \
  "-Dmaven.repo.local=$MAVEN_REPO" clean package

BUILT=$ROOT/fixtures/avro-publisher/target/avro-fixture-publisher.jar
[[ -f $BUILT && ! -L $BUILT ]] || fail "shaded publisher is missing"
PENDING=$(mktemp "$OUTPUT/.fixture.XXXXXX")
cp "$BUILT" "$PENDING"
MANIFEST=$(unzip -p "$PENDING" META-INF/MANIFEST.MF) || fail "manifest is missing"
[[ $MANIFEST == *"Main-Class: com.sportsbook.orchestration.fixture.FixturePublisher"* ]] \
  || fail "publisher Main-Class mismatch"
INVENTORY=$(jar tf "$PENDING") || fail "publisher JAR is unreadable"
for entry in \
  com/sportsbook/orchestration/fixture/FixturePublisher.class \
  com/sportsbook/protocol/event/EventLifecycle.class \
  org/apache/kafka/clients/producer/KafkaProducer.class; do
  grep -qx "$entry" <<<"$INVENTORY" || fail "publisher dependency is missing: $entry"
done
PROPERTIES=$(unzip -p "$PENDING" META-INF/maven/com.sportsbook/shared-protocol/pom.properties)
[[ $PROPERTIES == *"version=1.0.0"* ]] || fail "shared protocol version mismatch"
MAJOR=$(javap -verbose -classpath "$PENDING" \
  com.sportsbook.orchestration.fixture.FixturePublisher | awk '/major version/ {print $3; exit}')
[[ $MAJOR == 61 ]] || fail "publisher bytecode is not Java 17"

mv -f "$PENDING" "$OUTPUT/avro-fixture-publisher.jar"
PENDING=
shasum -a 256 "$OUTPUT/avro-fixture-publisher.jar"
trap - EXIT INT TERM
