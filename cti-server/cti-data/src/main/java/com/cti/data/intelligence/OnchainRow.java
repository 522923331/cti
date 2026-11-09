package com.cti.data.intelligence;

import java.math.BigDecimal;

public record OnchainRow(
        String assetCode,
        String metricCode,
        String intervalCode,
        long eventTimeMs,
        BigDecimal value,
        String unit,
        String providerCode,
        String dataStatus,
        boolean delayed
) {
}
