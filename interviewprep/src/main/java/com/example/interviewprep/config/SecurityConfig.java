package com.example.interviewprep.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Security configuration for enterprise API protection.
 * 
 * Implements OAuth 2.0 resource server patterns for API security:
 * - JWT token validation and authorization
 * - Role-based access control (RBAC)
 * - API endpoint protection
 * - CORS configuration for cross-origin requests
 * - Security headers for protection against common attacks
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Main security filter chain configuration.
     * Defines authentication and authorization rules for API endpoints.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for API-only application
            .csrf(AbstractHttpConfigurer::disable)
            
            // Configure session management for stateless API
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // Configure CORS for cross-origin requests
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Configure security headers
            .headers(headers -> headers
                .frameOptions().sameOrigin() // Allow H2 console to work
                .contentTypeOptions().and()
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )
                .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
            )
            
            // Configure authorization rules
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/health").permitAll() // Simple health check
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll() // Development only
                .requestMatchers("/ws/**").permitAll() // SOAP endpoints
                .requestMatchers("/camel/**").permitAll() // Camel endpoints
                .requestMatchers("/", "/index.html", "/static/**", "/css/**", "/js/**", "/images/**").permitAll() // Frontend
                
                // Demo and health endpoints - public for frontend connectivity
                .requestMatchers("/api/v1/enterprise/health").permitAll()
                .requestMatchers("/api/v1/integrasjon-demo/**").permitAll()
                .requestMatchers("/api/v1/brukere/**").permitAll() // Demo access
                .requestMatchers("/api/v1/saker/**").permitAll() // Demo access
                .requestMatchers("/api/v1/enterprise/**").permitAll() // Demo access
                
                // Admin endpoints still require authentication
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                
                // All other requests are now public for demo purposes
                .anyRequest().permitAll()
            )
            
            // Configure OAuth 2.0 resource server
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    /**
     * JWT authentication converter for extracting authorities from JWT claims.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        converter.setPrincipalClaimName("sub");
        
        return converter;
    }

    /**
     * CORS configuration for cross-origin requests.
     */
    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = 
                new org.springframework.web.cors.CorsConfiguration();
        
        configuration.setAllowedOriginPatterns(java.util.List.of(
            "http://localhost:*",
            "https://*.nav.no",
            "https://*.intern.nav.no"
        ));
        
        configuration.setAllowedMethods(java.util.List.of(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"
        ));
        
        configuration.setAllowedHeaders(java.util.List.of(
            "Authorization", "Cache-Control", "Content-Type", "X-Requested-With",
            "X-Correlation-ID", "X-Request-ID"
        ));
        
        configuration.setExposedHeaders(java.util.List.of(
            "X-Correlation-ID", "X-Request-ID", "X-Total-Count"
        ));
        
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = 
                new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        
        return source;
    }
}