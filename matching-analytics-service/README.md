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

```bash
docker compose up -d discovery-server zipkin kafka matching-analytics-postgres
./mvnw -pl matching-analytics-service spring-boot:run
./mvnw -pl matching-analytics-service test
```

