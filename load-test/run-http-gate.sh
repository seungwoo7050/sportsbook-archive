#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
REPO_ROOT=$(cd "${SCRIPT_DIR}/.." && pwd -P)
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"
RESULT_ROOT=${RESULT_ROOT:?Set RESULT_ROOT to a new directory outside the repository}
SERVER_PORT=${SERVER_PORT:-8085}
REDIS_PORT=${REDIS_PORT:-6392}
KAFKA_PORT=${KAFKA_PORT:-9096}
REQUEST_RATE=${REQUEST_RATE:-1000}
COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME:-odds-feed-http-gate}
MAVEN_REPO_LOCAL=${MAVEN_REPO_LOCAL:-}
BASE_URL="http://localhost:${SERVER_PORT}"
SERVICE_PID=''
MAVEN_REPO_OPTION=()
[[ -z "${MAVEN_REPO_LOCAL}" ]] || MAVEN_REPO_OPTION=(-Dmaven.repo.local="${MAVEN_REPO_LOCAL}")

source "${SCRIPT_DIR}/fixtures/mock.env"
source "${SCRIPT_DIR}/lib/runtime.sh"
source "${SCRIPT_DIR}/fixtures/prepare-mock.sh"
source "${SCRIPT_DIR}/lib/k6-gate.sh"

for command in curl docker java jq k6 openssl sed tr; do
  command -v "${command}" >/dev/null || {
    echo "Missing required command: ${command}" >&2
    exit 2
  }
done

[[ "${RESULT_ROOT}" == /* ]] || {
  echo "RESULT_ROOT must be absolute" >&2
  exit 2
}
RESULT_ROOT=${RESULT_ROOT%/}
[[ -n "${RESULT_ROOT}" ]] || {
  echo "RESULT_ROOT cannot be the filesystem root" >&2
  exit 2
}
RESULT_PARENT=$(cd "$(dirname "${RESULT_ROOT}")" && pwd -P) || {
  echo "RESULT_ROOT parent must already exist" >&2
  exit 2
}
RESULT_ROOT="${RESULT_PARENT}/$(basename "${RESULT_ROOT}")"
[[ ! -e "${RESULT_ROOT}" ]] || {
  echo "RESULT_ROOT must not already exist" >&2
  exit 2
}
case "${RESULT_ROOT}/" in
  "${REPO_ROOT}/"*)
    echo "RESULT_ROOT must be outside the repository" >&2
    exit 2
    ;;
esac
mkdir "${RESULT_ROOT}"
trap cleanup_runtime EXIT INT TERM

cd "${REPO_ROOT}"
ADMIN_API_INTERNAL_KEY=$(openssl rand -hex 32)
export ADMIN_API_INTERNAL_KEY
./mvnw "${MAVEN_REPO_OPTION[@]}" -B -ntp clean verify
FINAL_NAME=$(./mvnw "${MAVEN_REPO_OPTION[@]}" -q -Dstyle.color=never \
  -DforceStdout help:evaluate -Dexpression=project.build.finalName)
JAR_PATH="${REPO_ROOT}/target/${FINAL_NAME}.jar"
[[ -f "${JAR_PATH}" ]] || {
  echo "Executable jar not found: ${JAR_PATH}" >&2
  exit 2
}

reset_stack
start_service events "${FIXTURE_FROZEN_TICK_INTERVAL_MS}"
wait_for_service
wait_for_events
run_endpoint_gate events
stop_service

reset_stack
start_service odds "${FIXTURE_FROZEN_TICK_INTERVAL_MS}"
wait_for_service
wait_for_events
discover_odds_fixture
verify_frozen_odds
run_endpoint_gate odds

echo "HTTP release gate passed; results: ${RESULT_ROOT}"
