package com.cti.data.intelligence;

import java.math.BigDecimal;

public record DerivativesMetricRow(
        String symbol,
        String metricCode,
        String intervalCode,
        long eventTimeMs,
        BigDecimal value,
        String dataStatus
) {
}
