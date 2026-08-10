# Trip Service

The state machine and source of truth for a trip (ARCHITECTURE.md §3), and the orchestrator that
turns downstream answers into state transitions (§4). Every transition is a Postgres row update
**and** a Kafka message on `trip-events`.

## Responsibilities

| Concern | Where |
|---|---|
| Which transitions are legal (incl. the k=3 retry budget) | `domain/TripStateMachine` |
| Persisting a transition + history row + Kafka event | `domain/TripService` (transactional) |
| Calling Price Estimation / Matching / Surge and deciding the next state | `domain/TripOrchestrator` (**not** transactional) |
| Normalising downstream failures | `domain/*Gateway`, `domain/RemoteCalls` |

`TripService` never makes a remote call and `TripOrchestrator` never opens a transaction. That split
is deliberate: holding a database connection across a synchronous HTTP call is how one slow
dependency becomes a connection-pool outage.

## API

| Method | Path | Notes |
|---|---|---|
| `POST` | `/trips` | Creates in `REQUESTED`, calls Price Estimation, auto-transitions to `PRICED`. `201` on success. |
| `POST` | `/trips/{id}/request-quote` | Retry path when pricing was unavailable at creation time. |
| `POST` | `/trips/{id}/request-match` | Retry path when Matching was unavailable. Consumes no retry budget. |
| `POST` | `/trips/{id}/transition` | Applies `toState` and any orchestration it implies. |
| `GET` | `/trips/{id}` | Full trip incl. quote, driver, declined drivers and history. |
| `GET` | `/actuator/health` | |

### Auto-transitions

| You send | What happens | You get back |
|---|---|---|
| — (`POST /trips`) | Price Estimation is called | `PRICED`, or `502` with `tripId` |
| `ACCEPTED_BY_RIDER` | Matching is called | `DRIVER_PROPOSED`, or `UNMATCHED` if the marketplace is empty |
| `DRIVER_DECLINED` | decliner excluded, attempt consumed, Matching re-called | `DRIVER_PROPOSED`, or `UNMATCHED` once k=3 is spent |
| `COMPLETED` / `CANCELLED_BY_RIDER` | surge pending-request decremented | the state you asked for |

`PRICED`, `DRIVER_PROPOSED` and `UNMATCHED` cannot be set directly — they are conclusions drawn from
a downstream answer, and asserting them would create e.g. a `DRIVER_PROPOSED` trip with no driver.
Attempting it returns `409` naming the endpoint that does produce them.

### Status codes

| Code | Meaning |
|---|---|
| `409` | Illegal transition, orchestrator-owned state, or a concurrent update (optimistic lock) |
| `502` | A downstream service could not answer. Body carries `dependency` and `tripId`; the trip is untouched and the call is safe to repeat. |

A Matching `404` (no eligible driver) is **not** a failure — it is a real answer and moves the trip to
`UNMATCHED`, which is terminal. The rider's retry is to request a new trip; there is no delayed-retry
scheduler in the MVP and an immediate retry cannot change the answer. A Matching `502` is different:
it leaves the trip where it is and consumes **no** attempt, so an outage can never park a trip in
`UNMATCHED`. Recover it with `POST /trips/{id}/request-match`.

## Declined drivers

The declined-driver list is durable state on the trip row, and it is **sent to Matching** as
`excludedDriverIds` on every retry. That is required rather than an optimisation: Matching is
greedy-nearest and deterministic, so a retry without exclusions returns the same driver who just
declined, every time, and the decline-and-retry flow could never succeed.

Trip Service re-checks the response against its own list anyway — ARCHITECTURE.md §4 makes it
ultimately responsible for never proposing a decliner twice, and an older Matching instance would
silently ignore the field. If a decliner comes back regardless, we re-ask up to
`max-proposals-per-attempt` times and then report no driver rather than looping forever.

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `trip.orchestration.max-proposals-per-attempt` | `3` | Safety net for a Matching instance that ignores `excludedDriverIds`: how many times to re-ask within one attempt before concluding no eligible driver exists. |

## Surge demand signal

The pending-request counter is incremented on `REQUESTED → PRICED` and decremented when the trip
leaves the matching pipeline (`COMPLETED`, `CANCELLED_BY_RIDER`, `UNMATCHED`). A
`surge_pending_registered` flag on the trip row makes both sides idempotent, so a retried quote
cannot double-increment and a re-sent terminal transition cannot double-decrement. The calls are
best effort: if Surge Pricing is down the trip still succeeds and the multiplier is briefly stale.

