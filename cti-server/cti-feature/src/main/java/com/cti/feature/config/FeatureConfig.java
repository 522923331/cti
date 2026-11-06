package com.cti.feature.config;

import com.cti.feature.QuantFeatureClient;
import com.cti.feature.http.HttpQuantFeatureClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(QuantProperties.class)
public class FeatureConfig {

    @Bean
    public QuantFeatureClient quantFeatureClient(QuantProperties properties, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .build();
        return new HttpQuantFeatureClient(properties, objectMapper, httpClient);
    }
}
