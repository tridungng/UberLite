FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /workspace
COPY . .
RUN mvn -f pom.xml -pl price-estimation-service -am -DskipTests package

FROM eclipse-temurin:25-jre
COPY --from=builder /workspace/price-estimation-service/target/price-estimation-service-0.0.1-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
