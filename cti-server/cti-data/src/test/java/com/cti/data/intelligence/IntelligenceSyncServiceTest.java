package com.cti.data.intelligence;

import com.cti.data.store.AssetRepository;
import com.cti.data.store.AssetRow;
import com.cti.market.FuturesMarketClient;
import com.cti.market.model.FuturesPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntelligenceSyncServiceTest {

    @Test
    void aFailedSymbolDoesNotDropPointsAlreadyCollected() {
        List<String> saved = new ArrayList<>();
        FuturesMarketClient futures = new FuturesMarketClient() {
            @Override
            public long serverTime() {
                return 1_000;
            }

            @Override
            public List<FuturesPoint> collect(String symbol, long startTime, long serverTime) {
                if ("ETHUSDT".equals(symbol)) {
                    throw new IllegalStateException("timeout");
                }
                return List.of(new FuturesPoint(symbol, "FUNDING_RATE", "8h", 1, new BigDecimal("0.0001"), "CLOSED", "{}"));
            }
        };
        DerivativesMetricRepository metrics = new DerivativesMetricRepository() {
            @Override
            public int upsertAll(List<FuturesPoint> points, long collectTimeMs, String providerCode) {
                points.forEach(point -> saved.add(point.symbol()));
                return points.size();
            }

            @Override
            public int count(String symbol) {
                return 0;
            }

            @Override
            public List<DerivativesMetricRow> latest(String symbol, int limit) {
                return List.of();
            }
        };
        AssetRepository assets = new AssetRepository() {
            @Override
            public List<AssetRow> enabled() {
                return List.of(asset("BTCUSDT"), asset("ETHUSDT"), asset("SOLUSDT"));
            }

            @Override
            public Optional<AssetRow> find(String symbolOrCode) {
                return Optional.empty();
            }
        };
        IntelligenceSyncService service = new IntelligenceSyncService(futures, assets, null, metrics, null, null);
        IllegalStateException failure = assertThrows(IllegalStateException.class, service::syncDerivatives);
        assertEquals(List.of("BTCUSDT", "SOLUSDT"), saved);
        assertTrue(failure.getMessage().contains("ETHUSDT"));
    }

    private static AssetRow asset(String symbol) {
        return new AssetRow(symbol.substring(0, 3), symbol, symbol, "SPOT", true);
    }
}
