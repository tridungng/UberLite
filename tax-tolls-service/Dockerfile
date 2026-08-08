FROM maven:3.10.1-eclipse-temurin-27 AS builder
WORKDIR /workspace
COPY . .
RUN mvn -f pom.xml -pl tax-tolls-service -am -DskipTests package

FROM eclipse-temurin:27-jre
COPY --from=builder /workspace/tax-tolls-service/target/tax-tolls-service-0.0.1-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
