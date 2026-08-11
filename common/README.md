# common

Shared library. Not a service — it has no `main`, no port and no Eureka registration.

Everything here exists because it must be **identical** in more than one module. Anything used by
exactly one service belongs in that service.

## Contents

| Package | What | Why it is shared |
|---|---|---|
| `dto` | Cross-service request/response types | A type produced by one service and consumed by another is the *same class* on both sides, so a renamed field breaks the build instead of the demo |
| `events` | `TripEvent`, `TripState`, `Topics`, `TripEventPayloadKeys` | Topic names and payload keys are defined once and imported — never inlined in a service (ARCHITECTURE.md §3) |
| `events.kafka` | `TripEventConsumerConfiguration` | Every `trip-events` consumer needs the same deserializer and trusted-package setup |
| `geo` | `H3Util` | Map Indexing is a pure function, so it ships as a library rather than a network hop (ARCHITECTURE.md §2) |

## `uberlite-defaults.yml`

`src/main/resources/uberlite-defaults.yml` holds the Actuator exposure, tracing and Eureka
client/instance policy for all 14 services. Each module pulls it in with:

```yaml
spring:
  config:
    import: "optional:classpath:uberlite-defaults.yml"
```

An imported document has **lower** precedence than the file importing it, so a service can still
override any key locally. Don't copy management/tracing/eureka blocks back into a service — the
point is that "consistent across all 14 services" stays true without anyone maintaining it.

## Test-jar

`src/test/java/…/testing/StubServer` is published as a test-jar and used by the services that call
other services over Feign:

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

**WireMock is deliberately not used**; it cannot run on this classpath. The full rationale is on
`StubServer` itself. Don't re-add the dependency.

## Build

```bash
./mvnw -pl common test
```

`common` is first in the reactor, so a change here rebuilds everything: `./mvnw clean install`.

