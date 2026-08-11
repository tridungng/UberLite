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

```bash
docker compose up -d discovery-server zipkin kafka forecasting-postgres
./mvnw -pl forecasting-service spring-boot:run
./mvnw -pl forecasting-service test
```

