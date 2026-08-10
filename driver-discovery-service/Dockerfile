# Multi-stage Dockerfile for driver-discovery-service.
# Copies the whole repo: the root pom declares every module, so Maven's reactor needs all of them
# present even when building a single module with -pl/-am.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -f ./pom.xml -pl driver-discovery-service -am clean package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/driver-discovery-service/target/driver-discovery-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
