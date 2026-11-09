package com.cti.market.onchain;

import java.math.BigDecimal;

public record OnchainMetric(
        String asset,
        String metricCode,
        String intervalCode,
        long eventTimeMs,
        BigDecimal value,
        String unit,
        String dataStatus,
        boolean delayed
) {
}
