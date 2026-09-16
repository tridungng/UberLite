# forecasting-service

Forecasting Service — ARCHITECTURE.md §2 (background services). Demand per H3 cell and hour.

| | |
|---|---|
| Port | 8092 |
| Eureka id | `forecasting-service` |
| State | Postgres `forecastingdb` (host port 5436) |
| Input | Kafka `trip-events` (consumer only, no producer) |

## API

```
GET /forecast/{h3Cell}?hourOfDay=18
    → DemandForecastDto {h3Cell, hourOfDay, predictedDemand}
```

## How it works

`TripEventConsumer` filters `trip-events` down to `REQUESTED` transitions and increments
`demand_counts(h3_cell, hour_of_day, day_bucket, count)`. `DemandForecaster` then averages the last
`forecasting.window-days` buckets for that cell and hour.

Two details that are easy to get wrong and are therefore pinned by `ForecastWindowTest`:

- Only **complete** day buckets count. A bucket qualifies once its hour has fully elapsed, so asking
  at 14:00 about the 20:00 rush is not dragged down by an evening that has not happened yet.
- A day with no rows is **zero demand, not missing data**. Skipping empty days would make a quiet
  cell look as busy as a busy one, because the average would only ever see the days it was busy.

`counted_trips` gives the consumer idempotency: Kafka delivery is at-least-once, and without it a
redelivered `REQUESTED` would inflate demand and, downstream, the surge multiplier.

## MVP simplification

Rolling average, no model, no weather/events input. `DemandForecaster` is the documented plug-in
point for a real forecaster.

This endpoint is also the intended seam for a *surge-pricing v2*: Surge Pricing is currently purely
reactive (`pending_requests / active_drivers`) and would use the forecast to pre-position its clamp
bounds. **Not wired up yet** — deliberately, so the coupling is a decision rather than an accident.

## Configuration

```yaml
forecasting:
  window-days: 7
  zone: UTC
```

## Run

The platform (`docker compose up -d` with no profile) is Eureka, the gateway, Kafka, Redis, Zipkin
and the databases. This service needs `kafka`, `forecasting-postgres` on top of that, and Compose starts them
automatically when you name the service - it declares no dependency on any other UberLite service,
so it boots on its own and any call to a peer that is not running fails fast rather than blocking
startup. See the root README, "Independent deployability".

```bash
docker compose up -d forecasting-service          # in a container, with its dependencies
# or, running it from source against the containerised platform:
docker compose up -d
./mvnw -pl forecasting-service spring-boot:run
./mvnw -pl forecasting-service test
```

| Probe | Meaning |
|-------|---------|
| `/actuator/health/liveness` | what the container `HEALTHCHECK` polls; a failure means restart |
| `/actuator/health/readiness` | `readinessState` plus `db` - safe to route traffic here |
| `/actuator/health` | composite, including peers - informational, a `DOWN` here can just mean a dependency is missing |

