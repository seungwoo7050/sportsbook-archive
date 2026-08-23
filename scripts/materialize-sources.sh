#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
ROOT=$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)
LOCK=${SERVICES_LOCK:-$ROOT/services.lock}
TARGET=${1:-$ROOT/.runtime/sources}
MODE=${2:-materialize}
CREATED=()

fail() {
  printf 'materialize: %s\n' "$1" >&2
  exit 1
}

absolute_target() {
  local parent base
  parent=$(dirname "$TARGET")
  base=$(basename "$TARGET")
  mkdir -p "$parent"
  parent=$(cd "$parent" && pwd -P)
  printf '%s/%s\n' "$parent" "$base"
}

validate_target() {
  TARGET=$(absolute_target)
  [[ $TARGET != / && $TARGET != "$ROOT" && $TARGET != "$(dirname "$ROOT")" ]] \
    || fail "refusing broad target: $TARGET"
  [[ ! -L $TARGET ]] || fail "target must not be a symlink"
}

locked_entries() {
  awk -F'|' '
    NF && $1 !~ /^#/ { if (NF != 4) exit 2; print; count++ }
    END { if (count != 8) exit 2 }
  ' "$LOCK"
}

cleanup_created() {
  local path
  for ((index=${#CREATED[@]} - 1; index >= 0; index--)); do
    path=${CREATED[$index]}
    git -C "$ROOT" worktree remove --force "$path" >/dev/null 2>&1 || true
  done
  [[ ! -d $TARGET ]] || rmdir "$TARGET" >/dev/null 2>&1 || true
}

materialize() {
  local logical branch commit artifact path
  [[ ! -e $TARGET ]] || fail "target already exists: $TARGET"
  mkdir "$TARGET"
  trap cleanup_created EXIT ERR INT TERM
  while IFS='|' read -r logical branch commit artifact; do
    git -C "$ROOT" cat-file -e "$commit^{commit}"
    [[ $(git -C "$ROOT" rev-parse "refs/heads/$branch") == "$commit" ]] \
      || fail "$branch no longer matches $commit"
    path=$TARGET/$logical
    git -C "$ROOT" worktree add --quiet --detach "$path" "$commit"
    CREATED+=("$path")
    [[ $(git -C "$path" rev-parse HEAD) == "$commit" ]] || fail "$logical checkout mismatch"
    ! git -C "$path" symbolic-ref -q HEAD >/dev/null || fail "$logical is not detached"
  done <<<"$ENTRIES"
  trap - EXIT ERR INT TERM
}

cleanup() {
  local logical branch commit artifact path
  local -a paths=()
  [[ -d $TARGET && ! -L $TARGET ]] || fail "cleanup target is not a directory"
  while IFS='|' read -r logical branch commit artifact; do
    path=$TARGET/$logical
    [[ -f $path/.git ]] || fail "unmanaged worktree: $path"
    [[ $(git -C "$path" rev-parse HEAD) == "$commit" ]] || fail "changed worktree: $path"
    ! git -C "$path" symbolic-ref -q HEAD >/dev/null || fail "attached worktree: $path"
    [[ -z $(git -C "$path" status --porcelain) ]] || fail "dirty worktree: $path"
    paths+=("$path")
  done <<<"$ENTRIES"
  for path in "${paths[@]}"; do
    git -C "$ROOT" worktree remove --force "$path"
  done
  rmdir "$TARGET"
}

validate_target
ENTRIES=$(locked_entries) || fail "invalid services lock"
case "$MODE" in
  materialize) materialize ;;
  cleanup) cleanup ;;
  *) fail "mode must be materialize or cleanup" ;;
esac
