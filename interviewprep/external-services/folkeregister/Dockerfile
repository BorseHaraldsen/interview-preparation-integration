# Dockerfile for Folkeregister API
# External service simulating Norwegian Population Registry
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY src/ src/

# Build application
RUN ./mvnw clean package -B -DskipTests

# Expose port
EXPOSE 8080

# Set JVM options for container environment
ENV JAVA_OPTS="-Xmx256m -Xms128m -Djava.awt.headless=true -Duser.timezone=Europe/Oslo"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar target/*.jar"]

# Labels for documentation
LABEL maintainer="NAV Integration Team"
LABEL description="Folkeregister API - Norwegian Population Registry External Service"
LABEL version="1.0.0"