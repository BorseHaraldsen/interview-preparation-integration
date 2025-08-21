# Dockerfile for A-Ordningen API
# External service simulating Norwegian Employment Registry with CSV responses
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

# Set JVM options for container environment with Norwegian locale
ENV JAVA_OPTS="-Xmx256m -Xms128m -Djava.awt.headless=true -Duser.timezone=Europe/Oslo -Duser.country=NO -Duser.language=no -Dfile.encoding=UTF-8"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar target/*.jar"]

# Labels for documentation
LABEL maintainer="NAV Integration Team"
LABEL description="A-Ordningen API - Norwegian Employment Registry External Service"
LABEL version="1.0.0"
LABEL data-format="csv-pipe-delimited"