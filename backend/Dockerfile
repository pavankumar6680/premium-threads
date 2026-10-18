# syntax=docker/dockerfile:1

# ---- build: compile and package (tests run in CI / locally, where they start an embedded PostgreSQL) ----
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Dependencies get their own layer and a BuildKit cache, so code changes rebuild quickly.
RUN --mount=type=cache,target=/root/.m2 chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && cp target/backend-*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- runtime: JRE only, non-root ----
FROM eclipse-temurin:21-jre-noble
RUN groupadd --system spring && useradd --system --gid spring --uid 1001 --no-create-home spring \
    && apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
# Spring Boot layers, least to most frequently changed, become separate image layers
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
# uploaded product images (a volume in docker-compose); the rest of the filesystem is read-only
RUN mkdir -p /app/media && chown spring:spring /app/media
USER spring
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
