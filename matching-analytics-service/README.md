# matching-analytics-service

Matching Analytics Service — ARCHITECTURE.md §2 (background services). The audit trail for matching.

| | |
|---|---|
| Port | 8093 |
| Eureka id | `matching-analytics-service` |
| State | Postgres `matchinganalyticsdb` (host port 5437) |
| Input | Kafka `trip-events` (consumer only, no producer) |

## API

```
GET /match-log/{tripId}   → [MatchLogEntryDto]
```

Debug read. Nothing in the trip flow depends on this service being up — it is a subscriber, and
Trip Service does not know it exists (ARCHITECTURE.md §3, the trigger framework).

## Why it exists

`TripEventConsumer` filters `trip-events` for `DRIVER_PROPOSED`, `DRIVER_ACCEPTED` and
`DRIVER_DECLINED`, writing one `match_log` row each.

This is the **only** place a trip's full matching history survives. Trip Service overwrites
`driver_id` on every retry, so its own row cannot show that two drivers declined before a third
accepted — the state history table records the transitions but the losing drivers are gone. Any
future work on match quality needs precisely those losers.

## MVP simplification

Logging only, no training loop (ARCHITECTURE.md §9).

## Run

The platform (`docker compose up -d` with no profile) is Eureka, the gateway, Kafka, Redis, Zipkin
and the databases. This service needs `kafka`, `matching-analytics-postgres` on top of that, and Compose starts them
automatically when you name the service - it declares no dependency on any other UberLite service,
so it boots on its own and any call to a peer that is not running fails fast rather than blocking
startup. See the root README, "Independent deployability".

```bash
docker compose up -d matching-analytics-service          # in a container, with its dependencies
# or, running it from source against the containerised platform:
docker compose up -d
./mvnw -pl matching-analytics-service spring-boot:run
./mvnw -pl matching-analytics-service test
```

| Probe | Meaning |
|-------|---------|
| `/actuator/health/liveness` | what the container `HEALTHCHECK` polls; a failure means restart |
| `/actuator/health/readiness` | `readinessState` plus `db` - safe to route traffic here |
| `/actuator/health` | composite, including peers - informational, a `DOWN` here can just mean a dependency is missing |

