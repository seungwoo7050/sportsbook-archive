#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
head_commit=$(git rev-parse HEAD)
root_commit=$(git rev-list --max-parents=0 HEAD)
head_subject=$(git show -s --format=%s HEAD)
expected_release=""
if [[ $head_subject == "docs(project): document admin API contracts" ]]; then
  expected_release=$(git rev-parse HEAD^)
fi
subject_pattern='^(feat|fix|test|refactor|perf|build|docs|chore|style|ci)\([a-z0-9-]+\): .+'
forbidden_subject='fixup|squash|devlog|changelog|provenance|reconstruct'
forbidden_path='(^|/)(devlog|changelog|provenance|reconstruction)(/|\.|$)|(^|/)load-test/results/'
failed=0

report() {
  printf '%s\n' "$1" >&2
  failed=1
}

only_paths() {
  local pattern=$1 path
  while read -r path; do
    [[ $path =~ $pattern ]] || return 1
  done
}

while read -r commit; do
  subject=$(git show -s --format=%s "$commit")
  body=$(git show -s --format=%b "$commit")
  paths=$(git diff-tree --root --no-commit-id --name-only -r "$commit")
  short=${commit:0:12}
  has_main=false
  has_test=false
  main_files=0

  while read -r path; do
    if [[ $path == src/main/* ]]; then has_main=true; ((main_files += 1)); fi
    [[ $path == src/test/* ]] && has_test=true
  done <<<"$paths"

  [[ $subject =~ $subject_pattern ]] || report "$short has a non-conventional subject: $subject"
  [[ -z $body ]] || report "$short has a non-empty commit body"
  if grep -Eiq "$forbidden_subject" <<<"$subject" || grep -Eiq "$forbidden_path" <<<"$paths"; then
    report "$short contains forbidden reconstruction material"
  fi
  [[ $has_main == false || $has_test == false ]] || report "$short mixes production and test files"
  if [[ $subject == test\(* && $has_main == true ]]; then
    report "$short labels production code as a test"
  elif [[ $subject != test\(* && $has_test == true ]]; then
    report "$short includes tests in a non-test commit"
  fi
  ((main_files <= 2)) || report "$short changes more than two production files"

  churn=$(git show --numstat --format= "$commit" |
    awk '$1 ~ /^[0-9]+$/ && $2 ~ /^[0-9]+$/ {n += $1 + $2} END {print n + 0}')
  if ((churn > 100)); then
    exception=false
    if [[ $subject == "build(maven): establish Java 17 baseline" && $paths == pom.xml ]]; then
      exception=true
    elif [[ $subject =~ ^build\(wrapper\): ]] && only_paths '^(mvnw|mvnw.cmd|\.mvn/wrapper/)' <<<"$paths"; then
      exception=true
    elif [[ $subject =~ ^build\(flyway\): ]] && [[ $(wc -l <<<"$paths") -eq 1 ]] && [[ $paths == *.sql ]]; then
      exception=true
    elif [[ $subject =~ ^feat\(audit\): ]] && [[ $(wc -l <<<"$paths") -eq 1 ]] && [[ $paths == *.avsc ]]; then
      exception=true
    elif [[ $commit == "$head_commit" && $subject == "docs(project): document admin API contracts" && $paths == README.md ]]; then
      exception=true
    fi
    [[ $exception == true ]] || report "$short exceeds the 100-line review gate: $churn lines"
  fi

  if [[ $commit == "$root_commit" ]]; then
    [[ $subject == "docs(project): establish admin API ownership" && $paths == README.md ]] ||
      report "$short is not the required README-only root"
  elif [[ $subject == docs\(* ]]; then
    [[ $commit == "$head_commit" && $subject == "docs(project): document admin API contracts" && $paths == README.md ]] ||
      report "$short is an intermediate documentation commit"
  fi
  if [[ $subject == chore\(release\):* ]]; then
    [[ $commit == "$expected_release" && $subject == "chore(release): release admin API 1.0.0" && $paths == pom.xml ]] ||
      report "$short is not the single penultimate release commit"
  fi

  if grep -Eq '^(src/main/|pom\.xml$|config/|mvnw$|mvnw\.cmd$|\.mvn/wrapper/|\.github/(scripts|workflows)/)' <<<"$paths" \
    && [[ $subject != test\(* && $subject != docs\(* && $subject != "chore(release): release admin API 1.0.0" ]]; then
    next=$(git rev-list --ancestry-path "$commit..HEAD" --reverse | head -1)
    next_subject=$([[ -n $next ]] && git show -s --format=%s "$next" || true)
    [[ $next_subject == test\(* ]] || report "$short is not followed by its test commit"
  fi
done < <(git rev-list --reverse HEAD)
if [[ -n $(git rev-list --min-parents=2 --max-count=1 HEAD) ]]; then
  report "archive history contains a merge commit"
fi
if git ls-tree -r --name-only HEAD | grep -Eiq "$forbidden_path"; then
  report "final tree contains forbidden reconstruction material"
fi
if [[ $head_subject == "docs(project): document admin API contracts" ]]; then
  [[ $(git show -s --format=%s HEAD^) == "chore(release): release admin API 1.0.0" ]] ||
    report "release commit is not immediately before final documentation"
elif git log --format=%s | grep -q '^chore(release):'; then
  report "release commit exists without final documentation"
fi

exit "$failed"
