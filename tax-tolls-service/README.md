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

The platform (`docker compose up -d` with no profile) is Eureka, the gateway, Kafka, Redis, Zipkin
and the databases. This service needs `tax-tolls-postgres` on top of that, which Compose starts for you when you name the service.
It declares no dependency on any other UberLite service, so it boots on its own and a call to a peer
that is not running fails fast rather than blocking startup. See the root README, "Independent deployability".

```bash
docker compose up -d tax-tolls-service          # in a container, with its dependencies
# or, running it from source against the containerised platform:
docker compose up -d
./mvnw -pl tax-tolls-service spring-boot:run
./mvnw -pl tax-tolls-service test
```

| Probe | Meaning |
|-------|---------|
| `/actuator/health/liveness` | what the container `HEALTHCHECK` polls; a failure means restart |
| `/actuator/health/readiness` | `readinessState` plus `db` - safe to route traffic here |
| `/actuator/health` | composite, including peers - informational, a `DOWN` here can just mean a dependency is missing |

The integration test needs Docker (Testcontainers Postgres) and self-skips when it is unavailable.

