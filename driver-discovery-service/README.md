# driver-discovery-service

Driver Discovery Service (DRS) — ARCHITECTURE.md §2. Where the drivers are, right now.

| | |
|---|---|
| Port | 8086 |
| Eureka id | `driver-discovery-service` |
| State | Redis (no Postgres) |
| Called by | `matching-service`, `surge-pricing-service` |

## API

```
POST   /drivers/{driverId}/location   body LocationDto {lat, lon}
POST   /drivers/{driverId}/status     body {status: ONLINE|BUSY|OFFLINE}
GET    /drivers/nearby?lat=&lon=&radiusMeters=[&limit=10]        → [DriverCandidateDto]
GET    /drivers/nearby-by-cell?h3Cell=&kRing=[&limit=10]         → [DriverCandidateDto]
```

Two neighbourhood queries on purpose: `nearby` is a metric radius (what Matching wants — "who can
reach this pickup"), `nearby-by-cell` is an H3 k-ring (what Surge Pricing wants — supply per cell,
on the same grid the multiplier is keyed by). Emulating one with the other would force a caller to
convert between a circle and a hex tiling.

## Storage (ARCHITECTURE.md §7)

```
GEOADD drivers:active <lon> <lat> <driverId>
HSET   driver:<driverId> h3Cell <cell> lastSeen <ts> status <ONLINE|BUSY>
```

Redis rather than Postgres because this is the paper's "Realtime / Severe staleness sensitivity"
data: it is overwritten every few seconds and has no value once stale.

## Eviction

`EvictionScheduler` drops drivers unseen for 2 minutes every 30s. A driver whose app was killed
stops sending locations but does not send a goodbye, so without eviction the geo-index slowly fills
with drivers who will never accept — and Matching would keep proposing them, burning the trip's
k=3 retry budget on ghosts.

## Run

The platform (`docker compose up -d` with no profile) is Eureka, the gateway, Kafka, Redis, Zipkin
and the databases. This service needs `redis` on top of that, which Compose starts for you when you name the service.
It declares no dependency on any other UberLite service, so it boots on its own and a call to a peer
that is not running fails fast rather than blocking startup. See the root README, "Independent deployability".

```bash
docker compose up -d driver-discovery-service          # in a container, with its dependencies
# or, running it from source against the containerised platform:
docker compose up -d
./mvnw -pl driver-discovery-service spring-boot:run
./mvnw -pl driver-discovery-service test
```

| Probe | Meaning |
|-------|---------|
| `/actuator/health/liveness` | what the container `HEALTHCHECK` polls; a failure means restart |
| `/actuator/health/readiness` | `readinessState` plus `redis` - safe to route traffic here |
| `/actuator/health` | composite, including peers - informational, a `DOWN` here can just mean a dependency is missing |

The integration test needs Docker (Testcontainers Redis) and self-skips when it is unavailable.

