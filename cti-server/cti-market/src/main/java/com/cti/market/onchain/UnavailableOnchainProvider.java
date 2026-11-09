package com.cti.market.onchain;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UnavailableOnchainProvider implements OnchainDataProvider {

    @Override
    public String providerCode() {
        return "NONE";
    }

    @Override
    public boolean configured() {
        return false;
    }

    @Override
    public List<OnchainMetric> fetchMetrics(String asset, long startMs, long endMs) {
        return List.of();
    }
}
