# discovery-server

Netflix Eureka service registry (ARCHITECTURE.md §5). Every other JVM in the stack registers here;
`@FeignClient(name = …)` and the gateway's `lb://…` URIs resolve against it.

| | |
|---|---|
| Port | 8761 |
| Eureka id | `discovery-server` |
| State | none (in-memory registry) |
| Clients | 13 — the API gateway and the 12 application services |

Dashboard: http://localhost:8761

## Configuration

The server does not register with or fetch from itself (`register-with-eureka: false`,
`fetch-registry: false`) — it is a single node, so there is no peer to replicate to.

Self-preservation is left at its default. In a laptop-scale Compose stack a service really is gone
when its heartbeats stop, and holding on to dead instances would make Feign hand out addresses that
no longer answer.

## Boot order

This is the first service Compose starts; everything else declares `depends_on:
condition: service_healthy` against it. A client that starts before the registry does still recovers
(the Eureka client retries), but the first few calls fail with "No instances available", which makes
`scripts/demo.sh` flaky for no reason.

## Run

```bash
./mvnw -pl discovery-server spring-boot:run
```

