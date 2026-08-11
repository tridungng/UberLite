# UberLite MVP — Architecture Spec

Stack: Java 25, Spring Boot 4.x, Maven multi-module

Source: *Designing UberLite: a Ride Aggregator Service* (Prasaad & Vikström, UW CSE552, Fall 2019).
This document is the MVP interpretation of that paper: same services, same trip state machine,
infrastructure simplified to run on a laptop via Docker Compose.

## 1. Design principles for the MVP

- **Every service from the paper exists**, even if its internals are a stub. The point is to
  prove out the *decomposition and contracts*, not to build production ML.
- **Simplify compute, not the interface.** e.g. Time Estimation Service returns a real
  time-estimate over HTTP; internally it uses a fake static heat map instead of live traffic data.
  Anything downstream can't tell the difference.
- **Postgres instead of HBase/Accumulo** for the Trip Store. The paper picks HBase for horizontal
  scale and secondary indexing; at MVP scale Postgres + explicit domain events gives the same
  functional contract (durable state machine, queryable by rider/driver, triggers) without the
  operational cost.
- **Kafka is the trigger framework**, exactly as the paper proposes (Sec. 6, "Trigger Framework").
  Trip Service publishes a domain event per state transition; other services subscribe.
- **Redis** for anything the paper marks "Realtime / Severe staleness sensitivity" — driver
  locations, surge multipliers.
- **REST + OpenFeign** between synchronous services (e.g. Price Estimation calling Tax Service).
  gRPC is a stretch goal, not MVP-blocking.
- **No ML.** Forecasting, Pricing strategy, and Matching are rule-based in the MVP. Each service's
  section below states the honest rule used and where a model would plug in later.

## 2. Service inventory

### Core marketplace services (paper Sec. 4)

