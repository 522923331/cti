package com.cti.market.binance;

import com.cti.market.SpotMarketClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BinanceMarketProperties.class)
public class MarketConfig {

    @Bean
    public SpotMarketClient spotMarketClient(BinanceMarketProperties properties, ObjectMapper objectMapper) {
        return new BinanceSpotClient(properties, objectMapper, duration -> {
            try {
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        });
    }
}
