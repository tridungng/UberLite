# discounts-analytics-service

Discounts Analytics Service — ARCHITECTURE.md §2 (background services). Nightly promo targeting.

| | |
|---|---|
| Port | 8094 |
| Eureka id | `discounts-analytics-service` |
| State | Postgres `discountsanalyticsdb` (host port 5438) |
| Input | `GET /trips/rider-trip-counts` on `trip-service` (**not** Kafka) |

## API

```
GET  /promo-candidates            → [PromoCandidateDto]
POST /promo-candidates/refresh    runs the batch now instead of waiting for the cron; idempotent
```

## How it works

At 02:00 (`discounts-analytics.cron`) `PromoFlagger` pulls rider trip counts from Trip Service and
flags riders below `discounts-analytics.trip-threshold` (default 3) into `promo_candidates`.

Riders who cross the threshold are **swept out** on the next run. Without the sweep the promotion
would never end — a rider flagged on their first ride would still be flagged on their hundredth.

## Why this one is not a Kafka consumer

Unlike the other two background services, this is a batch that pulls an *aggregate*. Subscribing to
`trip-events` would mean maintaining a running per-rider count in order to answer a question that is
asked once a day, and an idle consumer group would be a dependency with nothing to do. The Feign
client's `readTimeout` is 30s accordingly: this is a batch, so a slow aggregate query is expected
rather than an outage.

## Status

The table is **populated only**. `discounts-promotions-service` still prices from the live
`riderTripCount`; making it read `promo_candidates` is a follow-up issue.

## Run

The platform (`docker compose up -d` with no profile) is Eureka, the gateway, Kafka, Redis, Zipkin
and the databases. This service needs `discounts-analytics-postgres` on top of that, which Compose starts for you when you name the service.
It declares no dependency on any other UberLite service, so it boots on its own and a call to a peer
that is not running fails fast rather than blocking startup. See the root README, "Independent deployability".

```bash
docker compose up -d discounts-analytics-service          # in a container, with its dependencies
# or, running it from source against the containerised platform:
docker compose up -d
./mvnw -pl discounts-analytics-service spring-boot:run
./mvnw -pl discounts-analytics-service test
```

| Probe | Meaning |
|-------|---------|
| `/actuator/health/liveness` | what the container `HEALTHCHECK` polls; a failure means restart |
| `/actuator/health/readiness` | `readinessState` plus `db` - safe to route traffic here |
| `/actuator/health` | composite, including peers - informational, a `DOWN` here can just mean a dependency is missing |

