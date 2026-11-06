package com.cti.strategy.config;

import com.cti.feature.config.QuantProperties;
import com.cti.strategy.ScoreClient;
import com.cti.strategy.http.HttpScoreClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class StrategyConfig {

    @Bean
    public ScoreClient scoreClient(QuantProperties properties, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .build();
        return new HttpScoreClient(properties, objectMapper, httpClient);
    }
}
