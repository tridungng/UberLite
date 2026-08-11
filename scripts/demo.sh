#!/usr/bin/env bash
#
# UberLite end-to-end demo (issue 11).
#
# Drives the full happy-path trip lifecycle against a running `docker compose up`, printing every
# request and response. Exits non-zero on the first unexpected status code, so it doubles as a smoke
# test in CI.
#
#   ./scripts/demo.sh
#   BASE_URL=http://localhost:8083 ./scripts/demo.sh   # bypass the gateway, hit trip-service direct
#
# Everything goes through the API gateway by default: if the demo passes, the gateway routes, Eureka
# registrations and every downstream hop are all working.

set -Eeuo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ZIPKIN_URL="${ZIPKIN_URL:-http://localhost:9411}"
EUREKA_URL="${EUREKA_URL:-http://localhost:8761}"
# Generous: on a cold `docker compose up --build` the last service can take a while to register.
READY_TIMEOUT_SECONDS="${READY_TIMEOUT_SECONDS:-300}"
# Kafka-driven analytics are eventually consistent; poll rather than sleep-and-hope.
EVENTUAL_TIMEOUT_SECONDS="${EVENTUAL_TIMEOUT_SECONDS:-60}"

PICKUP_LAT="${PICKUP_LAT:-37.7749}"
PICKUP_LON="${PICKUP_LON:--122.4194}"
DROPOFF_LAT="${DROPOFF_LAT:-37.8044}"
DROPOFF_LON="${DROPOFF_LON:--122.2712}"

RED=$'\033[0;31m'; GREEN=$'\033[0;32m'; YELLOW=$'\033[0;33m'; BLUE=$'\033[0;34m'; BOLD=$'\033[1m'; OFF=$'\033[0m'
if [[ ! -t 1 ]]; then RED=''; GREEN=''; YELLOW=''; BLUE=''; BOLD=''; OFF=''; fi

STEP=0
BODY=''

# ------------------------------------------------------------------------------------------------
# Plumbing
# ------------------------------------------------------------------------------------------------

die() { printf '%s\n' "${RED}FAILED: $*${OFF}" >&2; exit 1; }

require() {
  command -v "$1" >/dev/null 2>&1 \
    || die "'$1' is required but not installed.${2:+ $2}"
}

banner() {
  STEP=$((STEP + 1))
  printf '\n%s\n' "${BOLD}${BLUE}── ${STEP}. $* ${OFF}"
}

# Single-header B3 context created by the *client*, so the whole demo shares one trace id and the
# Zipkin link printed at the end is guaranteed to resolve. `1` is the "sampled" flag.
#
# `head` reads /dev/urandom directly rather than sitting downstream of `tr`: as the *consumer* of a
# pipe it would close the pipe early, and the resulting SIGPIPE kills the script under `pipefail`.
random_hex() { head -c "$1" /dev/urandom | od -An -tx1 | tr -d ' \n'; }
new_trace_id() { random_hex 16; }
new_span_id() { random_hex 8; }

# request <expected-status> <method> <path> [json-body]
# Populates the global $BODY with the response, prints request/response, exits non-zero on mismatch.
request() {
  local expected="$1" method="$2" path="$3" body="${4:-}"
  local url="${BASE_URL}${path}" status
  local -a args=(-sS -o /tmp/uberlite-demo-body -w '%{http_code}' -X "$method" "$url"
                 -H "b3: ${TRACE_ID}-$(new_span_id)-1")

  if [[ -n "$body" ]]; then
    args+=(-H 'Content-Type: application/json' -d "$body")
    printf '%s %s\n%s\n' "${YELLOW}→ ${method}${OFF}" "$url" "$(printf '%s' "$body" | jq -c .)"
  else
    printf '%s %s\n' "${YELLOW}→ ${method}${OFF}" "$url"
  fi

  status="$(curl "${args[@]}" || true)"
  BODY="$(cat /tmp/uberlite-demo-body 2>/dev/null || true)"

  if [[ "$status" != "$expected" ]]; then
    printf '%s\n%s\n' "${RED}← ${status:-<no response>} (expected ${expected})${OFF}" "$BODY" >&2
    die "${method} ${path} returned ${status:-<no response>}, expected ${expected}"
  fi

  printf '%s\n' "${GREEN}← ${status}${OFF}"
  [[ -n "$BODY" ]] && printf '%s\n' "$BODY" | jq . || true
}

json() { printf '%s' "$BODY" | jq -r "$1"; }

expect_state() {
  local actual; actual="$(json '.state')"
  [[ "$actual" == "$1" ]] || die "expected trip state '$1' but the trip is in '$actual'"
  printf '%s\n' "   ${GREEN}state = ${actual}${OFF}"
}

# Polls until <predicate on $BODY> holds, so the script never races Kafka-driven projections.
poll_until() {
  local description="$1" expected_status="$2" method="$3" path="$4" filter="$5"
  local deadline=$(( SECONDS + EVENTUAL_TIMEOUT_SECONDS ))
  printf '%s\n' "${YELLOW}⋯ waiting for ${description}${OFF}"
  while (( SECONDS < deadline )); do
    if curl -sS -o /tmp/uberlite-demo-body -w '%{http_code}' -X "$method" "${BASE_URL}${path}" \
         | grep -qx "$expected_status"; then
      BODY="$(cat /tmp/uberlite-demo-body)"
      if printf '%s' "$BODY" | jq -e "$filter" >/dev/null 2>&1; then
        printf '%s\n' "${GREEN}✓ ${description}${OFF}"
        printf '%s\n' "$BODY" | jq .
        return 0
      fi
    fi
    sleep 2
  done
  die "timed out after ${EVENTUAL_TIMEOUT_SECONDS}s waiting for ${description}"
}

