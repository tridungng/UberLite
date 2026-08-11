# UberLite MVP

Minimal scaffold for the UberLite MVP implementing the *Designing UberLite: a Ride Aggregator Service* paper (Prasaad & Vikström, UW CSE552, Fall 2019).

**Stack:** Java 25, Spring Boot 4.x, Maven 3.9+, Docker Compose

## Architecture Overview

See `ARCHITECTURE.md` for the complete service decomposition, data models, and API contracts. UberLite is a **microservices** project with:

- **15 services** (8 core marketplace, 4 background analytics, 3 infrastructure)
- **Postgres** for trip state machine and static reference data
- **Redis** for driver locations and surge multipliers (real-time, low latency)
- **Kafka** for domain event triggers (trip state transitions propagate to analytics services)
- **Eureka** for service discovery
- **Spring Cloud Gateway** as API entry point
- **Micrometer + Zipkin** for distributed tracing across every hop

## Getting Started

From a clean clone to a completed trip in three commands.

### Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 25 | `java -version` must report 25; the build sets `--release 25` |
| Maven | 3.9+ | or use the bundled `./mvnw` |
| Docker | with Compose v2 | `docker compose version` — v2 syntax, not the old `docker-compose` binary |
| `curl`, `jq` | any | only needed by `scripts/demo.sh` (`brew install jq`) |

You need roughly **8 GB of RAM free for Docker**. The stack is 24 containers: 14 JVMs, six
Postgres instances, Kafka + ZooKeeper, Redis and Zipkin.

### 1. Clone and build

```bash
git clone <repo-url> UberLite
cd UberLite
./mvnw clean install
```

The Docker images build the code again inside the build stage, so this step is not strictly
required to run the stack — but it fails fast, and much faster, if something is broken.

### 2. Bring the whole stack up

```bash
docker compose up --build
```

First run takes a while: every image compiles the Maven reactor from scratch. Later runs reuse the
layer cache.

Compose starts things in dependency order and waits on real health checks, so when the command
settles every service is genuinely reachable rather than merely started. Watch progress with:

```bash
docker compose ps          # STATUS column should read "healthy" for every service
```

### 3. Run the end-to-end demo

In a second terminal:

```bash
./scripts/demo.sh
```

It drives the full happy path through the API gateway — puts two drivers online, creates and prices
a trip, matches a driver, walks the trip to `PAID`, then checks that the Kafka-driven analytics
services saw it. Every request and response is printed. **It exits non-zero on the first unexpected
status code**, so it works as a smoke test in CI too.

Useful overrides:

```bash
BASE_URL=http://localhost:8083 ./scripts/demo.sh   # bypass the gateway, hit trip-service directly
READY_TIMEOUT_SECONDS=600 ./scripts/demo.sh        # slow machine / cold image cache
```

### 4. Where to look

| What | Where | Shows |
|------|-------|-------|
| **Eureka dashboard** | http://localhost:8761 | every registered instance; all 13 clients should be listed |
| **Zipkin UI** | http://localhost:9411 | distributed traces — search by service `trip-service` |
| **Aggregate health** | http://localhost:8080/health/aggregate | one JSON document with the health of every registered service; `200` only when all are `UP` |
| **Per-service health** | `http://localhost:<port>/actuator/health` | see the port table below |
| **Per-service metrics** | `http://localhost:<port>/actuator/prometheus` | Micrometer metrics |
| **API gateway** | http://localhost:8080 | single entry point; routes listed in `api-gateway/application.yml` |

To see the fan-out the demo talks about, open Zipkin, pick `trip-service` and look at the trace for
`POST /trips`. One trace spans trip-service → price-estimation-service → route-service,
time-estimation-service, surge-pricing-service, tax-tolls-service and
discounts-promotions-service. `scripts/demo.sh` prints a direct link to its own trace when it
finishes.

### 5. Shut down

```bash
docker compose down          # stop everything, keep the databases
docker compose down -v       # also drop the Postgres volumes for a truly clean slate
```

### Running a single service outside Docker

Every module is independently runnable. Start the infrastructure it needs, then:

```bash
docker compose up -d discovery-server zipkin redis      # whatever that service depends on
./mvnw -pl route-service spring-boot:run
```

Without the `docker` profile a service defaults to `localhost` for Eureka, Redis, Kafka and its
database, and the host ports published by Compose match those defaults — so a locally run service
drops into a partially containerised stack without extra configuration.

## Service Inventory

### Core Marketplace Services (paper Sec. 4)

