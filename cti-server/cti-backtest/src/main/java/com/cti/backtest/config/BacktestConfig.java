package com.cti.backtest.config;

import com.cti.backtest.BacktestClient;
import com.cti.backtest.http.HttpBacktestClient;
import com.cti.feature.config.QuantProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class BacktestConfig {

    @Bean
    public BacktestClient backtestClient(QuantProperties properties, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .build();
        return new HttpBacktestClient(properties, objectMapper, httpClient);
    }
}