wait_for_stack() {
  local deadline=$(( SECONDS + READY_TIMEOUT_SECONDS ))
  printf '%s\n' "${YELLOW}⋯ waiting for every service to report UP at ${BASE_URL}/health/aggregate${OFF}"
  while (( SECONDS < deadline )); do
    if curl -fsS "${BASE_URL}/health/aggregate" -o /tmp/uberlite-demo-health 2>/dev/null; then
      printf '%s\n\n' "${GREEN}✓ all services UP${OFF}"
      jq -r '.services | to_entries[] | "   \(.value.status)  \(.key)"' /tmp/uberlite-demo-health
      return 0
    fi
    sleep 3
  done
  printf '%s\n' "${RED}Last aggregate health report:${OFF}" >&2
  jq . /tmp/uberlite-demo-health 2>/dev/null >&2 || true
  die "stack was not healthy within ${READY_TIMEOUT_SECONDS}s — check 'docker compose ps'"
}

# ------------------------------------------------------------------------------------------------
# The demo
# ------------------------------------------------------------------------------------------------

require curl
require jq "Install it with 'brew install jq' or 'apt-get install jq'."

TRACE_ID="$(new_trace_id)"
RIDER_ID="rider-$(date +%s)"

printf '%s\n' "${BOLD}UberLite end-to-end demo${OFF}"
printf '   base url : %s\n   rider    : %s\n   trace id : %s\n' "$BASE_URL" "$RIDER_ID" "$TRACE_ID"

banner "Wait for the stack to come up"
wait_for_stack

banner "Put two drivers online near the pickup"
# Matching needs candidates; without this the trip goes straight to UNMATCHED and the demo is
# testing the sad path by accident.
for driver in driver-alpha driver-bravo; do
  request 200 POST "/drivers/${driver}/location" \
    "{\"lat\": ${PICKUP_LAT}, \"lon\": ${PICKUP_LON}}"
  request 200 POST "/drivers/${driver}/status" '{"status": "ONLINE"}'
done

banner "Create the trip — this is the traced price-estimate fan-out"
# One call, six services: trip-service → price-estimation-service → route, time-estimation, surge,
# tax/tolls and discounts. This is the trace the acceptance criteria ask for in Zipkin.
request 201 POST "/trips" "$(jq -nc \
  --arg riderId "$RIDER_ID" \
  --argjson plat "$PICKUP_LAT" --argjson plon "$PICKUP_LON" \
  --argjson dlat "$DROPOFF_LAT" --argjson dlon "$DROPOFF_LON" \
  '{riderId: $riderId, pickup: {lat: $plat, lon: $plon}, dropoff: {lat: $dlat, lon: $dlon}}')"
expect_state PRICED

TRIP_ID="$(json '.id')"
PICKUP_H3="$(json '.pickupH3')"
printf '   trip id  : %s\n   quote    : %s %s\n' \
  "$TRIP_ID" "$(json '.quotedPrice')" "$(json '.quoteCurrency')"

banner "Read the trip back"
request 200 GET "/trips/${TRIP_ID}"
expect_state PRICED

banner "Rider accepts the quote — trip-service calls matching-service"
# ACCEPTED_BY_RIDER auto-advances: the orchestrator asks Matching for a driver and applies the
# answer, so the trip comes back one state further on.
request 200 POST "/trips/${TRIP_ID}/transition" '{"toState": "ACCEPTED_BY_RIDER"}'
expect_state DRIVER_PROPOSED
printf '   driver   : %s\n' "$(json '.driverId')"

banner "Driver accepts, drives, completes and gets paid"
for state in DRIVER_ACCEPTED EN_ROUTE_TO_PICKUP RIDER_PICKED_UP COMPLETED PAID; do
  request 200 POST "/trips/${TRIP_ID}/transition" "{\"toState\": \"${state}\"}"
  expect_state "$state"
done

banner "Completed-trip counts now include this rider"
request 200 GET "/trips/rider-trip-counts"
printf '%s' "$BODY" | jq -e --arg r "$RIDER_ID" 'any(.[]; .riderId == $r and .completedTrips >= 1)' >/dev/null \
  || die "rider ${RIDER_ID} is missing from /trips/rider-trip-counts"

banner "Matching Analytics consumed the trip-events stream (async, via Kafka)"
# PROPOSED then ACCEPTED: the full matching history that Trip Service's own row cannot show,
# because it overwrites driver_id on every retry.
poll_until "a match log for trip ${TRIP_ID}" 200 GET "/match-log/${TRIP_ID}" \
  'any(.[]; .outcome == "PROPOSED") and any(.[]; .outcome == "ACCEPTED")'

banner "Forecasting counted the demand signal for the pickup cell"
request 200 GET "/forecast/${PICKUP_H3}?hourOfDay=$(date -u +%-H)"

banner "Discounts Analytics nightly batch, run on demand"
request 200 POST "/promo-candidates/refresh"
request 200 GET "/promo-candidates"

printf '\n%s\n' "${BOLD}${GREEN}✓ end-to-end demo passed${OFF}"
printf '  trip          %s/trips/%s\n' "$BASE_URL" "$TRIP_ID"
printf '  trace         %s/zipkin/traces/%s\n' "$ZIPKIN_URL" "$TRACE_ID"
printf '  registry      %s\n' "$EUREKA_URL"
printf '  aggregate     %s/health/aggregate\n\n' "$BASE_URL"



