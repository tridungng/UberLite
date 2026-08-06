# UberLite

Minimal scaffold for the UberLite MVP. Requires JDK 27 and Spring Boot 4.x.

Running locally

1. Build:

   mvn clean install

2. Start core infra with Docker Compose:

   docker-compose up discovery-server api-gateway zookeeper kafka redis trip-service trip-service-postgres

3. Services

- discovery-server: http://localhost:8761 (Eureka dashboard)
- api-gateway: http://localhost:8080 (API Gateway)
- trip-service: http://localhost:8083

Each module has a `docker` profile in `application.yml` that points at the Compose service names.
