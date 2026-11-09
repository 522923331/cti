package com.cti.market.onchain;

import java.util.List;

public interface OnchainDataProvider {

    String providerCode();

    boolean configured();

    List<OnchainMetric> fetchMetrics(String asset, long startMs, long endMs);
}
