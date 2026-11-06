package com.cti.strategy;

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

class ScoreServiceTest {

    @Test
    void keepsMissingSentimentAndDerivativesNull() {
        List<ScoreRow> saved = new ArrayList<>();
        ScoreRow row = new ScoreRow("BTCUSDT", LocalDate.parse("2026-11-05"),
                new BigDecimal("10.0000"), new BigDecimal("20.0000"), new BigDecimal("0.0000"),
                null, null, new BigDecimal("12.0000"), "NEUTRAL",
                "{\"sentiment\":\"ABSENT\"}", "{\"regime\":\"NEUTRAL\"}",
                Versions.FEATURE, Versions.STRATEGY);
        ScoreService service = service(new ScoreClient() {
            @Override
            public RegimeBatch regimes(List<String> symbols, LocalDate tradeDate) {
                return new RegimeBatch(Versions.STRATEGY, Versions.FEATURE, List.of());
            }

            @Override
            public ScoreBatch scores(List<String> symbols, LocalDate tradeDate) {
                assertEquals(List.of("BTCUSDT"), symbols);
                assertNull(tradeDate);
                return new ScoreBatch(Versions.STRATEGY, Versions.FEATURE, List.of(row));
            }
        }, saved);
        String message = service.buildScores();
        assertEquals(1, saved.size());
        assertNull(saved.get(0).sentimentScore());
        assertNull(saved.get(0).derivativesScore());
        assertTrue(message.contains("rows=1"));
    }

    @Test
    void rejectsAScoreThatFillsSentimentWithZero() {
        List<ScoreRow> saved = new ArrayList<>();
        ScoreRow row = new ScoreRow("BTCUSDT", LocalDate.parse("2026-11-05"),
                null, null, null, null, BigDecimal.ZERO, null, null, "{}", "{}",
                Versions.FEATURE, Versions.STRATEGY);
        ScoreService service = service(new ScoreClient() {
            @Override
            public RegimeBatch regimes(List<String> symbols, LocalDate tradeDate) {
                return new RegimeBatch(Versions.STRATEGY, Versions.FEATURE, List.of());
            }

            @Override
            public ScoreBatch scores(List<String> symbols, LocalDate tradeDate) {
                return new ScoreBatch(Versions.STRATEGY, Versions.FEATURE, List.of(row));
            }
        }, saved);
        assertThrows(IllegalStateException.class, service::buildScores);
        assertTrue(saved.isEmpty());
    }

    private static ScoreService service(ScoreClient client, List<ScoreRow> saved) {
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
        return new ScoreService(assets, client, new RegimeRepository() {
            @Override
            public void saveAll(List<RegimeRow> rows) {
            }

            @Override
            public List<RegimeRow> latest(String strategyVersion, int limit) {
                return List.of();
            }
        }, new ScoreRepository() {
            @Override
            public void saveAll(List<ScoreRow> rows) {
                saved.addAll(rows);
            }

            @Override
            public List<ScoreRow> history(String symbol, String strategyVersion, int limit) {
                return List.of();
            }

            @Override
            public List<ScoreRow> ranking(LocalDate tradeDate, String strategyVersion) {
                return List.of();
            }

            @Override
            public LocalDate latestDate(String strategyVersion) {
                return null;
            }
        });
    }
}