## Running and testing

```bash
mvn -pl trip-service spring-boot:run     # needs Postgres on :5433, Kafka on :9092, Eureka on :8761
mvn -pl trip-service test
```

Tests use embedded Kafka, an in-memory schema, and `StubServer` (from `common`'s test-jar) to stand
up the three downstream services over real HTTP, so the Feign clients' URLs, verbs and JSON codecs
are genuinely exercised.

## Manual smoke test (happy path)

Boot everything, then wait for all services to appear in Eureka at <http://localhost:8761>:

```bash
docker-compose up -d
docker-compose logs -f trip-service   # wait for "Started TripServiceApplication"
```

Driver Discovery needs at least one available driver near the pickup, otherwise matching correctly
returns `UNMATCHED`:

```bash
curl -X POST http://localhost:8086/drivers/driver-1/location \
  -H 'Content-Type: application/json' \
  -d '{"lat": 37.7752, "lon": -122.4189, "available": true}'
```

**1. Create the trip — it comes back already `PRICED`.**

```bash
TRIP=$(curl -sS -X POST http://localhost:8083/trips \
  -H 'Content-Type: application/json' \
  -d '{"riderId":"rider-1","pickup":{"lat":37.7749,"lon":-122.4194},"dropoff":{"lat":37.8044,"lon":-122.2712}}')
echo "$TRIP" | jq '{id, state, quotedPrice, quoteCurrency}'
TRIP_ID=$(echo "$TRIP" | jq -r .id)
```

Expect `"state": "PRICED"` and a non-null `quotedPrice`. If you get `502`, Price Estimation or one
of its five dependencies is down — the response still contains `tripId`, so retry with:

```bash
curl -sS -X POST "http://localhost:8083/trips/$TRIP_ID/request-quote" | jq .state
```

**2. Rider accepts — Matching runs and the trip lands in `DRIVER_PROPOSED`.**

```bash
curl -sS -X POST "http://localhost:8083/trips/$TRIP_ID/transition" \
  -H 'Content-Type: application/json' -d '{"toState":"ACCEPTED_BY_RIDER"}' \
  | jq '{state, driverId, attemptCount}'
```

Expect `"state": "DRIVER_PROPOSED"` and a `driverId`. (If no driver is registered you will see
`"state": "UNMATCHED"` — that is the correct behaviour, not a bug.)

**3. Driver accepts, drives, completes, pays.**

```bash
for STATE in DRIVER_ACCEPTED EN_ROUTE_TO_PICKUP RIDER_PICKED_UP COMPLETED PAID; do
  curl -sS -X POST "http://localhost:8083/trips/$TRIP_ID/transition" \
    -H 'Content-Type: application/json' -d "{\"toState\":\"$STATE\"}" | jq -r .state
done
```

Expect `DRIVER_ACCEPTED EN_ROUTE_TO_PICKUP RIDER_PICKED_UP COMPLETED PAID`, one per line.

**4. Inspect the final trip and its full history.**

```bash
curl -sS "http://localhost:8083/trips/$TRIP_ID" | jq '{state, driverId, quotedPrice, history: [.history[].toState]}'
```

**5. Verify the events landed on `trip-events`.**

```bash
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-server kafka:9092 --topic trip-events --from-beginning --timeout-ms 5000
```

Expect nine events for this trip: `REQUESTED, PRICED, ACCEPTED_BY_RIDER, DRIVER_PROPOSED,
DRIVER_ACCEPTED, EN_ROUTE_TO_PICKUP, RIDER_PICKED_UP, COMPLETED, PAID`.

### Decline path

Between steps 2 and 3, decline instead of accepting:

```bash
curl -sS -X POST "http://localhost:8083/trips/$TRIP_ID/transition" \
  -H 'Content-Type: application/json' -d '{"toState":"DRIVER_DECLINED"}' \
  | jq '{state, driverId, attemptCount, declinedDriverIds}'
```

One request, two transitions: `DRIVER_DECLINED` then straight back to `DRIVER_PROPOSED` with a
different driver, `attemptCount` incremented and the decliner in `declinedDriverIds`. After three
declines the trip ends in `UNMATCHED`.



