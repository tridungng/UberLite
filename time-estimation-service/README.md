# time-estimation-service

Time Estimation Service (TES) — ARCHITECTURE.md §2. Travel-time estimate for a pickup point.

| | |
|---|---|
| Port | 8088 |
| Eureka id | `time-estimation-service` |
| State | none (seed file loaded at startup) |
| Called by | `price-estimation-service` |

## API

```
GET /time/estimate?lat=&lon=   →  TimeEstimateDto {minutes}
```

Returns the shared `TimeEstimateDto` from `common`. The wire field is `minutes`; `getSeconds()` is a
derived convenience on the DTO so callers reasoning in seconds don't each redo the conversion.

## MVP simplification

`estimate = base-minutes × heat-multiplier(cell)`. The multiplier comes from a static seed file, not
live traffic. Cells absent from the seed are free-flowing (multiplier 1.0), and a missing or
unreadable seed file degrades to a flat 1.0 rather than refusing to start — an optimistic ETA is a
much smaller problem than a price-estimation fan-out that cannot complete.

Cell keys are whole-degree `"<lat>,<lon>"` truncation rather than H3: the seed table is a demo
fixture, and H3 resolution would imply a precision the data does not have.

A real traffic feed plugs in at `HeatmapService.multiplierFor(...)` with no contract change.

## Configuration

```yaml
time-estimation:
  base-minutes: 10.0
  heatmap-seed: classpath:heatmap-seed.json
```

## Run

The platform (`docker compose up -d` with no profile) is Eureka, the gateway, Kafka, Redis, Zipkin
and the databases. This service needs nothing on top of that, and declares no dependency on
any other UberLite service - it boots on its own, and a call to a peer that is not running fails
fast rather than blocking startup. See the root README, "Independent deployability".

```bash
docker compose up -d time-estimation-service          # in a container, with its dependencies
# or, running it from source against the containerised platform:
docker compose up -d
./mvnw -pl time-estimation-service spring-boot:run
./mvnw -pl time-estimation-service test
```

| Probe | Meaning |
|-------|---------|
| `/actuator/health/liveness` | what the container `HEALTHCHECK` polls; a failure means restart |
| `/actuator/health/readiness` | `readinessState` - safe to route traffic here |
| `/actuator/health` | composite, including peers - informational, a `DOWN` here can just mean a dependency is missing |

