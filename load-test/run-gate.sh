#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
compose_file=${repo_root}/load-test/docker-compose.yml
compose_project=risk-release-gate-$$
output_dir=$(mktemp -d "${TMPDIR:-/tmp}/risk-gate.XXXXXX")
service_pid=
export RISK_GATE_OUTPUT=${output_dir}
export RISK_BASE_URL=${RISK_BASE_URL:-http://localhost:18083}

cleanup() {
  status=$?
  if [[ ${status} -ne 0 && -f ${output_dir}/service.log ]]; then
    tail -100 "${output_dir}/service.log" >&2
  fi
  if [[ -n ${service_pid} ]]; then
    kill "${service_pid}" 2>/dev/null || true
    wait "${service_pid}" 2>/dev/null || true
  fi
  docker compose -p "${compose_project}" -f "${compose_file}" down -v --remove-orphans \
    > /dev/null 2>&1 || true
  rm -rf "${output_dir}"
  exit "${status}"
}
trap cleanup EXIT INT TERM

for command in docker curl jq java; do
  command -v "${command}" > /dev/null || { echo "missing command: ${command}" >&2; exit 1; }
done
docker compose version > /dev/null
for name in INTERNAL_BETTING_SERVICE_API_KEY INTERNAL_ADMIN_API_KEY INTERNAL_PLATFORM_API_KEY; do
  value=${!name:-}
  [[ ${#value} -ge 32 ]] || { echo "${name} must contain at least 32 characters" >&2; exit 1; }
done
[[ ${INTERNAL_BETTING_SERVICE_API_KEY} != "${INTERNAL_ADMIN_API_KEY}" \
  && ${INTERNAL_BETTING_SERVICE_API_KEY} != "${INTERNAL_PLATFORM_API_KEY}" \
  && ${INTERNAL_ADMIN_API_KEY} != "${INTERNAL_PLATFORM_API_KEY}" ]] \
  || { echo "risk gate credentials must be distinct" >&2; exit 1; }

cd "${repo_root}"
./mvnw -B -o -Dmaven.repo.local="${RISK_MAVEN_REPO:?}" clean verify
jar_path=$(find target -maxdepth 1 -name 'risk-service-*.jar' ! -name '*.original' -print -quit)
[[ -n ${jar_path} ]] || { echo "risk service jar is missing" >&2; exit 1; }

docker compose -p "${compose_project}" -f "${compose_file}" up -d --wait --wait-timeout 180
SERVER_PORT=18083 REDIS_HOST=localhost REDIS_PORT=16379 KAFKA_BOOTSTRAP=localhost:19092 \
  java -jar "${jar_path}" > "${output_dir}/service.log" 2>&1 &
service_pid=$!

ready=false
for attempt in $(seq 1 120); do
  if curl -fsS "${RISK_BASE_URL}/actuator/health/readiness" \
    -o "${output_dir}/readiness.json"; then
    ready=true
    break
  fi
  kill -0 "${service_pid}" 2>/dev/null || { echo "risk service exited" >&2; exit 1; }
  sleep 1
done
[[ ${ready} == true ]] || { echo "risk service readiness timed out" >&2; exit 1; }
jq -e '.status == "UP"' "${output_dir}/readiness.json" > /dev/null

bash load-test/concurrent-admission.sh
bash load-test/lifecycle-replay.sh
curl -fsS "${RISK_BASE_URL}/actuator/prometheus" -o "${output_dir}/metrics.txt"

assert_sample() {
  local metric=$1 expected=$2 first_label=$3 second_label=${4:-}
  awk -v metric="${metric}" -v expected="${expected}" -v first="${first_label}" \
    -v second="${second_label}" '$1 ~ ("^" metric "\\{") && index($1, first) &&
      (second == "" || index($1, second)) { count++; value=$2 }
      END { exit !(count == 1 && value + 0 == expected + 0) }' "${output_dir}/metrics.txt"
}
assert_sample risk_reservation_requests_total 4 'result="created"'
assert_sample risk_reservation_requests_total 99 'result="replayed"'
assert_sample risk_reservation_requests_total 1 'result="rejected"'
assert_sample risk_reservation_transitions_total 1 'operation="commit"' 'result="applied"'
assert_sample risk_reservation_transitions_total 1 'operation="commit"' 'result="replayed"'
assert_sample risk_reservation_transitions_total 1 'operation="commit"' 'result="tombstoned"'
assert_sample risk_reservation_transitions_total 1 'operation="release"' 'result="applied"'
assert_sample risk_reservation_transitions_total 1 'operation="release"' 'result="replayed"'
assert_sample risk_reservation_transitions_total 1 'operation="release"' 'result="conflict"'
echo "risk correctness gate passed"
