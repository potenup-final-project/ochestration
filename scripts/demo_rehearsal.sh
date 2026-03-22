#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"

section() {
  echo
  echo "============================================================"
  echo "$1"
  echo "============================================================"
}

call() {
  local method="$1"
  local path="$2"
  local body="${3:-}"

  echo
  echo "[$method] $path"
  if [[ -n "$body" ]]; then
    curl -sS -X "$method" "$BASE_URL$path" \
      -H 'Content-Type: application/json' \
      -d "$body"
  else
    curl -sS -X "$method" "$BASE_URL$path"
  fi
  echo
}

section "0) Health Check"
call GET "/api/providers"

section "1) Seed 상태 확인"
call GET "/api/providers"
call GET "/api/providers/health"
call GET "/api/providers/capabilities"

section "2) Provider 연결/해제 시연"
call DELETE "/api/providers/INICIS"
call POST "/api/providers/connect" '{"provider":"INICIS","displayName":"이니시스 백업","apiKey":"demo-key"}'
call GET "/api/providers"

section "3) Payment 승인 시나리오 9종"
SCENARIOS=(
  "TOSS_SUCCESS"
  "TOSS_FAIL_THEN_KAKAOPAY_SUCCESS"
  "TOSS_BUSINESS_FAIL"
  "ALL_FAIL"
  "KAKAOPAY_SUCCESS"
  "INICIS_SUCCESS"
  "TOSS_UNHEALTHY_THEN_KAKAOPAY_SUCCESS"
  "INICIS_NOT_CONNECTED"
  "KAKAOPAY_CAPABILITY_EXCLUDED"
)

for s in "${SCENARIOS[@]}"; do
  call POST "/api/payments/approve" "{\"orderId\":\"order-$s\",\"amount\":15000,\"scenario\":\"$s\"}"
done

section "4) Lookup + Cancel 시연 (pay-006 기준)"
call GET "/api/payments/pay-006"
call POST "/api/payments/pay-006/cancel" '{"reason":"고객 요청"}'
call GET "/api/payments/pay-006"

section "완료"
echo "Rehearsal finished for $BASE_URL"
