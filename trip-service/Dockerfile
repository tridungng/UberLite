# Multi-stage Dockerfile for trip-service
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY trip-service/pom.xml ./trip-service/pom.xml
COPY common/pom.xml ./common/pom.xml
COPY trip-service/src ./trip-service/src
COPY common/src ./common/src
RUN mvn -B -f ./pom.xml -pl trip-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/trip-service/target/trip-service-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java","-jar","/app/app.jar"]
