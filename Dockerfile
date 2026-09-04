# ---- Build stage ----
# Building inside the container (not shelling out to the host's Gradle) is
# also what sidesteps this dev machine's known JVM loopback-socket issue
# with the local Gradle daemon (see CLAUDE.md) -- a Linux container has no
# such problem.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew --version

COPY src ./src
# Tests need Testcontainers (Docker-in-Docker) and a real Postgres/Redis --
# not available in an image build context. CI runs the real test suite;
# this build only needs the jar.
RUN ./gradlew bootJar --no-daemon -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S fantasyiq && adduser -S fantasyiq -G fantasyiq
WORKDIR /app
COPY --from=build /app/build/libs/fantasyiq.jar app.jar
USER fantasyiq

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget --quiet --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
