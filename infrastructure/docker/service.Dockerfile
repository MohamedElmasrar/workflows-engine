# syntax=docker/dockerfile:1.7
# Multi-stage build shared by all Spring Boot services: docker build --build-arg MODULE=services/workflow-service .
ARG MODULE

FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /src
# 1) poms only, so dependency resolution is cached until a pom changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY packages/contracts/pom.xml packages/contracts/
COPY packages/platform-support/pom.xml packages/platform-support/
COPY services/api-gateway/pom.xml services/api-gateway/
COPY services/workflow-service/pom.xml services/workflow-service/
COPY services/execution-engine/pom.xml services/execution-engine/
COPY services/worker-service/pom.xml services/worker-service/
COPY services/trigger-service/pom.xml services/trigger-service/
# 2) sources
COPY packages packages
COPY services services
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -q -pl ${MODULE} -am package -DskipTests \
    && cp ${MODULE}/target/*-SNAPSHOT.jar /app.jar \
    && java -Djarmode=tools -jar /app.jar extract --layers --launcher --destination /layers

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app -u 10001 app
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER 10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    LOG_FORMAT=ecs
ARG PORT=8080
ENV PORT=${PORT}
EXPOSE ${PORT}
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
    CMD wget -qO- "http://localhost:${PORT}/actuator/health/readiness" | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
