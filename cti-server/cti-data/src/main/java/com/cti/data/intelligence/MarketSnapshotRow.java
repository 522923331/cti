package com.cti.data.intelligence;

import java.math.BigDecimal;

public record MarketSnapshotRow(
        String metricCode,
        long snapshotTimeMs,
        BigDecimal value,
        String unit,
        String providerCode,
        String dataStatus
) {
}