| Service | Port | Purpose | State |
|---------|------|---------|-------|
| **Trip Service** | 8083 | Trip state machine, orchestrator | ✅ Complete |
| **Price Estimation Service** | 8085 | Quote trip price | ✅ Implemented |
| **Matching Service** | 8089 | Assign driver to trip (greedy) | ✅ Implemented |
| **Route Service** | 8087 | Calculate distance (Haversine) | ✅ Implemented |
| **Time Estimation Service** | 8088 | Estimate ETA (static heat map) | ✅ Implemented |
| **Surge Pricing Service** | 8084 | Compute surge multiplier | ✅ Implemented |
| **Driver Discovery Service** | 8086 | Query nearby drivers (Redis geo) | ✅ Implemented |
| **Tax & Tolls Service** | 8090 | Look up tax/toll rates | ✅ Implemented |
| **Discounts & Promotions Service** | 8091 | Apply promo codes | ✅ Implemented |

### Background Analytics Services (paper Sec. 5)

All three are rule-based/logging-only in the MVP — no ML — and each owns its own Postgres instance
("database per service", ARCHITECTURE.md §5).

| Service | Port | Purpose | State |
|---------|------|---------|-------|
| **Forecasting Service** | 8092 | Counts `REQUESTED` demand per H3 cell/hour, serves a rolling average | ✅ Implemented |
| **Matching Analytics Service** | 8093 | Kafka consumer, persists every propose/accept/decline to `match_log` | ✅ Implemented |
| **Discounts Analytics Service** | 8094 | Nightly `@Scheduled` batch, flags low-ride riders into `promo_candidates` | ✅ Implemented |

**Forecasting Service** — consumes `trip-events`, increments
`demand_counts(h3_cell, hour_of_day, day_bucket, count)` on each `REQUESTED` event, and serves:

```
GET /forecast/{h3Cell}?hourOfDay=18   →  {"h3Cell":"...","hourOfDay":18,"predictedDemand":4.5}
```

The average is taken over the last 7 *complete* day buckets for that cell and hour — a bucket only
counts once its hour has fully elapsed, so asking at 14:00 about the 20:00 rush is not dragged down
by an evening that has not happened yet. Days with no rows count as zero demand, not as missing
data. Tunable via `forecasting.window-days` and `forecasting.zone`.

This endpoint is the documented plug-in point for a future *surge-pricing-service v2*: Surge Pricing
is currently reactive (`pending_requests / active_drivers` from live counters only) and would use the
forecast to pre-position its clamp bounds. **Not wired up in this issue** — see `DemandForecaster`.

**Matching Analytics Service** — filters `trip-events` for `DRIVER_PROPOSED`, `DRIVER_ACCEPTED` and
`DRIVER_DECLINED`, writing one `match_log` row each. This is the only place a trip's *full* matching
history survives: Trip Service overwrites `driver_id` on every retry, so its own row cannot show that
two drivers declined before a third accepted. Debug read: `GET /match-log/{tripId}`.

**Discounts Analytics Service** — nightly at 02:00 it calls Trip Service's
`GET /trips/rider-trip-counts` and flags riders with fewer than `discounts-analytics.trip-threshold`
(default 3) completed trips into `promo_candidates`. Riders who cross the threshold are swept out on
the next run, so the promotion actually ends. The table is **populated only** — Discounts &
Promotions' rule evaluator still prices from the live `riderTripCount` and reading this table is a
follow-up issue. Debug: `GET /promo-candidates`, `POST /promo-candidates/refresh` (runs the batch now
instead of waiting for the cron; it is idempotent).

Unlike the other two, this service has no Kafka consumer — it is a batch that pulls an aggregate, so
an idle consumer group would be a dependency with nothing to do.

### Infrastructure Services

| Service | Port | Purpose |
|---------|------|---------|
| **Discovery Server (Eureka)** | 8761 | Service registry |
| **API Gateway** | 8080 | Single entry point + `/health/aggregate` |
| **Zipkin** | 9411 | Trace collector and UI |
| **Kafka** | 9092 (in-network) / 29092 (host) | `trip-events` topic |
| **Redis** | 6379 | Driver locations, surge counters |
| **Postgres** | 5433–5438 | One instance per stateful service |
| **Common** | — | Shared DTOs, H3 utilities, Kafka event schemas, `uberlite-defaults.yml` |

Kafka advertises two listeners: containers reach it as `kafka:9092`, the host as `localhost:29092`.
A single listener cannot serve both — one side always gets an address it cannot route to.

