# Dockerfile for NAV Integration Demo
# Bruker multi-stage build for optimalisering

# Build stage
FROM openjdk:17-jdk-slim as builder

# Sett working directory
WORKDIR /app

# Kopier Maven wrapper og pom.xml først (for Docker layer caching)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Last ned dependencies (cached layer hvis pom.xml ikke endres)
RUN ./mvnw dependency:go-offline -B

# Kopier kildekode
COPY src src

# Bygg applikasjonen (skip tests for raskere build)
RUN ./mvnw clean package -DskipTests -B

# Runtime stage
FROM openjdk:17-jre-slim

# Metadata
LABEL maintainer="NAV Integration Team"
LABEL version="1.0.0"
LABEL description="NAV Integration Demo Application"

# Installer curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Opprett non-root bruker for sikkerhet
RUN groupadd -r navuser && useradd -r -g navuser navuser

# Sett working directory
WORKDIR /app

# Kopier JAR fra build stage
COPY --from=builder /app/target/*.jar app.jar

# Endre eierskap til navuser
RUN chown navuser:navuser app.jar

# Eksporter port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/nav-integration/actuator/health || exit 1

# Bytt til non-root bruker
USER navuser

# JVM tuning for containers
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:+UseContainerSupport"

# Kjør applikasjonen
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]