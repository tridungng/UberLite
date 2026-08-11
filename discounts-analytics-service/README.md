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

```bash
docker compose up -d discovery-server zipkin trip-service discounts-analytics-postgres
./mvnw -pl discounts-analytics-service spring-boot:run
./mvnw -pl discounts-analytics-service test
```

