# Multi-stage Dockerfile for api-gateway.
# Copies the whole repo: the root pom declares every module, so Maven's reactor needs all of them
# present even when building a single module with -pl/-am.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -f ./pom.xml -pl api-gateway -am clean package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/api-gateway/target/api-gateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
