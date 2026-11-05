# ==============================================================================
# Stage 1: Build & Extracao de Camadas Spring Boot (Maven + JDK 21)
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    apk add --no-cache maven && \
    mvn dependency:go-offline -B

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -B && \
    java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ==============================================================================
# Stage 2: Custom Minimal JRE via jlink (Imagem Enxuta e Endurecida)
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS jre-builder
RUN apk add --no-cache binutils && \
    $JAVA_HOME/bin/jlink \
      --add-modules java.base,java.logging,java.naming,java.desktop,java.management,java.security.jgss,java.instrument,java.sql,jdk.unsupported \
      --strip-debug \
      --no-man-pages \
      --no-header-files \
      --compress=zip-6 \
      --output /custom-jre

# ==============================================================================
# Stage 3: Runtime de Producao (Alpine + Non-Root + Layered Boot)
# ==============================================================================
FROM alpine:3.20
ENV JAVA_HOME=/opt/jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN addgroup -S keyfalls && adduser -S keyfalls -G keyfalls && \
    apk add --no-cache tzdata curl

COPY --from=jre-builder /custom-jre $JAVA_HOME

WORKDIR /app
COPY --from=builder --chown=keyfalls:keyfalls /workspace/target/extracted/dependencies/ ./
COPY --from=builder --chown=keyfalls:keyfalls /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=keyfalls:keyfalls /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=keyfalls:keyfalls /workspace/target/extracted/application/ ./

USER keyfalls:keyfalls
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=25s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseZGC", \
  "-XX:+ZGenerational", \
  "org.springframework.boot.loader.launch.JarLauncher"]
