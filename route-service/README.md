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

```bash
docker compose up -d discovery-server zipkin
./mvnw -pl route-service spring-boot:run
./mvnw -pl route-service test
```

