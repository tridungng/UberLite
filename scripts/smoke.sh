#!/usr/bin/env bash
#
# UberLite per-service smoke check.
#
# One line per service, so "which one is broken?" is answered in a second rather than by scrolling
# through interleaved logs. Works against the host-published ports, so it needs no `docker exec` and
# also covers services you are running outside Docker from your IDE.
#
#   ./scripts/smoke.sh              # every service
#   ./scripts/smoke.sh trip-service price-estimation-service
#
# Exit code is the number of services that are not UP, so it is usable as a CI gate.
#
# Columns:
#   LIVE   /actuator/health/liveness  - is the JVM serving? This is what the container HEALTHCHECK
#                                       polls; DOWN or unreachable means restart it.
#   READY  /actuator/health/readiness - can it serve traffic, i.e. is its own datastore reachable?
#   HEALTH /actuator/health           - the composite, including peers and the registry. This one is
#                                       *informational*: a DOWN here with LIVE/READY UP means a
#                                       dependency is missing, not that this service is broken.

set -uo pipefail

SERVICES=(
  "discovery-server:8761"
  "api-gateway:8080"
  "trip-service:8083"
  "surge-pricing-service:8084"
  "price-estimation-service:8085"
  "driver-discovery-service:8086"
  "route-service:8087"
  "time-estimation-service:8088"
  "matching-service:8089"
  "tax-tolls-service:8090"
  "discounts-promotions-service:8091"
  "forecasting-service:8092"
  "matching-analytics-service:8093"
  "discounts-analytics-service:8094"
)

HOST="${HOST:-localhost}"
TIMEOUT="${TIMEOUT:-2}"

RED=$'\033[0;31m'; GREEN=$'\033[0;32m'; YELLOW=$'\033[0;33m'; BOLD=$'\033[1m'; OFF=$'\033[0m'
[[ -t 1 ]] || { RED=''; GREEN=''; YELLOW=''; BOLD=''; OFF=''; }

# Prints the top-level Actuator status word, or UNREACHABLE. Deliberately not `curl -f`: a 503 still
# carries a body naming the component that is down, and that body is the whole point.
#
# The body is parsed as JSON rather than grepped: "status" appears once per component, so a text
# match reports whichever one happens to be last and would call a DOWN service UP.
probe() {
  local body
  body=$(curl -s -m "$TIMEOUT" "http://${HOST}:$1$2" 2>/dev/null) || { echo "UNREACHABLE"; return; }
  [[ -z "$body" ]] && { echo "UNREACHABLE"; return; }
  printf '%s' "$body" | python3 -c \
    'import json,sys; print(json.load(sys.stdin).get("status","UNKNOWN"))' 2>/dev/null \
    || echo "UNPARSEABLE"
}

colour() {
  case "$1" in
    UP) printf '%s%-12s%s' "$GREEN" "$1" "$OFF" ;;
    UNREACHABLE) printf '%s%-12s%s' "$RED" "$1" "$OFF" ;;
    *) printf '%s%-12s%s' "$YELLOW" "$1" "$OFF" ;;
  esac
}

wanted=("$@")
failures=0

printf '%s%-32s %-6s %-12s %-12s %-12s%s\n' "$BOLD" "SERVICE" "PORT" "LIVE" "READY" "HEALTH" "$OFF"
for entry in "${SERVICES[@]}"; do
  name=${entry%%:*}
  port=${entry##*:}
  if (( ${#wanted[@]} )) && [[ ! " ${wanted[*]} " == *" $name "* ]]; then
    continue
  fi

  live=$(probe "$port" /actuator/health/liveness)
  ready=$(probe "$port" /actuator/health/readiness)
  composite=$(probe "$port" /actuator/health)

  printf '%-32s %-6s ' "$name" "$port"
  colour "$live"; printf ' '
  colour "$ready"; printf ' '
  colour "$composite"; printf '\n'

  [[ "$live" == "UP" && "$ready" == "UP" ]] || failures=$((failures + 1))
done

if (( failures )); then
  printf '\n%s%d service(s) not ready.%s Next steps:\n' "$RED" "$failures" "$OFF"
  printf '  docker compose ps                       # Up / Exited / restarting?\n'
  printf '  docker compose logs --tail=50 <service> # the actual stack trace\n'
  printf '  curl -s localhost:<port>/actuator/health | jq   # which component is DOWN\n'
else
  printf '\n%sAll checked services are live and ready.%s\n' "$GREEN" "$OFF"
fi

exit "$failures"


