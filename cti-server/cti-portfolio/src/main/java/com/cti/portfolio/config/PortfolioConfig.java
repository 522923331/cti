package com.cti.portfolio.config;

import com.cti.feature.config.QuantProperties;
import com.cti.portfolio.PortfolioClient;
import com.cti.portfolio.http.HttpPortfolioClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class PortfolioConfig {

    @Bean
    public PortfolioClient portfolioClient(QuantProperties properties, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .build();
        return new HttpPortfolioClient(properties, objectMapper, httpClient);
    }
}
