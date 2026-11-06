package com.cti.feature;

import com.cti.common.Versions;
import com.cti.data.store.AssetRepository;
import com.cti.data.store.AssetRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyFeatureServiceTest {

    @Test
    void savesPythonRowsAndKeepsMissingDerivativesNull() {
        List<FeatureRow> saved = new ArrayList<>();
        FeatureRow row = sample();
        DailyFeatureService service = service((symbols, tradeDate) -> {
            assertEquals(List.of("BTCUSDT"), symbols);
            assertNull(tradeDate);
            return new FeatureBatch(Versions.FEATURE, List.of(row));
        }, saved);
        String message = service.build(null);
        assertEquals(1, saved.size());
        assertNull(saved.get(0).fundingRate());
        assertNull(saved.get(0).oiChange1d());
        assertEquals(0, saved.get(0).spotDelta().compareTo(BigDecimal.ZERO));
        assertTrue(message.contains("rows=1"));
        assertTrue(message.contains(Versions.FEATURE));
    }

    @Test
    void mismatchedVersionDoesNotSave() {
        List<FeatureRow> saved = new ArrayList<>();
        DailyFeatureService service = service((symbols, tradeDate) -> new FeatureBatch("features-0.0.0", List.of()), saved);
        assertThrows(IllegalStateException.class, () -> service.build(null));
        assertTrue(saved.isEmpty());
    }

    private static DailyFeatureService service(QuantFeatureClient client, List<FeatureRow> saved) {
        AssetRepository assets = new AssetRepository() {
            @Override
            public List<AssetRow> enabled() {
                return List.of(new AssetRow("BTC", "Bitcoin", "BTCUSDT", "SPOT", true));
            }

            @Override
            public Optional<AssetRow> find(String symbolOrCode) {
                return Optional.empty();
            }
        };
        FeatureRepository repository = new FeatureRepository() {
            @Override
            public void saveAll(List<FeatureRow> rows) {
                saved.addAll(rows);
            }

            @Override
            public List<FeatureRow> latest(String symbol, String featureVersion, int limit) {
                return List.of();
            }
        };
        return new DailyFeatureService(assets, client, repository);
    }

    private static FeatureRow sample() {
        return new FeatureRow("BTCUSDT", LocalDate.parse("2024-01-20"),
                new BigDecimal("10"), null, null,
                null, null, null, null,
                null, null, null, null,
                null, null,
                BigDecimal.ZERO, null,
                null, null, null, null,
                Versions.FEATURE, "NORMAL");
    }
}
