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

```bash
docker compose up -d discovery-server zipkin redis
./mvnw -pl driver-discovery-service spring-boot:run
./mvnw -pl driver-discovery-service test
```

The integration test needs Docker (Testcontainers Redis) and self-skips when it is unavailable.

