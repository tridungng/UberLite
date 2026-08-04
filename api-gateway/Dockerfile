# Multi-stage Dockerfile for api-gateway
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY api-gateway/pom.xml ./api-gateway/pom.xml
COPY common/pom.xml ./common/pom.xml
COPY api-gateway/src ./api-gateway/src
COPY common/src ./common/src
RUN mvn -B -f ./pom.xml -pl api-gateway -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/api-gateway/target/api-gateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
