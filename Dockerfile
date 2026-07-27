# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the application with Maven ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so they are cached when only source code changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Integration tests need a Docker daemon (Testcontainers), which is not available inside the
# image build. Tests are run in CI / locally; the image build only packages the artifact.
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage: slim JRE with just the fat jar ----
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Run as an unprivileged user.
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /build/target/coupon-service-*.jar app.jar
RUN chown app:app app.jar
USER app

EXPOSE 8080

# Fail fast if the JVM cannot start; container orchestrators use the actuator health endpoint.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
