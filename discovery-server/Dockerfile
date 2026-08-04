# Multi-stage Dockerfile for discovery-server
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# copy root pom and module sources to enable module build
COPY pom.xml ./
COPY discovery-server/pom.xml ./discovery-server/pom.xml
COPY common/pom.xml ./common/pom.xml
COPY discovery-server/src ./discovery-server/src
COPY common/src ./common/src
RUN mvn -B -f ./pom.xml -pl discovery-server -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/discovery-server/target/discovery-server-*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java","-jar","/app/app.jar"]