## Data Flow Example: Request a Trip

```
1. Rider App → API Gateway (HTTP POST /trips)
2. API Gateway → Trip Service (create trip, state=REQUESTED)
3. Trip Service → Price Estimation Service (fetch quote)
   3a. Price Estimation → Route Service (distance)
   3b. Price Estimation → Time Estimation Service (ETA)
   3c. Price Estimation → Surge Pricing Service (multiplier)
   3d. Price Estimation → Tax & Tolls Service (rates)
   3e. Price Estimation → Discounts Service (promo)
4. Trip Service publishes Kafka event (state → PRICED)
5. Trip Service → Matching Service (POST /matches → best driver, or 404 if none)
   5a. Matching → Driver Discovery Service (nearby drivers)
   5b. Matching → Route Service (per candidate, pickup ETA)
6. Trip Service publishes Kafka event (state → DRIVER_PROPOSED)
7. Matching Analytics Service (Kafka consumer) logs the match
8. Discounts Analytics Service flags low-ride riders on its own nightly schedule (it polls
   Trip Service's `/trips/rider-trip-counts`; it is not a Kafka consumer)
9. Driver App → API Gateway (accept/decline driver proposal)
10. Trip Service publishes Kafka event (state → DRIVER_ACCEPTED or DRIVER_DECLINED)
... (continue through RIDER_PICKED_UP, COMPLETED, PAID)
```

## Trip State Machine

```
REQUESTED
  ↓
PRICED (Price Estimation Service)
  ↓
ACCEPTED_BY_RIDER or CANCELLED_BY_RIDER
  ↓
DRIVER_PROPOSED (Matching Service assigns candidate)
  ↓
DRIVER_ACCEPTED or DRIVER_DECLINED (retry up to 3 times)
  ↓
EN_ROUTE_TO_PICKUP
  ↓
RIDER_PICKED_UP
  ↓
COMPLETED
  ↓
PAID
```

Each transition publishes a Kafka event on topic `trip-events` for async subscribers (Matching Analytics, Discounts Analytics).

## Package Structure

Each service follows the same layout:

```
<service>/
  src/main/java/com/uberlite/<servicename>/
    ├── api/                          # REST controllers
    │   └── *Controller.java
    ├── client/                       # OpenFeign clients (for calling other services)
    │   └── *Client.java
    ├── domain/                       # Domain logic / business rules
    │   ├── *Service.java
    │   └── *Entity.java
    ├── repository/                   # Data access (Spring Data JPA or custom)
    │   └── *Repository.java
    ├── kafka/                        # Kafka consumers (if applicable)
    │   └── *Listener.java
    └── <ServiceName>Application.java # Spring Boot entry point
  src/main/resources/
    └── application.yml               # Service config
  src/test/
    ├── java/                         # Unit & integration tests
    └── resources/
  Dockerfile                          # Multi-stage Maven build
  pom.xml                             # Service-specific dependencies
```

## Configuration

### Shared defaults

Actuator exposure, tracing, and the Eureka client/instance policy are defined **once**, in
`common/src/main/resources/uberlite-defaults.yml`, and pulled in by every module:

```yaml
spring:
  config:
    import: "optional:classpath:uberlite-defaults.yml"
```

An imported document has lower precedence than the file importing it, so a service can still
override anything locally. Don't copy management/tracing/eureka blocks back into a service — the
whole point is that "consistent across all 14 services" stays true without anyone maintaining it.

### Per-service application.yml

Only what is genuinely service-specific lives in the module:

```yaml
server:
  port: 8XXX                          # unique per service, see the port table above

spring:
  config:
    import: "optional:classpath:uberlite-defaults.yml"
  application:
    name: <service-name>              # MUST match the @FeignClient(name = ...) used by callers
  datasource:
    url: jdbc:postgresql://localhost:5433/<db>   # localhost default keeps the module standalone
    username: uberlite
    password: changeme
  kafka:
    bootstrap-servers: localhost:9092
```

`spring.application.name` is load-bearing, not cosmetic: it is the Eureka service id that
`@FeignClient(name = ...)` and the gateway's `lb://` URIs resolve against. A service without one
registers as `UNKNOWN` and every caller fails with "No instances available".

### Docker profile

Each service's second YAML document overrides only the hostnames that differ inside Compose:

```yaml
---
spring:
  config:
    activate:
      on-profile: docker
  datasource:
    url: jdbc:postgresql://trip-service-postgres:5432/tripdb
  kafka:
    bootstrap-servers: kafka:9092
```

