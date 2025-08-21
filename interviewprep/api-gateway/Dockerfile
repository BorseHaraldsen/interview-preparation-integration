# Dockerfile for API Gateway
# NAV Integration Platform - Spring Cloud Gateway
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
# Gateway needs more memory for routing and circuit breaker state
ENV JAVA_OPTS="-Xmx512m -Xms256m -Djava.awt.headless=true -Duser.timezone=Europe/Oslo"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar target/*.jar"]

# Labels for documentation
LABEL maintainer="NAV Integration Team"
LABEL description="NAV Integration Platform API Gateway"
LABEL version="1.0.0"
LABEL component="gateway"