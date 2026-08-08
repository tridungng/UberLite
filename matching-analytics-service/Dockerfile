FROM maven:3.10.1-eclipse-temurin-27 AS builder
WORKDIR /workspace
COPY . .
RUN mvn -f pom.xml -pl matching-analytics-service -am -DskipTests package

FROM eclipse-temurin:27-jre
COPY --from=builder /workspace/matching-analytics-service/target/matching-analytics-service-0.0.1-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