Eureka and Zipkin addresses are *not* repeated per service — they come from the `EUREKA_URL` and
`ZIPKIN_ENDPOINT` environment variables that `docker-compose.yml` sets on every container.

Run with `docker compose up`, or set `SPRING_PROFILES_ACTIVE=docker` manually.

## Testing

### Unit tests
```bash
./mvnw -pl <service> test
```

### Integration tests (requires Docker running)
```bash
docker compose up -d kafka redis discovery-server trip-service-postgres
./mvnw -pl <service> -Dgroups=integration test
```

### Full build + tests
```bash
./mvnw clean install
```

### End-to-end smoke test
`scripts/demo.sh` is the executable version of the manual smoke test — it exercises every service
against a running stack and exits non-zero on the first unexpected status code, so it can be run in
CI as-is. See [Getting Started](#3-run-the-end-to-end-demo).

### Stubbing downstream services

Services that call other services over Feign (Price Estimation, Matching) are tested against
`StubServer`, a small record-and-replay HTTP server shipped in `common`'s **test-jar**:

```xml
<dependency>
    <groupId>com.uberlite</groupId>
    <artifactId>common</artifactId>
    <type>test-jar</type>
    <scope>test</scope>
</dependency>
```

It serves real HTTP on a random port, so the real Feign clients, JSON codecs and URLs are exercised —
which is what catches a `@FeignClient` whose path disagrees with the downstream route.

**WireMock is deliberately not used.** It cannot run on this classpath: `wiremock-jre8` embeds Jetty
9.4, which needs the pre-Jakarta `javax.servlet` API that Spring Boot 4 has dropped, and once that is
patched it fails on `org.eclipse.jetty.util.log.Log`, a class deleted in Jetty 10 (Boot 4 manages
Jetty 12). The full rationale is documented on `StubServer` itself. Don't re-add the dependency.

## Build & Deployment

### Build a single service
```bash
mvn -pl <service-name> clean package
```

### Build all services
```bash
mvn clean install
```

### Docker images

All 14 Dockerfiles are generated from one template and are identical apart from the module name and
port:

1. **Build stage** — `maven:3.9-eclipse-temurin-25`, compiles the module with `-pl <module> -am`.
   The whole repo is copied in because the root pom declares every module, so Maven's reactor needs
   them all present even for a single-module build.
2. **Runtime stage** — `eclipse-temurin:25-jre`, plus `curl` for the healthcheck. Runs as the
   unprivileged `uberlite` user, honours `JAVA_OPTS`, and declares a `HEALTHCHECK` against
   `/actuator/health` so Compose's `condition: service_healthy` has something real to wait on.

The Java version is set once in the root `pom.xml` (`<java.version>25</java.version>`) and must
match the base image tags. Keep the three in sync when upgrading.

Build a single service image:
```bash
docker build -t uberlite/<service-name>:latest -f <service-name>/Dockerfile .
```

## Key Design Decisions

- **REST + OpenFeign** for synchronous inter-service calls (e.g., Price Estimation calling Route Service)
- **Kafka** for asynchronous events (trip state transitions propagate to analytics without tight coupling)
- **Database per service** (each has its own Postgres schema) to enforce service boundaries
- **Redis** for real-time, low-latency data (driver locations, surge multipliers) that tolerates staleness
- **Eureka** for dynamic service discovery so services don't hardcode ports/addresses
- **No authentication/authz** in MVP (assume trusted clients; see ARCHITECTURE.md §9)
- **Rule-based algorithms** (no ML) for forecasting, pricing, matching — these are plug-in points for real models later

## Observability

### Actuator

Every service exposes the same endpoints, because they are configured once rather than per module:

```
GET /actuator/health        # composite health: db, redis, kafka, Eureka registration
GET /actuator/info          # service name, JVM, OS
GET /actuator/metrics       # Micrometer metrics
GET /actuator/prometheus    # the same, in Prometheus scrape format
```

`/actuator/health` is also what each container's `HEALTHCHECK` polls, which is what makes
`depends_on: condition: service_healthy` in `docker-compose.yml` mean "genuinely callable" rather
than "process has started".

### Aggregate view

```
GET http://localhost:8080/health/aggregate
```

The API gateway fans out to every instance Eureka knows about and returns one document. It answers
`200` only when everything is `UP`, `503` otherwise, so `curl -f` is a sufficient CI gate. Instances
are reported individually, so a partial outage ("3 of 4 up") is visible rather than averaged away.

It is deliberately *not* folded into the gateway's own `/actuator/health` — the gateway's health
must describe the gateway, or Compose would restart a perfectly healthy gateway because an unrelated
analytics service was still booting.

### Distributed tracing

Micrometer Tracing with the Brave bridge, reporting to the Zipkin container at
http://localhost:9411. Trace context propagates over both HTTP hops (`feign-micrometer` on every
service that has a Feign client) and Kafka, so a single trip request appears as one trace across
every service it touches.

