# tax-tolls-service

Tax & Tolls Service (TTS) — ARCHITECTURE.md §2. Reference data for the `cm` and `T` terms of the
price formula.

| | |
|---|---|
| Port | 8090 |
| Eureka id | `tax-tolls-service` |
| State | Postgres `taxtollsdb` (host port 5434) |
| Called by | `price-estimation-service` |

## API

```
GET  /tax/{regionId}      → TaxRateDto     {regionId, rate}
POST /tolls/estimate      body RouteDto    → TollEstimateDto {amount}
```

## Schema (ARCHITECTURE.md §7)

```
tax_rates(region_id, rate)
toll_segments(route_id, amount)
```

Flyway-managed under `src/main/resources/db/migration`, with the demo rows seeded by the same
migration — a fresh `docker compose up` must be able to quote a price without a manual data load.

## MVP simplification

Static reference tables, no external tax authority or toll operator integration. The tables are
"static-ish": they change on a legislative timescale, not a request timescale, which is exactly why
this is Postgres and not Redis.

## Run

```bash
docker compose up -d discovery-server zipkin tax-tolls-postgres
./mvnw -pl tax-tolls-service spring-boot:run
./mvnw -pl tax-tolls-service test
```

The integration test needs Docker (Testcontainers Postgres) and self-skips when it is unavailable.

