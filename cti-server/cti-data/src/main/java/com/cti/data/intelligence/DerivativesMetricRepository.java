package com.cti.data.intelligence;

import com.cti.market.model.FuturesPoint;

import java.util.List;

public interface DerivativesMetricRepository {

    int upsertAll(List<FuturesPoint> points, long collectTimeMs, String providerCode);

    int count(String symbol);

    List<DerivativesMetricRow> latest(String symbol, int limit);
}
