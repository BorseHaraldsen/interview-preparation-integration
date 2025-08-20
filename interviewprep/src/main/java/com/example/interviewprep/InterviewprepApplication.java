package com.example.interviewprep;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Main application class for NAV Integration Platform.
 * 
 * Configures Spring Boot application with event-driven architecture support
 * and enterprise integration capabilities for government service delivery.
 * 
 * Key features:
 * - Event-driven messaging with Kafka
 * - RESTful API endpoints for service integration
 * - Database integration with JPA/Hibernate
 * - External system connectivity patterns
 */
@SpringBootApplication
@EnableKafka
public class InterviewprepApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewprepApplication.class, args);
        System.out.println("NAV Integration Platform started successfully");
        System.out.println("API endpoints: http://localhost:8080/interviewprep");
        System.out.println("H2 Database Console: http://localhost:8080/interviewprep/h2-console");
        System.out.println("Health Check: http://localhost:8080/interviewprep/actuator/health");
    }
}