package com.example.interviewprep.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * Konfigurasjon for integrasjoner - OPPDATERT for Spring Boot 3.x
 */
@Configuration
@EnableRetry
public class IntegrationConfig {

    /**
     * RestTemplate bean for eksterne API-kall
     * Bruker requestFactory for timeout kontroll
     */
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(createRequestFactory(5000, 10000));
        return restTemplate;
    }

    /**
     * Spesialisert RestTemplate for kritiske systemer
     */
    @Bean("folkeregisterRestTemplate")
    public RestTemplate folkeregisterRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(createRequestFactory(3000, 8000));
        return restTemplate;
    }

    /**
     * RestTemplate for ikke-kritiske systemer
     */
    @Bean("standardRestTemplate")
    public RestTemplate standardRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(createRequestFactory(10000, 30000));
        return restTemplate;
    }

    /**
     * Lag request factory med timeout kontroll
     */
    private ClientHttpRequestFactory createRequestFactory(int connectTimeout, int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}