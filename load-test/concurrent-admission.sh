#!/usr/bin/env bash
set -euo pipefail

base_url=${RISK_BASE_URL:?}
output_dir=${RISK_GATE_OUTPUT:?}
betting_key=${INTERNAL_BETTING_SERVICE_API_KEY:?}
admin_key=${INTERNAL_ADMIN_API_KEY:?}
user_id=60000000-0000-4000-8000-000000000001
selection_id=70000000-0000-4000-8000-000000000001

auth=(-H "X-Internal-Service: betting-service" -H "X-Internal-Api-Key: ${betting_key}")
admin=(-H "X-Internal-Service: admin-api" -H "X-Internal-Api-Key: ${admin_key}")
json=(-H "Content-Type: application/json")

body() {
  local bet_id=$1 amount=$2
  printf '{"userId":"%s","betId":"%s","stake":{"amount":%s,"currency":"KRW"},"selectionIds":["%s"]}' \
    "${user_id}" "${bet_id}" "${amount}" "${selection_id}"
}

reserve() {
  local bet_id=$1 amount=$2 target=$3
  curl -sS "${auth[@]}" "${json[@]}" -X POST -d "$(body "${bet_id}" "${amount}")" \
    "${base_url}/internal/v1/risk/reservations" -o "${target}.json" \
    -w '%{http_code}' > "${target}.status"
}

same_bet=80000000-0000-4000-8000-000000000001
pids=()
for attempt in $(seq 1 100); do
  reserve "${same_bet}" 10 "${output_dir}/same-${attempt}" &
  pids+=("$!")
done
for pid in "${pids[@]}"; do wait "${pid}"; done

test "$(sort -u "${output_dir}"/same-*.status)" = 200
jq -s -e '
  length == 100 and
  all(.approved == true and .reservationState == "RESERVED") and
  (map(select(.replayed == false)) | length) == 1 and
  (map(select(.replayed == true)) | length) == 99 and
  (map(.reservationToken) | unique | length) == 1
' "${output_dir}"/same-*.json > /dev/null

for type in STAKE_DAILY STAKE_WEEKLY STAKE_MONTHLY; do
  curl -fsS "${admin[@]}" "${json[@]}" -X PATCH \
    -d "{\"type\":\"${type}\",\"currency\":\"KRW\",\"value\":100}" \
    "${base_url}/internal/v1/risk/limits/${user_id}" > /dev/null
done

pids=()
for ordinal in 2 3; do
  bet_id=80000000-0000-4000-8000-$(printf '%012d' "${ordinal}")
  reserve "${bet_id}" 60 "${output_dir}/capacity-${ordinal}" &
  pids+=("$!")
done
for pid in "${pids[@]}"; do wait "${pid}"; done

test "$(sort -u "${output_dir}"/capacity-*.status)" = 200
jq -s -e '
  length == 2 and
  (map(select(.approved == true and .replayed == false and
    .reservationState == "RESERVED" and (.reservationToken | type == "string"))) | length) == 1 and
  (map(select(.approved == false and .replayed == false and
    .rejectionReason == "STAKE_DAILY_LIMIT_EXCEEDED" and (has("reservationToken") | not))) | length) == 1
' "${output_dir}"/capacity-*.json > /dev/null
