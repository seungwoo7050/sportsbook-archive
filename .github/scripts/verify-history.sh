#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
head_commit=$(git rev-parse HEAD)

subject_pattern='^(feat|fix|test|refactor|perf|build|docs|chore|style|ci)\([a-z0-9-]+\): .+'
forbidden_subject_pattern='devlog|changelog|provenance'
forbidden_path_pattern='(^|/)(devlog|changelog|provenance)(/|\.|$)'
failed=0

report() {
  printf '%s\n' "$1" >&2
  failed=1
}

while read -r commit; do
  subject=$(git show -s --format=%s "$commit")
  body=$(git show -s --format=%b "$commit")
  paths=$(git diff-tree --root --no-commit-id --name-only -r "$commit")
  short=${commit:0:12}
  has_main=false
  has_test=false

  while read -r path; do
    [[ $path == src/main/* ]] && has_main=true
    [[ $path == src/test/* ]] && has_test=true
  done <<<"$paths"

  if [[ ! $subject =~ $subject_pattern ]]; then
    report "$short has a non-conventional subject: $subject"
  fi
  if [[ -n $body ]]; then
    report "$short has a non-empty commit body"
  fi
  if [[ $subject =~ $forbidden_subject_pattern ]] \
    || grep -Eiq "$forbidden_path_pattern" <<<"$paths"; then
    report "$short contains forbidden development-log material"
  fi
  if [[ $has_main == true && $has_test == true ]]; then
    report "$short mixes production and test files"
  fi

  churn=$(
    git show --numstat --format= "$commit" \
      | awk '$1 ~ /^[0-9]+$/ && $2 ~ /^[0-9]+$/ { total += $1 + $2 } END { print total + 0 }'
  )
  if ((churn > 100)); then
    exception=false
    if [[ $subject =~ ^build\(wrapper\): ]]; then
      exception=true
      while read -r path; do
        [[ $path == mvnw || $path == mvnw.cmd || $path == .mvn/wrapper/* ]] || exception=false
      done <<<"$paths"
    elif [[ $subject =~ ^build\(flyway\): ]]; then
      exception=true
      while read -r path; do
        [[ $path == src/main/resources/db/migration/* ]] || exception=false
      done <<<"$paths"
    elif [[ $subject == "docs(project): document settlement service" \
      && $commit == "$head_commit" ]]; then
      exception=true
      while read -r path; do
        [[ $path == README.md ]] || exception=false
      done <<<"$paths"
    fi
    if [[ $exception == false ]]; then
      report "$short exceeds the 100-line review gate: $churn lines"
    fi
  fi
done < <(git rev-list --reverse HEAD)

if [[ -n $(git rev-list --min-parents=2 --max-count=1 HEAD) ]]; then
  report "archive history contains a merge commit"
fi

if git ls-tree -r --name-only HEAD | grep -Eiq "$forbidden_path_pattern"; then
  report "final tree contains forbidden development-log material"
fi

exit "$failed"
