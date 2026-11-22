# Multi-stage build for the TechStore Spring Boot API (Java 21).
# Docker build context: apps/api
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./pom.xml
RUN mvn -B -ntp -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests clean package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system techstore && useradd --system --gid techstore --uid 10001 techstore
WORKDIR /app
COPY --from=build /build/target/techstore-api-0.1.0-SNAPSHOT.jar /app/app.jar
USER techstore
EXPOSE 8080
# Managed hosts typically inject PORT; local Docker defaults to 8080.
ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
