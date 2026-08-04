# UberLite

Minimal scaffold for the UberLite MVP.

Running locally

1. Build:

   mvn clean install

2. Start core infra with Docker Compose:

   docker-compose up discovery-server api-gateway zookeeper kafka redis

3. Services

- discovery-server: http://localhost:8761 (Eureka dashboard)
- api-gateway: http://localhost:8080 (API Gateway)

Each module has a `docker` profile in `application.yml` that points at the Compose service names.