| Service | Paper role | MVP simplification |
|---|---|---|
| Map Indexing | H3 hexagonal geo-index | **Not simplified** — use Uber's real `h3-java` library as a shared dependency, not a separate network service (it's a pure function, no state) |
| Driver Discovery (DRS) | Realtime driver locations, neighborhood queries | Redis geo-index keyed by H3 cell; drivers POST location every N seconds |
| Route Service (RS) | k candidate routes A→B | Haversine straight-line distance × a fixed detour factor, returns 1 synthetic route (no real road network) |
| Time Estimation (TES) | Traffic-aware ETA | Static per-H3-cell "heat" multiplier table (seed data), applied to Route Service's distance/speed |
| Surge Pricing (SPS) | Realtime surge multiplier per cell | `multiplier = clamp(pending_requests / active_drivers, 1.0, 3.0)` per H3 cell, recomputed on each Driver Discovery / Trip event |
| Discounts & Promotions (DPS) | Rider/driver incentives | Static rule table (e.g. "first 3 rides: 20% off") stored in Postgres, no personalization model |
| Tax & Tolls (TTS) | Region tax + toll lookup | Static config table per region/route, Postgres |
| Price Estimation (PES) | Aggregates all of the above into a price | Implements the paper's formula exactly (Sec. 2), calling the other services over REST |
| Matching Service (MS) | Optimal driver↔rider batch matching | Greedy nearest-available-driver matching per request, not batch-optimal assignment |
| Trip Service | State machine + source of truth | Postgres-backed state machine, publishes Kafka events per transition (this *is* the paper's "Trip Store" + implicit orchestrator) |

### Background services (paper Sec. 5)

| Service | Paper role | MVP simplification |
|---|---|---|
| Forecasting | ML demand/supply forecast | Rolling average of last-N-days demand per H3 cell/hour-of-day, no weather/events input |
| Pricing (strategy) | Turns forecast into surge policy | Directly feeds the SPS clamp bounds from the forecast rolling average; no separate optimization |
| Matching Analytics | Logs matches for model improvement | Kafka consumer that persists every match input/output to Postgres — logging only, no training loop |
| Discounts Analytics | Personalized promotion strategy | Nightly batch job (Spring `@Scheduled`) that flags riders below a ride-count threshold for a promo, writes rows DPS reads |

## 3. Trip state machine (paper Sec. 3, Fig. 1)

```
REQUESTED
   → PRICED            (Price Estimation returns quote)
   → ACCEPTED_BY_RIDER  (rider confirms)  |  CANCELLED_BY_RIDER
   → DRIVER_PROPOSED    (Matching assigns a candidate driver)
   → DRIVER_ACCEPTED    |  DRIVER_DECLINED → (retry, up to k=3 attempts) → DRIVER_PROPOSED
                                            → (k exceeded) → UNMATCHED
   → EN_ROUTE_TO_PICKUP
   → RIDER_PICKED_UP
   → COMPLETED
   → PAID
```

`UNMATCHED` is also reachable directly from `ACCEPTED_BY_RIDER`: the *first* call to Matching can
already come back "no drivers", and there is no driver to decline in that case. It is terminal —
the rider's retry is to request a new trip, which is the normal "no cars available" flow in a
ride-hailing app, not an internal loop.

Each transition is a Postgres row update inside Trip Service **and** a Kafka message on topic
`trip-events` with `{tripId, fromState, toState, timestamp, payload}`. This is the paper's
trigger framework: any service can subscribe to `trip-events` without Trip Service knowing who's
listening (Matching Analytics and Discounts Analytics both do this). Trip creation is published as
the `null → REQUESTED` transition, so a consumer never sees a history that begins mid-trip. The
payload keys are defined once in `common` (`TripEventPayloadKeys`) and never inlined.

`PRICED`, `DRIVER_PROPOSED` and `UNMATCHED` are *conclusions* drawn from a downstream answer, not
states a client may assert. `POST /trips/{id}/transition` rejects them with `409`; they are produced
only by the orchestration below.

## 4. Service-to-service call graph

```
Rider App → API Gateway → Trip Service (REQUESTED)
Trip Service → Price Estimation Service
  Price Estimation → Route Service
  Price Estimation → Time Estimation Service
  Price Estimation → Surge Pricing Service
  Price Estimation → Tax & Tolls Service
  Price Estimation → Discounts & Promotions Service
Trip Service ← price quote ← Price Estimation           [state → PRICED]
Trip Service → Surge Pricing (pending-request ++)        [rider now waiting in this H3 cell]
Rider accepts → Trip Service [state → ACCEPTED_BY_RIDER]
Trip Service → Matching Service
  Matching → Driver Discovery Service (candidate drivers near pickup H3 cell)
  Matching → Route Service (per candidate, pickup ETA)
Trip Service ← proposed driver ← Matching                [state → DRIVER_PROPOSED]
Driver app accepts/declines → Trip Service
Trip Service → Surge Pricing (pending-request --)        [on COMPLETED / CANCELLED_BY_RIDER / UNMATCHED]
Trip Service --Kafka(trip-events)--> Matching Analytics, Discounts Analytics (async, fire-and-forget)
```

The pending-request counter is the demand half of the surge signal — Trip Service is the only
component that knows how many riders are actually waiting in a cell. The calls are best effort and
guarded by a flag on the trip row so they cannot double-count: a stale multiplier is an acceptable
failure, a rider who cannot book because Surge Pricing is unwell is not.

### Matching Service contract

`POST /matches` — body `{tripId, pickup: {lat, lon}, excludedDriverIds: [...]}` → `DriverCandidateDto`
`{driverId, location, etaSeconds}`.

| Status | Meaning | Trip Service should |
|---|---|---|
| 200 | A driver was proposed | move to `DRIVER_PROPOSED` |
| 400 | Invalid body | fix the caller; not retryable |
| 404 | No eligible driver near the pickup | move to `UNMATCHED` (terminal) and notify the rider |
| 502 | A downstream service is unreachable | retry **without** consuming an attempt |

The 404/502 split is load-bearing. Matching's Feign clients have no fallback on purpose: a fallback
returning an empty driver list would make an outage look like an empty marketplace, and Trip Service
would park the trip in `UNMATCHED` for an infrastructure reason.

**404 ends the trip rather than being retried in place.** An immediate retry cannot change the
answer — Driver Discovery's state does not move within a request — and a *delayed* retry would need
a scheduler, which is not in §5. So the retry for an empty marketplace belongs to the rider (request
a new trip), while the k=3 budget covers the case the paper actually describes: a driver was found
and *declined*.

**Matching holds no state between calls, but exclusions travel with the request.** Trip Service owns
the retry budget (k=3) and the durable declined-driver list on the trip row, and sends it as
`excludedDriverIds` on every retry. This is required, not an optimisation: greedy-nearest matching is
deterministic, so a retry without exclusions returns the same driver who just declined, every time.
Matching filters them out *before* the per-candidate Route Service fan-out, and answers 404 if
nothing eligible remains. Trip Service re-checks the response against its own list anyway, since it
is ultimately responsible for never proposing a decliner twice.

## 5. Infrastructure

| Concern | Choice | Why |
|---|---|---|
| Service discovery | Spring Cloud Netflix Eureka | Standard Spring microservices pattern, minimal config |
| API gateway | Spring Cloud Gateway | Single entry point for rider/driver clients |
| Sync inter-service calls | REST via OpenFeign | Simplest to scaffold, easy for Copilot to generate consistently |
| Async / triggers | Kafka (`spring-kafka`) | Matches paper's explicit recommendation for the trigger framework |
| Trip store | Postgres (one schema per service — no shared DB) | Durable, queryable, "database per service" |
| Geospatial / hot data | Redis | Driver locations, surge multipliers — matches paper's "Realtime, Severe staleness" services |
| Local orchestration | Docker Compose | One `docker compose up` boots Eureka, Gateway, Zipkin, Kafka+Zookeeper, Postgres (per service), Redis and all app services. Ordering is gated on container health checks, not just start order |
| Build | Maven multi-module (decided in issue 00; Gradle was the alternative) | Shared `common` module for DTOs + H3 helpers, plus a test-jar with shared test infrastructure |
| Observability | Spring Boot Actuator + Micrometer + Zipkin (Brave bridge) | Delivered in issue 11, no longer a stretch goal. Every service exposes `/actuator/health`, `/info`, `/metrics` and `/prometheus`; trace context propagates over HTTP (via `feign-micrometer`) and Kafka, so one rider request is one Zipkin trace. Shared config lives once in `common/src/main/resources/uberlite-defaults.yml` |
| Aggregate health | `GET /health/aggregate` on the API gateway | Fans out to every Eureka-registered instance and returns a single document; `200` only when all are `UP`. Deliberately separate from the gateway's own `/actuator/health` |

## 6. Repo layout

```
uberlite/
  common/                      # shared DTOs, H3 utility wrapper, Kafka event schemas
  discovery-server/            # Eureka
  api-gateway/                 # Spring Cloud Gateway
  trip-service/
  driver-discovery-service/
  route-service/
  time-estimation-service/
  surge-pricing-service/
  discounts-promotions-service/
  tax-tolls-service/
  price-estimation-service/
  matching-service/
  forecasting-service/
  matching-analytics-service/
  discounts-analytics-service/
  docker-compose.yml
  .github/copilot-instructions.md
  ARCHITECTURE.md
```

## 7. Data model highlights

**Trip Service (Postgres, `trip` schema)**
```
trips(id UUID PK, rider_id, state, pickup_h3 VARCHAR, dropoff_h3 VARCHAR,
      quoted_price NUMERIC, driver_id NULLABLE, attempt_count INT,
      created_at, updated_at)
trip_state_history(id, trip_id FK, from_state, to_state, occurred_at)
```

**Driver Discovery (Redis)**
```
GEOADD drivers:active <lon> <lat> <driverId>
HSET driver:<driverId> h3Cell <cell> lastSeen <ts> status <ONLINE|BUSY>
```

**Surge Pricing (Redis)**
```
HSET surge:<h3Cell> multiplier <float> updatedAt <ts>
```

**Tax & Tolls, Discounts & Promotions (Postgres, static-ish reference tables)**
```
tax_rates(region_id, rate)
toll_segments(route_id, amount)
promo_rules(id, description, discount_pct, condition_json)
```

## 8. Price formula (paper Sec. 2, implemented as-is in Price Estimation Service)

```
p = ((cd * d + ct * t) * s + cm) * η * (1 + T)
```
`cd`=cost/distance, `d`=distance, `ct`=cost/time, `t`=estimated time, `s`=surge multiplier,
`cm`=misc (tolls), `η`=discount rate, `T`=tax rate.

## 9. What's explicitly out of scope for MVP

- Real map/routing provider integration (OSRM/Google) — Route Service is a stub, swappable later.
- Any trained ML model — Forecasting/Pricing/Matching are rule-based, with the paper's intended
  model boundary documented in code comments so a real model can be dropped in.
- Payments processing — Trip Service has a `PAID` state but no real payment gateway call.
- Auth/authz — assume trusted clients for MVP; issue 11 sketches where JWT would go.
- Multi-region / global H3 base cells — single-region deployment only.
