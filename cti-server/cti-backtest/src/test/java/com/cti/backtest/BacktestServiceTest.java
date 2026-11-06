package com.cti.backtest;

import com.cti.common.Versions;
import com.cti.data.store.AssetRepository;
import com.cti.data.store.AssetRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void savesTheReturnedCurveAndRejectsAVersionMismatch() throws Exception {
        List<SavedRun> saved = new ArrayList<>();
        BacktestService service = service(saved, payload("1.0.0", "features-1.0.0"));
        RunSummary summary = service.run("CTI_TREND_V1", null, null, "BASE");
        assertEquals(1, saved.size());
        assertEquals(new BigDecimal("0.038884106583"), summary.totalReturn());
        assertEquals(1, saved.get(0).trades().size());
        assertEquals(1, saved.get(0).equity().size());
        assertTrue(summary.runId().startsWith("bt-"));

        BacktestService mismatch = service(saved, payload("9.9.9", "features-1.0.0"));
        assertThrows(IllegalStateException.class, () -> mismatch.run("CTI_TREND_V1", null, null, null));
        assertEquals(1, saved.size());
    }

    @Test
    void rejectsAnUnknownStrategyBeforeCallingPython() {
        List<SavedRun> saved = new ArrayList<>();
        BacktestService service = service(saved, mapper.createObjectNode());
        assertThrows(IllegalArgumentException.class, () -> service.run("NO_SUCH", null, null, "BASE"));
        assertTrue(saved.isEmpty());
    }

    private BacktestService service(List<SavedRun> saved, JsonNode payload) {
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
        BacktestClient client = new BacktestClient() {
            @Override
            public JsonNode run(List<String> symbols, String strategy, LocalDate start, LocalDate end, String costScenario) {
                return payload;
            }

            @Override
            public JsonNode compare(List<String> symbols, List<String> strategies, LocalDate start, LocalDate end, String costScenario) {
                return mapper.createArrayNode();
            }

            @Override
            public JsonNode walkForward(List<String> symbols, LocalDate start, LocalDate end, String costScenario) {
                return mapper.createObjectNode();
            }

            @Override
            public JsonNode sensitivity(List<String> symbols, LocalDate start, LocalDate end, String costScenario) {
                return mapper.createObjectNode();
            }
        };
        BacktestRepository repository = new BacktestRepository() {
            @Override
            public void save(SavedRun run) {
                saved.add(run);
            }

            @Override
            public List<RunSummary> recent(int limit) {
                return List.of();
            }

            @Override
            public Optional<RunSummary> find(String runId) {
                return saved.stream().filter(row -> row.runId().equals(runId)).map(BacktestServiceTest::summary).findFirst();
            }

            @Override
            public List<TradeRow> trades(String runId) {
                return List.of();
            }

            @Override
            public List<EquityRow> equity(String runId, int limit) {
                return List.of();
            }
        };
        return new BacktestService(assets, client, repository, mapper);
    }

    private JsonNode payload(String strategyVersion, String featureVersion) throws Exception {
        return mapper.readTree("""
                {
                  "strategyVersion": "%s",
                  "featureVersion": "%s",
                  "strategy": "CTI_TREND_V1",
                  "startDate": "2024-08-28",
                  "endDate": "2026-11-05",
                  "costScenario": "BASE",
                  "metrics": {"totalReturn": "0.038884106583", "totalTrades": 29},
                  "report": {"initialCapital": "100000", "commissionRate": "0.001", "slippageRate": "0.001"},
                  "equity": [{"tradeDate": "2026-11-05", "cash": "103888.41", "equity": "103888.41", "peakEquity": "106779.18", "drawdown": "-0.027", "exposure": "0"}],
                  "trades": [{"tradeDate": "2024-11-11", "symbol": "BTCUSDT", "side": "BUY", "quantity": "1", "openPrice": "100", "fillPrice": "100.1", "fee": "0.1", "slippage": "0.1", "pnl": null, "openedOn": null, "holdingDays": null}]
                }
                """.formatted(strategyVersion, featureVersion));
    }

    private static RunSummary summary(SavedRun run) {
        return new RunSummary(
                run.runId(), run.strategyName(), run.strategyVersion(), run.startDate(), run.endDate(),
                run.initialCapital(), run.commissionRate(), run.slippageRate(), run.status(), run.configJson(), run.reportJson(),
                run.totalReturn(), run.annualizedReturn(), run.annualizedVolatility(), run.sharpeRatio(), run.sortinoRatio(),
                run.maxDrawdown(), run.winRate(), run.profitFactor(), run.totalTrades(), run.averageHoldingDays(),
                run.totalFee(), run.totalSlippage(), run.benchmarkReturn(), run.excessReturn());
    }
}
