#!/usr/bin/env bash

run_endpoint_gate() {
  local endpoint=$1
  local scenario="${SCRIPT_DIR}/scenarios/${endpoint}.js"
  local output_dir="${RESULT_ROOT}/${endpoint}"
  local run
  mkdir "${output_dir}"

  echo "Warming ${endpoint} for 60 seconds"
  GATE_STAGE=warmup DURATION=60s BASE_URL="${BASE_URL}" REQUEST_RATE="${REQUEST_RATE}" \
    EVENT_ID="${EVENT_ID:-}" MARKET_ID="${MARKET_ID:-}" SELECTION_ID="${SELECTION_ID:-}" \
    EXPECTED_ODDS="${EXPECTED_ODDS:-}" \
    k6 run --quiet --summary-export "${output_dir}/warmup.json" "${scenario}"

  for run in 1 2 3 4 5; do
    echo "Measuring ${endpoint}: ${run}/5"
    GATE_STAGE=measure DURATION=60s BASE_URL="${BASE_URL}" REQUEST_RATE="${REQUEST_RATE}" \
      EVENT_ID="${EVENT_ID:-}" MARKET_ID="${MARKET_ID:-}" SELECTION_ID="${SELECTION_ID:-}" \
      EXPECTED_ODDS="${EXPECTED_ODDS:-}" \
      k6 run --quiet --summary-export "${output_dir}/measure-${run}.json" "${scenario}"
  done
}
