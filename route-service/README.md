# route-service

Route Service (RS) — ARCHITECTURE.md §2. Distance between two points.

| | |
|---|---|
| Port | 8087 |
| Eureka id | `route-service` |
| State | none (pure function) |
| Called by | `price-estimation-service`, `matching-service` |

## API

```
GET /route/estimate?lat1=&lon1=&lat2=&lon2=[&actualDistanceKm=]
    → RouteEstimateDto {straightDistanceKm, detourFactor}
```

Returns the shared `RouteEstimateDto` from `common` — the same class both callers deserialize into,
so the contract cannot drift without a compile error on one side.

`detourFactor` is **null** unless the caller supplies `actualDistanceKm`. It is deliberately not
defaulted to 0: callers multiply the trip distance by it, and a 0 would produce a free ride. See
`RouteControllerTest`, which pins the null case.

## MVP simplification

Great-circle (haversine) distance, no road network. Swapping in OSRM or Google replaces
`RouteService` only; the HTTP contract is expressed in distance, not in how the distance was found
(ARCHITECTURE.md §9).

## Run

The platform (`docker compose up -d` with no profile) is Eureka, the gateway, Kafka, Redis, Zipkin
and the databases. This service needs nothing on top of that, and declares no dependency on
any other UberLite service - it boots on its own, and a call to a peer that is not running fails
fast rather than blocking startup. See the root README, "Independent deployability".

```bash
docker compose up -d route-service          # in a container, with its dependencies
# or, running it from source against the containerised platform:
docker compose up -d
./mvnw -pl route-service spring-boot:run
./mvnw -pl route-service test
```

| Probe | Meaning |
|-------|---------|
| `/actuator/health/liveness` | what the container `HEALTHCHECK` polls; a failure means restart |
| `/actuator/health/readiness` | `readinessState` - safe to route traffic here |
| `/actuator/health` | composite, including peers - informational, a `DOWN` here can just mean a dependency is missing |

