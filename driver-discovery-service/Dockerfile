FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY driver-discovery-service/pom.xml ./driver-discovery-service/pom.xml
COPY common/pom.xml ./common/pom.xml
COPY driver-discovery-service/src ./driver-discovery-service/src
COPY common/src ./common/src
RUN mvn -B -f ./pom.xml -pl driver-discovery-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/driver-discovery-service/target/driver-discovery-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
