# Surge Pricing Service

Redis-backed service for computing dynamic surge pricing multipliers per H3 geospatial cell.

## Overview

Part of the UberLite MVP. Implements Sec. 4.1 "Surge Pricing Service (SPS)" from the paper.

## Endpoints

### GET `/surge/{h3Cell}`
Returns the current surge multiplier for an H3 cell.

**Response:**
```json
{
  "h3Cell": "881f97e42c1ffff",
  "multiplier": 1.5,
  "updatedAtMs": 1691234567890
}
```

**Logic:**
- Checks Redis cache (15s TTL) first
- If miss: queries active drivers from driver-discovery-service
- Computes: `multiplier = clamp(pending_requests / max(active_drivers, 1), 1.0, 3.0)`
- Caches result in Redis

**Fallback:** If driver-discovery-service is unreachable, treats active drivers as 1 (no surge) rather than failing.

### POST `/surge/{h3Cell}/pending-request`
Increments pending request counter for a cell (called by Trip Service when trip enters matching pipeline).

**Response:** HTTP 200

### DELETE `/surge/{h3Cell}/pending-request`
Decrements pending request counter for a cell (called by Trip Service when trip leaves matching pipeline).

**Response:** HTTP 200

## Redis Schema

```
surge:pending:<h3Cell>          → integer count of pending requests
surge:<h3Cell>:multiplier       → float surge multiplier (TTL: 15s)
surge:<h3Cell>:multiplier:ts    → timestamp of computation (TTL: 15s)
```

Keys are namespaced `surge:` to avoid collisions with driver-discovery-service (`drivers:*`).

## Configuration

Default port: `8084`

**application.yml profiles:**
- `docker`: Uses Redis service name `redis` and Eureka at `discovery-server:8761`
- `local`: Uses localhost

## Building & Testing

```bash
mvn clean install                               # Build + run all tests
mvn test -pl surge-pricing-service              # Just tests
mvn spring-boot:run -pl surge-pricing-service   # Run locally
```

## Test Coverage

- **Unit tests** (SurgeMultiplierServiceTest): Clamp logic, no external deps
- **Service tests** (SurgeComputationServiceTest): E2E with mocked Redis and driver-discovery
  - Surge scaling with demand
  - Caching behavior
  - Fallback when driver-discovery unavailable

All tests: **17 passing**
