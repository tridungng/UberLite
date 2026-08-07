FROM maven:3.10.1-eclipse-temurin-25 AS builder
WORKDIR /workspace
COPY . .
RUN mvn -f pom.xml -pl route-service -am -DskipTests package

FROM eclipse-temurin:25-jre
COPY --from=builder /workspace/route-service/target/route-service-0.0.1-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
