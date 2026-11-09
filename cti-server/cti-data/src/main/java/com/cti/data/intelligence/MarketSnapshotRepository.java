package com.cti.data.intelligence;

import java.math.BigDecimal;
import java.util.List;

public interface MarketSnapshotRepository {

    void upsert(String metricCode, long snapshotTimeMs, BigDecimal value, String unit, String providerCode, String dataStatus);

    List<MarketSnapshotRow> latest();
}
