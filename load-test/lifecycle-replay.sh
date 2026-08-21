#!/usr/bin/env bash
set -euo pipefail

base_url=${RISK_BASE_URL:?}
output_dir=${RISK_GATE_OUTPUT:?}
betting_key=${INTERNAL_BETTING_SERVICE_API_KEY:?}
auth=(-H "X-Internal-Service: betting-service" -H "X-Internal-Api-Key: ${betting_key}")
json=(-H "Content-Type: application/json")
user_id=61000000-0000-4000-8000-000000000001
selection_id=71000000-0000-4000-8000-000000000001

reserve() {
  local bet_id=$1 target=$2
  curl -fsS "${auth[@]}" "${json[@]}" -X POST \
    -d "{\"userId\":\"${user_id}\",\"betId\":\"${bet_id}\",\"stake\":{\"amount\":10,\"currency\":\"KRW\"},\"selectionIds\":[\"${selection_id}\"]}" \
    "${base_url}/internal/v1/risk/reservations" -o "${target}"
  jq -e '.approved == true and .replayed == false and
    .reservationState == "RESERVED" and (.reservationToken | type == "string")' \
    "${target}" > /dev/null
}

committed=81000000-0000-4000-8000-000000000001
reserve "${committed}" "${output_dir}/committed.json"
token=$(jq -er '.reservationToken' "${output_dir}/committed.json")
for attempt in 1 2; do
  status=$(curl -sS "${auth[@]}" -H "X-Risk-Reservation-Token: ${token}" \
    -X PUT -o /dev/null -w '%{http_code}' \
    "${base_url}/internal/v1/risk/reservations/${committed}/commit")
  test "${status}" = 204
done
status=$(curl -sS "${auth[@]}" -X DELETE \
  -o "${output_dir}/committed-release.json" -w '%{http_code}' \
  "${base_url}/internal/v1/risk/reservations/${committed}")
test "${status}" = 409
jq -e '.errorCode == "RISK_RESERVATION_COMMITTED"' \
  "${output_dir}/committed-release.json" > /dev/null

released=81000000-0000-4000-8000-000000000002
reserve "${released}" "${output_dir}/released.json"
released_token=$(jq -er '.reservationToken' "${output_dir}/released.json")
for attempt in 1 2; do
  status=$(curl -sS "${auth[@]}" -X DELETE -o /dev/null -w '%{http_code}' \
    "${base_url}/internal/v1/risk/reservations/${released}")
  test "${status}" = 204
done
status=$(curl -sS "${auth[@]}" -H "X-Risk-Reservation-Token: ${released_token}" \
  -X PUT -o "${output_dir}/released-commit.json" -w '%{http_code}' \
  "${base_url}/internal/v1/risk/reservations/${released}/commit")
test "${status}" = 404
jq -e '.errorCode == "RISK_RESERVATION_NOT_FOUND"' \
  "${output_dir}/released-commit.json" > /dev/null
