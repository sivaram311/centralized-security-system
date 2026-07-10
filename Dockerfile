# Centralized Security System — JDK 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN mkdir -p /data \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /build/target/centralized-security-system-0.1.0-SNAPSHOT.jar /app/app.jar
EXPOSE 9000
ENV SPRING_DATASOURCE_URL=jdbc:h2:file:/data/css;DB_CLOSE_DELAY=-1;MODE=PostgreSQL \
    SPRING_DATASOURCE_DRIVERCLASSNAME=org.h2.Driver \
    SPRING_DATASOURCE_USERNAME=sa \
    SPRING_DATASOURCE_PASSWORD= \
    SPRING_H2_CONSOLE_ENABLED=false
VOLUME ["/data"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
