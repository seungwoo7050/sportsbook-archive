#!/usr/bin/env bash

compose() {
  REDIS_PORT="${REDIS_PORT}" KAFKA_PORT="${KAFKA_PORT}" \
    docker compose --project-name "${COMPOSE_PROJECT_NAME}" -f "${COMPOSE_FILE}" "$@"
}

stop_service() {
  local attempt
  if [[ -z "${SERVICE_PID}" ]]; then
    return
  fi
  if ! kill -0 "${SERVICE_PID}" 2>/dev/null; then
    wait "${SERVICE_PID}" 2>/dev/null || true
    SERVICE_PID=''
    return
  fi

  kill "${SERVICE_PID}" 2>/dev/null || true
  for ((attempt = 0; attempt < 40; attempt++)); do
    if ! kill -0 "${SERVICE_PID}" 2>/dev/null; then
      break
    fi
    sleep 0.25
  done
  if kill -0 "${SERVICE_PID}" 2>/dev/null; then
    kill -KILL "${SERVICE_PID}" 2>/dev/null || true
  fi
  wait "${SERVICE_PID}" 2>/dev/null || true
  SERVICE_PID=''
}

cleanup_runtime() {
  local exit_code=$?
  trap - EXIT INT TERM
  stop_service
  compose down --volumes --remove-orphans >/dev/null 2>&1 || true
  exit "${exit_code}"
}

reset_stack() {
  compose down --volumes --remove-orphans >/dev/null 2>&1 || true
  compose up --detach --wait
}

start_service() {
  local endpoint=$1
  local tick_interval=$2
  ADMIN_API_INTERNAL_KEY=$(openssl rand -hex 32)
  export ADMIN_API_INTERNAL_KEY
  SERVER_PORT="${SERVER_PORT}" \
  REDIS_HOST=localhost \
  REDIS_PORT="${REDIS_PORT}" \
  KAFKA_BOOTSTRAP_SERVERS="localhost:${KAFKA_PORT}" \
  OTEL_SAMPLING_PROBABILITY=0 \
  ODDSFEED_MOCK_RANDOM_SEED="${FIXTURE_RANDOM_SEED}" \
  ODDSFEED_MOCK_TICK_INTERVAL_MS="${tick_interval}" \
    java -jar "${JAR_PATH}" --spring.profiles.active=mock \
      --oddsfeed.mock.minutes-per-second="${FIXTURE_MINUTES_PER_SECOND}" \
      --oddsfeed.mock.scenarios.auto-rotate="${FIXTURE_SCENARIOS_AUTO_ROTATE}" \
      >"${RESULT_ROOT}/${endpoint}-service.log" 2>&1 &
  SERVICE_PID=$!
}
