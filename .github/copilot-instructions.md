# Copilot instructions — UberLite MVP

Read `ARCHITECTURE.md` at the repo root before starting any issue. It is the source of truth for
service boundaries, API contracts, data models, and tech choices. If an issue conflicts with it,
the issue's acceptance criteria win for that specific task, but flag the conflict in the PR
description rather than silently resolving it.

## Stack & conventions

- Java 25, Spring Boot 4.x, Maven multi-module build (root `pom.xml` with <modules> listing every
  service module).
- Each service is independently runnable (`mvn -pl trip-service spring-boot:run`) and independently
  testable (`mvn -pl trip-service test`).
- Package root per service: `com.uberlite.<servicename>` (e.g. `com.uberlite.tripservice`).
- REST controllers under `com.uberlite.<servicename>.api`, domain logic under `.domain`,
  persistence under `.repository`, Feign clients for calling other services under `.client`.
- DTOs shared between services live in `common` — never duplicate a DTO in a service module.
- Every new endpoint gets: a controller method, a request/response DTO in `common` if
  cross-service, a unit test for the domain logic, and a `@SpringBootTest` slice test for the
  controller. Don't skip tests to save time.
- Config via `application.yml`, one per module, profile `docker` for the Compose environment.
- Every service exposes `/actuator/health` (Actuator is already a dependency in the parent build).
- Kafka topic names and event payload schemas are defined once in `common` and imported —
  never inline a topic name string in a service.

## What "done" means for an issue

- Code compiles and `mvn clean install` passes for the whole repo, not just the touched module.
- New/changed endpoints match the request/response shapes in `ARCHITECTURE.md` or the issue body
  exactly — don't rename fields or change status codes without calling it out in the PR.
- Tests included and passing.
- `docker-compose.yml` updated if a new service/dependency was introduced, and the service boots
  cleanly with `docker-compose up <service>`.
- README section added/updated for the module if the issue introduces a new module.

## Explicitly do NOT do

- Don't introduce a new datastore, messaging system, or library not listed in
  `ARCHITECTURE.md` §5 without flagging it in the PR description first.
- Don't implement real ML for Forecasting/Pricing/Matching — use the rule-based approach the
  issue specifies. There will be a follow-up issue for that later.
- Don't add authentication/authorization — out of scope until explicitly issued.
- Don't collapse two services into one module for "simplicity" — the whole point of this repo is
  the service decomposition from the paper.