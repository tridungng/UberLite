# api-gateway

Spring Cloud Gateway — the single entry point for rider and driver clients (ARCHITECTURE.md §5).

| | |
|---|---|
| Port | 8080 |
| Eureka id | `api-gateway` |
| State | none |

## Routing

Routes live under `spring.cloud.gateway.server.webflux.routes` in `application.yml` and resolve
services through Eureka with `lb://<spring.application.name>`.

The property prefix matters: under the older `spring.cloud.gateway.routes` the routes parse as
nothing and the gateway starts happily with an **empty** route table, so every request 404s while
the service looks healthy. `ApiGatewayRoutesTest` guards this, and
`scripts/check-config-consistency.py` cross-checks each `lb://` id against the target's
`spring.application.name`.

## Aggregate health

```
GET /health/aggregate   → one document covering every Eureka-registered instance
                          200 only when all are UP, 503 otherwise
```

Instances are reported individually, so a partial outage ("3 of 4 up") is visible rather than
averaged away. `curl -f` against it is a sufficient CI gate.

This is deliberately **not** folded into the gateway's own `/actuator/health`: the gateway's health
must describe the gateway. If it reported the whole estate, Compose would restart a perfectly
healthy gateway because an unrelated analytics service was still booting.

## Run

The platform (`docker compose up -d` with no profile) is Eureka, the gateway, Kafka, Redis, Zipkin
and the databases. This service needs nothing on top of that, and declares no dependency on
any other UberLite service - it boots on its own, and a call to a peer that is not running fails
fast rather than blocking startup. See the root README, "Independent deployability".

```bash
docker compose up -d api-gateway          # in a container, with its dependencies
# or, running it from source against the containerised platform:
docker compose up -d
./mvnw -pl api-gateway spring-boot:run
./mvnw -pl api-gateway test
```

| Probe | Meaning |
|-------|---------|
| `/actuator/health/liveness` | what the container `HEALTHCHECK` polls; a failure means restart |
| `/actuator/health/readiness` | `readinessState` - safe to route traffic here |
| `/actuator/health` | composite, including peers - informational, a `DOWN` here can just mean a dependency is missing |

