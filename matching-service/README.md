# Matching Service

Greedy nearest-available-driver matching (ARCHITECTURE.md Sec. 2, "MS"; paper Sec. 4.1).

Stateless — no datastore. It calls out to `driver-discovery-service` and `route-service` only.

## API

### `POST /matches`

```json
{ "tripId": "trip-1", "pickup": { "lat": 37.7749, "lon": -122.4194 } }
```

**200** — the best `DriverCandidateDto`:

```json
{ "driverId": "driver-7", "location": { "lat": 37.78, "lon": -122.41 }, "etaSeconds": 150 }
```

| Status | Meaning |
|---|---|
| 200 | A driver was proposed |
| 400 | Invalid body (blank `tripId`, missing/out-of-range `pickup`) |
| 404 | No drivers available near the pickup — a real, retryable answer |
| 502 | A downstream service was unreachable |

**404 vs 502 matters.** A fallback that returned an empty driver list on a Driver Discovery outage
would make an infrastructure failure look like an empty marketplace, and Trip Service would burn its
k=3 retry budget and park the trip in `UNMATCHED` for the wrong reason. So the Feign clients have no
fallback and outages surface as 502.

For the same reason the service only treats `FeignException` as a dependency failure. A bug in our own
code (an NPE, say) propagates and becomes an honest 500 rather than being mislabelled "Driver
Discovery is down".

## Flow

1. `GET /drivers/nearby` on driver-discovery-service around the pickup (`matching.radius-meters`,
   `matching.candidate-limit`).
2. For each candidate, `GET /route/estimate` on route-service for the driver → pickup distance, then
   a local straight-line ETA at `matching.average-speed-mps`.
3. Return the candidate with the lowest pickup ETA (ties broken by `driverId` for determinism).

A candidate that can't be scored is skipped rather than failing the request. If candidates existed
but *none* could be scored, that's a 502, not a 404.

`time-estimation-service` is deliberately **not** called: this ETA only has to *rank* candidates, and
it is monotonic in distance, so it orders them identically to a traffic-aware ETA at N fewer network
calls. The rider-facing ETA remains TES's job.

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `matching.radius-meters` | `3000` | Search radius sent to Driver Discovery |
| `matching.candidate-limit` | `10` | Max candidates ranked; bounds route-service fan-out (max 50) |
| `matching.average-speed-mps` | `8.33` | 30 km/h urban average, used for the ranking ETA |
| `matching.default-detour-factor` | `1.3` | Straight-line → road-distance correction |

Port `8089`. Health at `/actuator/health`. Profile `docker` for Compose.

## Declined drivers are Trip Service's job

This service does **not** track exclusions. It has no memory of which driver it proposed a moment
ago. Trip Service owns the retry budget (k=3, ARCHITECTURE.md Sec. 3) and the declined-driver list on
the trip's Postgres row, and simply re-calls `POST /matches` with the same `tripId`.

Consequence, and it is a real MVP limitation: on a retry this service will happily re-propose the
driver who just declined, because from its point of view nothing has changed. Trip Service must
filter the response against its own declined list. The fix when it matters is an `excludedDriverIds`
field on `MatchRequestDto` — deliberately left out here because issue 02/09 owns the retry logic.

## Swap-out point

The paper specifies batch-optimal assignment over (rider, driver, route) triplets. That replaces
`MatchingService.findBestMatch` wholesale: buffer requests over a short window and solve a min-cost
bipartite assignment instead of picking greedily per request. The `POST /matches` contract does not
change.

## Testing

```bash
mvn -pl matching-service test
```

- `PickupEtaCalculatorTest` — the ranking rule as a pure function.
- `MatchingServiceTest` — ranking with both Feign clients mocked (3 candidates, closest ETA wins),
  plus the 404 / 502 / skip-bad-candidate paths.
- `MatchingIntegrationTest` — `@SpringBootTest` with both downstreams stubbed over real HTTP via
  `StubServer` from `common`'s test-jar. Eureka is disabled and each service id is pointed at the stub
  through the SimpleDiscoveryClient, so the real Feign clients and URLs are exercised.

The issue asked for WireMock. **It does not work here, and this was verified rather than assumed:**

| Attempt | Result |
|---|---|
| `wiremock-jre8:2.35.0` | `NoClassDefFoundError: javax/servlet/DispatcherType` — Jetty 9.4 needs the pre-Jakarta servlet API that Boot 4 dropped |
| …plus `javax.servlet-api` | `NoClassDefFoundError: org.eclipse.jetty.util.log.Log` — deleted in Jetty 10; Boot 4 manages Jetty 12 |
| `wiremock-standalone` (shaded) | Would work, but is not reachable from this environment's artifact mirror |

Pinning Jetty back to 9.4 for the whole module to satisfy a test double is not a trade worth making,
so `StubServer` stands in. Its API is intentionally WireMock-shaped if that ever changes.

## Running

```bash
mvn -pl matching-service spring-boot:run
```



