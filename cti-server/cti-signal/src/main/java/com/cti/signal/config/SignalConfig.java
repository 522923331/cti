package com.cti.signal.config;

import com.cti.feature.config.QuantProperties;
import com.cti.signal.SignalClient;
import com.cti.signal.http.HttpSignalClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class SignalConfig {

    @Bean
    public SignalClient signalClient(QuantProperties properties, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .build();
        return new HttpSignalClient(properties, objectMapper, httpClient);
    }
}
