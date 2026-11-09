package com.cti.market.onchain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnchainSyncTest {

    @Test
    void anUnconfiguredProviderWritesNoNetflow() {
        AtomicInteger fetches = new AtomicInteger();
        OnchainDataProvider provider = new OnchainDataProvider() {
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
                fetches.incrementAndGet();
                return List.of();
            }
        };
        String result = new OnchainSync().sync(provider, (metrics, providerCode) -> {
            throw new AssertionError("未接入时不能写链上快照");
        });
        assertEquals(0, fetches.get());
        assertTrue(result.contains("onchain=UNAVAILABLE"));
        assertTrue(result.contains("rows=0"));
    }
}
