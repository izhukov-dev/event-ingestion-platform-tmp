# Stage 1: Dependency extraction using Spring Boot jarmode
FROM eclipse-temurin:21-jre-alpine AS builder
WORKDIR /workspace
ARG JAR_FILE=ingestion-service/build/libs/ingestion-service.jar
COPY ${JAR_FILE} app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# Stage 2: Hardened Runtime
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache dumb-init \
    && addgroup -g 10001 -S appgroup \
    && adduser -u 10001 -S appuser -G appgroup

WORKDIR /app
USER 10001:10001

COPY --from=builder --chown=10001:10001 /workspace/extracted/dependencies/ ./
COPY --from=builder --chown=10001:10001 /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=10001:10001 /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=10001:10001 /workspace/extracted/application/ ./

EXPOSE 8081

ENTRYPOINT ["dumb-init", "--", "java", \
  "-XX:+UseContainerSupport", \
  "-XX:+UseSerialGC", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-XX:MaxRAMPercentage=70.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
