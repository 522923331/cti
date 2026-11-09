package com.cti.market.model;

import java.math.BigDecimal;

public record FuturesPoint(
        String symbol,
        String metricCode,
        String intervalCode,
        long eventTimeMs,
        BigDecimal value,
        String dataStatus,
        String rawJson
) {
}