Sampling is set to 100% via `TRACING_SAMPLE_RATE`, which is a demo setting — turn it down before
this ever sees real traffic.

`scripts/demo.sh` generates its own B3 trace id and sends it as a `b3:` header, then prints a direct
link to the resulting trace, so verifying the fan-out never depends on guessing which trace was
yours.

## Troubleshooting

**`docker compose up` hangs with services stuck in `starting`?**
- `docker compose ps` shows which one. A service that never turns `healthy` blocks everything
  declared `depends_on` it.
- `docker compose logs -f <service>` for the reason. The healthcheck is `/actuator/health`, so the
  service is reporting a component down — usually its database or Kafka.
- On a cold cache the first build is slow; `start_period` is 120s per service before failures count.

**A service isn't in the Eureka dashboard?**
- Check it has `spring.application.name` set. Without it the service registers as `UNKNOWN` and no
  `@FeignClient(name = ...)` or `lb://` route can resolve it.
- Check `EUREKA_URL` reached the container: `docker compose exec <service> env | grep EUREKA`.

**Feign calls fail with "No instances available for X"?**
- `X` must exactly equal the target's `spring.application.name`. This is the single most common
  break: the id in `@FeignClient(name = "…")`, the gateway's `lb://…`, and the target's
  `spring.application.name` are three copies of one string.
- Confirm at http://localhost:8761 that `X` is registered and `UP`.

**Gateway returns 404 for a path that works against the service directly?**
- The route table is `spring.cloud.gateway.server.webflux.routes` in `api-gateway/application.yml`.
  Under the older `spring.cloud.gateway.routes` prefix the routes silently parse as nothing and the
  gateway starts with an empty table. `ApiGatewayRoutesTest` guards this.

**`scripts/demo.sh` fails at "waiting for the stack"?**
- Read the aggregate report it dumps on failure: it names the service that is down.
- Raise the budget with `READY_TIMEOUT_SECONDS=600` on a slow machine.

**Kafka producer/consumer failing?**
- From inside Compose the broker is `kafka:9092`; from the host it is `localhost:29092`. Using the
  wrong one gives a connection that handshakes and then times out.
- Topic names come from `common` — never inline the string.

**Database connection refused?**
- Default (no profile) is `localhost:<published-port>`; the `docker` profile uses the Compose
  hostname on 5432. The published ports are listed in the infrastructure table above.
- Verify credentials match `POSTGRES_USER`/`POSTGRES_PASSWORD` in `docker-compose.yml`.

**Stale schema or weird data after a rebuild?**
- `docker compose down -v` drops the Postgres volumes so Flyway re-runs from scratch.

**Port conflicts?**
- Services occupy 8080, 8083–8094, 8761, 9411, 6379 and 5433–5438. Override a locally run service
  with `-Dserver.port=…`.

## Scripts

| Script | Purpose |
|--------|---------|
| `scripts/demo.sh` | Full happy-path trip lifecycle against a running stack. Prints every request/response, exits non-zero on the first unexpected status code. |
| `scripts/check-config-consistency.py` | Cross-checks `server.port` and `spring.application.name` against each Dockerfile, `docker-compose.yml` and the gateway route table. |

`server.port` and `spring.application.name` are each duplicated in four places, and drift between
them is silent — a service with the wrong name registers as `UNKNOWN` and callers only fail at
runtime. No Java test can see across all four files, so the consistency check runs in CI.

## Contributing

- Always update tests when modifying domain logic
- Follow the package structure (api, domain, repository, client)
- Use DTOs from `common` for cross-service communication
- Publish domain events to Kafka for async subscribers
- Ensure `mvn clean install` passes before pushing

## References

- **Paper:** *Designing UberLite: a Ride Aggregator Service* (Prasaad & Vikström, UW CSE552, Fall 2019)
- **Architecture spec:** See `ARCHITECTURE.md`
- **Spring Cloud docs:** https://spring.io/cloud
- **Kafka Java client:** https://kafka.apache.org/documentation/#api
- **Spring Data JPA:** https://spring.io/projects/spring-data-jpa
