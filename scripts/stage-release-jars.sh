#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
ROOT=$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)
SOURCE_ROOT=${1:-$ROOT/.runtime/sources}
MAVEN_REPO=${2:-$ROOT/.runtime/m2/repository}
LOCK=${SERVICES_LOCK:-$ROOT/services.lock}
DOCKER_DIR=${DOCKER_OUTPUT_ROOT:-$ROOT/docker}
STAGING=
LINK_TMP=
GENERATIONS_CREATED=

fail() {
  printf 'jar-stage: %s\n' "$1" >&2
  exit 1
}

cleanup() {
  local status=$?
  [[ -z $LINK_TMP || ! -L $LINK_TMP ]] || rm -f "$LINK_TMP"
  [[ -z $STAGING || ! -d $STAGING ]] || rm -rf -- "$STAGING"
  [[ -z $GENERATIONS_CREATED || ! -d $GENERATIONS_CREATED ]] \
    || rmdir -- "$GENERATIONS_CREATED" 2>/dev/null \
    || true
  exit "$status"
}
trap cleanup EXIT INT TERM

[[ -d $SOURCE_ROOT && ! -L $SOURCE_ROOT ]] || fail "source root is not materialized"
SOURCE_ROOT=$(cd "$SOURCE_ROOT" && pwd -P)
[[ -d $MAVEN_REPO && ! -L $MAVEN_REPO ]] || fail "isolated Maven repository is missing"
MAVEN_REPO=$(cd "$MAVEN_REPO" && pwd -P)
mkdir -p "$(dirname "$DOCKER_DIR")"
DOCKER_DIR=$(cd "$(dirname "$DOCKER_DIR")" && pwd -P)/$(basename "$DOCKER_DIR")
GENERATIONS=$DOCKER_DIR/.jars
JARS=$DOCKER_DIR/jars
if [[ -n ${MAVEN_RUNNER:-} ]]; then
  [[ -x $MAVEN_RUNNER ]] || fail "Maven runner is not executable"
  MAVEN_RUNNER=$(cd "$(dirname "$MAVEN_RUNNER")" && pwd -P)/$(basename "$MAVEN_RUNNER")
fi
JAVA_BIN=${JAVA_HOME:+$JAVA_HOME/bin/}java
JAVAC_BIN=${JAVA_HOME:+$JAVA_HOME/bin/}javac
JAVA_MAJOR=$($JAVA_BIN -version 2>&1 | awk -F'[."]' '/version/ {print $2; exit}')
JAVAC_MAJOR=$($JAVAC_BIN -version 2>&1 | awk '{print $2; exit}' | cut -d. -f1)
[[ $JAVA_MAJOR == 17 && $JAVAC_MAJOR == 17 ]] || fail "Java 17 JDK is required"
[[ ! -L $GENERATIONS ]] || fail "generation root must not be a symlink"
if [[ ! -d $GENERATIONS ]]; then
  mkdir -p "$GENERATIONS"
  GENERATIONS_CREATED=$GENERATIONS
fi
STAGING=$(mktemp -d "$GENERATIONS/generation.XXXXXX")
ENTRIES=$(awk -F'|' '
  NF && $1 !~ /^#/ { if (NF != 4) exit 2; print; count++ }
  END { if (count != 8) exit 2 }
' "$LOCK") || fail "invalid services lock"

count=0
while IFS='|' read -r logical branch commit artifact; do
  [[ $logical != shared ]] || continue
  source=$SOURCE_ROOT/$logical
  [[ -f $source/.git && $(git -C "$source" rev-parse HEAD) == "$commit" ]] \
    || fail "$logical source mismatch"
  ! git -C "$source" symbolic-ref -q HEAD >/dev/null || fail "$logical source is attached"
  [[ -z $(git -C "$source" status --porcelain) ]] || fail "$logical source is dirty"
  runner=${MAVEN_RUNNER:-$source/mvnw}
  [[ -x $runner ]] || fail "$logical Maven runner is not executable"
  (
    cd "$source"
    "$runner" -B "-Dmaven.repo.local=$MAVEN_REPO" -DskipTests clean package
  )
  built=$source/target/$artifact
  [[ -f $built && ! -L $built ]] || fail "$logical exact release JAR is missing"
  jar tf "$built" | grep -q '^BOOT-INF/classes/' || fail "$logical JAR is not executable"
  cp "$built" "$STAGING/$logical.jar"
  hash=$(shasum -a 256 "$STAGING/$logical.jar" | awk '{print $1}')
  printf '%s  %s.jar\n' "$hash" "$logical" >>"$STAGING/SHA256SUMS"
  count=$((count + 1))
done <<<"$ENTRIES"
[[ $count == 7 && $(wc -l <"$STAGING/SHA256SUMS" | tr -d ' ') == 7 ]] \
  || fail "release JAR set is incomplete"

old_target=
if [[ -e $JARS && ! -L $JARS ]]; then
  fail "docker/jars must be absent or a managed symlink"
elif [[ -L $JARS ]]; then
  old_target=$(readlink "$JARS")
  [[ $old_target =~ ^\.jars/generation\.[A-Za-z0-9]+$ ]] \
    || fail "docker/jars points outside managed generations"
  [[ -d $DOCKER_DIR/$old_target && ! -L $DOCKER_DIR/$old_target ]] \
    || fail "active generation is invalid"
fi

generation=$(basename "$STAGING")
LINK_TMP=$GENERATIONS/link.$$
ln -s ".jars/$generation" "$LINK_TMP"
case $(uname -s) in
  Darwin) mv -hf "$LINK_TMP" "$JARS" ;;
  Linux) mv -Tf "$LINK_TMP" "$JARS" ;;
  *) fail "atomic publication is unsupported" ;;
esac
LINK_TMP=
STAGING=
[[ -z $old_target ]] || rm -rf -- "$DOCKER_DIR/$old_target"
trap - EXIT INT TERM
