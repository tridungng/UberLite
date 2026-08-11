# Multi-stage build for route-service.
#
# Generated from one template so all 14 images stay identical in shape - see README, "Docker
# images". The whole repo is copied into the build stage because the root pom declares every
# module: Maven's reactor needs them all present even when building a single module with -pl/-am.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -f pom.xml -pl route-service -am -DskipTests clean package

FROM eclipse-temurin:25-jre

# curl is the probe used by HEALTHCHECK below. Without it the check can never pass, every service
# sits in "starting" forever, and `depends_on: condition: service_healthy` deadlocks the stack.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Unprivileged runtime user: nothing here needs root.
RUN useradd --system --create-home --uid 10001 uberlite

WORKDIR /app
COPY --from=build --chown=uberlite:uberlite /workspace/route-service/target/route-service-*.jar app.jar
USER uberlite

EXPOSE 8087

# The composite /actuator/health, not /actuator/health/readiness: the default readiness group holds
# only ApplicationAvailability, which flips UP the moment the context refreshes. The composite also
# aggregates the db, redis, kafka and discoveryComposite indicators, so
# `depends_on: condition: service_healthy` waits for a dependency that is genuinely callable -
# registered with Eureka and connected to its datastore. start-period covers JVM boot + Flyway
# migrations without counting them as failures.
HEALTHCHECK --interval=10s --timeout=3s --start-period=120s --retries=12 \
    CMD curl -fsS http://localhost:8087/actuator/health || exit 1

# JAVA_OPTS is honoured so heap/GC/JFR flags can be set per environment from docker-compose.yml
# without rebuilding the image. `exec` keeps the JVM as PID 1 so it receives SIGTERM on shutdown.
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
