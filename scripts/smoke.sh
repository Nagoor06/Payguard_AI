#!/usr/bin/env bash
set -euo pipefail
: "${APP_API_KEY:?Export APP_API_KEY before running}"
: "${REVIEWER_API_KEY:?Export REVIEWER_API_KEY before running}"
HOST="${PAYGUARD_URL:-http://localhost:8080}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
KEY="smoke-$(date +%s)-$$"
DATA='{"paymentState":"FAILED","gatewayCode":"TIMEOUT","retryCount":0,"question":"Synthetic test gateway timeout"}'
http_post() { curl -sS -o "$TMP/response" -w '%{http_code}' \
  -H "X-API-Key: $APP_API_KEY" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $1" -d "$2" "$HOST/api/v1/triage"; }
first="$(http_post "$KEY" "$DATA")"
[[ "$first" == 201 ]] || { echo "FAIL new incident: expected 201, got $first"; cat "$TMP/response"; exit 1; }
case_id="$(python3 -c 'import sys,json; print(json.load(sys.stdin)["caseId"])' < "$TMP/response")"
again="$(http_post "$KEY" "$DATA")"
[[ "$again" == 200 ]] || { echo "FAIL replay: expected 200, got $again"; exit 1; }
replayed_id="$(python3 -c 'import sys,json; print(json.load(sys.stdin)["caseId"])' < "$TMP/response")"
[[ "$case_id" == "$replayed_id" ]] || { echo "FAIL replay changed case ID"; exit 1; }
changed="$(http_post "$KEY" '{"paymentState":"FAILED","gatewayCode":"TIMEOUT","retryCount":1,"question":"Synthetic test gateway timeout"}')"
[[ "$changed" == 409 ]] || { echo "FAIL mismatch: expected 409, got $changed"; exit 1; }
review_key="${KEY}-review"
review_data='{"paymentState":"FAILED","gatewayCode":"FRAUD_REVIEW","retryCount":0,"question":"Synthetic security alert"}'
review_created="$(http_post "$review_key" "$review_data")"
[[ "$review_created" == 201 ]] || { echo "FAIL review incident: expected 201, got $review_created"; exit 1; }
review_id="$(python3 -c 'import sys,json; print(json.load(sys.stdin)["caseId"])' < "$TMP/response")"
review_url="$HOST/api/v1/triage/$review_id/review"
missing_role="$(curl -sS -o "$TMP/review" -w '%{http_code}' -X POST "$review_url" \
  -H "X-API-Key: $APP_API_KEY" -H 'Content-Type: application/json' \
  -d '{"action":"ESCALATED","reviewerRef":"ops-01"}')"
[[ "$missing_role" == 403 ]] || { echo "FAIL reviewer authorization: expected 403, got $missing_role"; exit 1; }
review_action() { curl -sS -o "$TMP/review" -w '%{http_code}' -X POST "$review_url" \
  -H "X-API-Key: $APP_API_KEY" -H "X-Reviewer-Key: $REVIEWER_API_KEY" \
  -H 'Content-Type: application/json' -d '{"action":"ESCALATED","reviewerRef":"ops-01"}'; }
review_first="$(review_action)"
[[ "$review_first" == 200 ]] || { echo "FAIL review: expected 200, got $review_first"; exit 1; }
review_again="$(review_action)"
[[ "$review_again" == 409 ]] || { echo "FAIL duplicate review: expected 409, got $review_again"; exit 1; }
unauthed_audit="$(curl -sS -o "$TMP/events" -w '%{http_code}' -H "X-API-Key: $APP_API_KEY" "$HOST/api/v1/triage/$review_id/events")"
[[ "$unauthed_audit" == 403 ]] || { echo "FAIL audit role check: expected 403, got $unauthed_audit"; exit 1; }
events="$(curl -sS -H "X-API-Key: $APP_API_KEY" -H "X-Reviewer-Key: $REVIEWER_API_KEY" "$HOST/api/v1/triage/$review_id/events")"
printf '%s' "$events" | python3 -c 'import json,sys; e=json.load(sys.stdin); assert [x["eventType"] for x in e]==["CASE_CREATED","REVIEW_ESCALATED"], e'
metrics_unauth="$(curl -sS -o "$TMP/metrics" -w '%{http_code}' "$HOST/actuator/prometheus")"
[[ "$metrics_unauth" == 401 ]] || { echo "FAIL metrics authorization: expected 401, got $metrics_unauth"; exit 1; }
metrics_auth="$(curl -sS -o "$TMP/metrics" -w '%{http_code}' -H "X-API-Key: $APP_API_KEY" "$HOST/actuator/prometheus")"
[[ "$metrics_auth" == 200 ]] || { echo "FAIL metrics scrape: expected 200, got $metrics_auth"; exit 1; }
echo 'PASS: create, replay, key conflict, review authorization, review transition, audit trail and metrics authentication' 
