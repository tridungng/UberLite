# UberLite MVP

Minimal scaffold for the UberLite MVP implementing the *Designing UberLite: a Ride Aggregator Service* paper (Prasaad & Vikström, UW CSE552, Fall 2019).

**Stack:** Java 27, Spring Boot 4.x, Maven 3.9+, Docker Compose

## Architecture Overview

See `ARCHITECTURE.md` for the complete service decomposition, data models, and API contracts. UberLite is a **microservices** project with:

- **15 services** (8 core marketplace, 4 background analytics, 3 infrastructure)
- **Postgres** for trip state machine and static reference data
- **Redis** for driver locations and surge multipliers (real-time, low latency)
- **Kafka** for domain event triggers (trip state transitions propagate to analytics services)
- **Eureka** for service discovery
- **Spring Cloud Gateway** as API entry point

## Quick Start

### Prerequisites
- JDK 27+
- Maven 3.9+
- Docker & Docker Compose

### 1. Build all services
```bash
mvn clean install
```

### 2. Start infrastructure and core services
```bash
docker-compose up discovery-server api-gateway zookeeper kafka redis trip-service-postgres trip-service
```

### 3. Access the system
- **Eureka dashboard:** http://localhost:8761 (view all registered services)
- **API Gateway:** http://localhost:8080 (entry point for rider/driver apps)
- **Trip Service health:** http://localhost:8083/actuator/health

### 4. Run individual services (for development)
Each service can be run standalone for testing:
```bash
# Terminal 1: Run Route Service
mvn -pl route-service spring-boot:run

# Terminal 2: Run Price Estimation Service (depends on Route Service, etc.)
mvn -pl price-estimation-service spring-boot:run
```

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

| Service | Purpose | State |
|---------|---------|-------|
| **Forecasting Service** | Demand forecast per H3 cell/hour | ✅ Implemented |
| **Matching Analytics Service** | Kafka consumer, log match events | ✅ Implemented |
| **Discounts Analytics Service** | Kafka consumer, nightly promo batch | ✅ Implemented |

### Infrastructure Services

| Service | Purpose |
|---------|---------|
| **Discovery Server (Eureka)** | Service registry (port 8761) |
| **API Gateway** | Single entry point (port 8080) |
| **Common** | Shared DTOs, H3 utilities, Kafka event schemas |

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
5. Trip Service → Matching Service (find driver)
   5a. Matching → Driver Discovery Service (nearby drivers)
6. Trip Service publishes Kafka event (state → DRIVER_PROPOSED)
7. Matching Analytics Service (Kafka consumer) logs the match
8. Discounts Analytics Service (Kafka consumer) checks for promo eligibility
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

### application.yml (common to all services)

```yaml
server:
  port: 8XXX                          # Unique per service

spring:
  application:
    name: <service-name>              # Eureka registration
  jpa:
    hibernate:
      ddl-auto: validate              # Create tables if needed
  datasource:
    url: jdbc:postgresql://localhost:5432/<db>
    username: uberlite
    password: changeme
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
  instance:
    preferIpAddress: true
```

### Docker Profile

Each service has a `docker` Spring profile that overrides localhost URLs:

```yaml
---
spring:
  config:
    activate:
      on-profile: docker
  datasource:
    url: jdbc:postgresql://trip-service-postgres:5432/tripdb  # Docker service name
  kafka:
    bootstrap-servers: kafka:9092

eureka:
  client:
    serviceUrl:
      defaultZone: http://discovery-server:8761/eureka/
```

Run with `docker-compose up` or manually set `SPRING_PROFILES_ACTIVE=docker`.

## Testing

### Unit tests
```bash
mvn -pl <service> test
```

### Integration tests (requires Docker Compose running)
```bash
docker-compose up kafka zookeeper redis discovery-server trip-service-postgres
mvn -pl <service> -Dgroups=integration test
```

### Full build + tests
```bash
mvn clean install
```

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
Each service has a `Dockerfile` using multi-stage build:
1. Build stage: Compile with Maven in `openjdk:27-slim`
2. Runtime stage: Copy JAR, run with minimal JRE

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

All services expose Spring Boot Actuator endpoints:
```
GET /actuator/health              # Service health
GET /actuator/prometheus          # Metrics (if Micrometer configured)
GET /actuator/loggers             # Runtime log level adjustment
```

See `ARCHITECTURE.md` §5 for stretch goal (Zipkin tracing).

## Troubleshooting

**Service not registering with Eureka?**
- Check `eureka.client.serviceUrl.defaultZone` in application.yml
- Ensure Eureka server is running on :8761

**Kafka producer/consumer failing?**
- Ensure Kafka broker is running on :9092 (or configured host)
- Check topic name spelling (trip-events is hardcoded in multiple places)

**Database connection refused?**
- Check Postgres service name and port in datasource URL (localhost:5432 locally, docker-compose service name in Docker)
- Verify credentials match POSTGRES_USER/POSTGRES_PASSWORD in docker-compose.yml

**Port conflicts?**
- Each service has a unique port (8083–8094). Check docker-compose.yml if running multiple.
- If running multiple locally, override with `-Dserver.port=8XXX` in mvn command.

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
