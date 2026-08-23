#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
ROOT=$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)
SOURCE_ROOT=${1:-$ROOT/.runtime/sources}
MAVEN_REPO=${2:-$ROOT/.runtime/m2/repository}
LOCK=${SERVICES_LOCK:-$ROOT/services.lock}
RUNNER=${MAVEN_RUNNER:-}

fail() {
  printf 'shared-install: %s\n' "$1" >&2
  exit 1
}

IFS='|' read -r logical branch commit artifact < <(
  awk -F'|' '$1 == "shared" { print; found=1 } END { if (!found) exit 2 }' "$LOCK"
)
[[ $logical == shared && $artifact == shared-protocol-1.0.0.jar ]] \
  || fail "invalid shared lock entry"

[[ -d $SOURCE_ROOT && ! -L $SOURCE_ROOT ]] || fail "source root is not materialized"
SOURCE_ROOT=$(cd "$SOURCE_ROOT" && pwd -P)
SOURCE=$SOURCE_ROOT/shared
[[ -f $SOURCE/.git && ! -L $SOURCE ]] || fail "shared source is not a detached worktree"
[[ $(git -C "$SOURCE" rev-parse HEAD) == "$commit" ]] || fail "shared source SHA mismatch"
! git -C "$SOURCE" symbolic-ref -q HEAD >/dev/null || fail "shared source is attached"
[[ -z $(git -C "$SOURCE" status --porcelain) ]] || fail "shared source is dirty"

JAVA_BIN=${JAVA_HOME:+$JAVA_HOME/bin/}java
JAVAC_BIN=${JAVA_HOME:+$JAVA_HOME/bin/}javac
JAVA_MAJOR=$($JAVA_BIN -version 2>&1 | awk -F'[."]' '/version/ {print $2; exit}')
JAVAC_MAJOR=$($JAVAC_BIN -version 2>&1 | awk '{print $2; exit}' | cut -d. -f1)
[[ $JAVA_MAJOR == 17 && $JAVAC_MAJOR == 17 ]] || fail "Java 17 JDK is required"

mkdir -p "$MAVEN_REPO"
MAVEN_REPO=$(cd "$MAVEN_REPO" && pwd -P)
[[ $MAVEN_REPO != / && $MAVEN_REPO != "$ROOT" ]] || fail "unsafe Maven repository"
[[ ! -L $MAVEN_REPO ]] || fail "Maven repository must not be a symlink"

if [[ -n $RUNNER ]]; then
  [[ -x $RUNNER ]] || fail "Maven runner is not executable"
  RUNNER=$(cd "$(dirname "$RUNNER")" && pwd -P)/$(basename "$RUNNER")
else
  RUNNER=$SOURCE/mvnw
fi
[[ -x $RUNNER ]] || fail "Maven runner is not executable"
(
  cd "$SOURCE"
  "$RUNNER" -B "-Dmaven.repo.local=$MAVEN_REPO" -DskipTests clean install
)

SOURCE_JAR=$SOURCE/target/$artifact
INSTALLED=$MAVEN_REPO/com/sportsbook/shared-protocol/1.0.0/$artifact
INSTALLED_POM=$MAVEN_REPO/com/sportsbook/shared-protocol/1.0.0/shared-protocol-1.0.0.pom
[[ -f $SOURCE_JAR && -f $INSTALLED && -f $INSTALLED_POM ]] \
  || fail "shared 1.0.0 artifacts are incomplete"
cmp -s "$SOURCE_JAR" "$INSTALLED" || fail "installed JAR differs from the build output"
jar tf "$INSTALLED" | grep -qx 'com/sportsbook/protocol/value/Money.class' \
  || fail "installed JAR is not shared-protocol 1.0.0"
grep -q '<version>1.0.0</version>' "$INSTALLED_POM" || fail "installed POM version mismatch"
