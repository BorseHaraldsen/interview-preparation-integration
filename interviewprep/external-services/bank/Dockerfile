# Dockerfile for Bank API
# External service simulating banking integration with complex JSON structures
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

# Set JVM options for container environment with international locale
ENV JAVA_OPTS="-Xmx256m -Xms128m -Djava.awt.headless=true -Duser.timezone=UTC -Duser.country=US -Duser.language=en"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar target/*.jar"]

# Labels for documentation
LABEL maintainer="NAV Integration Team"
LABEL description="Bank API - Banking Integration External Service"
LABEL version="1.0.0"
LABEL data-format="complex-json"