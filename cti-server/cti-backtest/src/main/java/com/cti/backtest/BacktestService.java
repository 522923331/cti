package com.cti.backtest;

import com.cti.common.Versions;
import com.cti.data.store.AssetRepository;
import com.cti.data.store.AssetRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class BacktestService {

    static final Set<String> STRATEGIES = Set.of(
            "CTI_TREND_V1", "TREND_PULLBACK_V1", "TREND_BREAKOUT_V1", "MARKET_FILTER_TREND",
            "BASELINE_BTC_HOLD", "BASELINE_BTC_EMA50_200", "BASELINE_BTC_EMA20_50", "BASELINE_TSMOM_14");

    private final AssetRepository assets;
    private final BacktestClient client;
    private final BacktestRepository repository;
    private final ObjectMapper objectMapper;

    public BacktestService(AssetRepository assets, BacktestClient client, BacktestRepository repository, ObjectMapper objectMapper) {
        this.assets = assets;
        this.client = client;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public RunSummary run(String strategy, LocalDate start, LocalDate end, String costScenario) {
        String name = requiredStrategy(strategy);
        String cost = cost(costScenario);
        JsonNode body = client.run(symbols(), name, start, end, cost);
        SavedRun saved = fromRun(body, "SUCCEEDED");
        repository.save(saved);
        return repository.find(saved.runId()).orElseThrow();
    }

    public List<RunSummary> compare(List<String> strategies, LocalDate start, LocalDate end, String costScenario) {
        if (strategies == null || strategies.isEmpty()) {
            throw new IllegalArgumentException("至少要有一个策略");
        }
        List<String> names = strategies.stream().map(this::requiredStrategy).toList();
        String cost = cost(costScenario);
        JsonNode body = client.compare(symbols(), names, start, end, cost);
        if (!body.isArray() || body.isEmpty()) {
            throw new IllegalStateException("Python 没有返回对照结果");
        }
        List<RunSummary> saved = new ArrayList<>();
        for (JsonNode item : body) {
            SavedRun run = fromRun(item, "SUCCEEDED");
            repository.save(run);
            saved.add(repository.find(run.runId()).orElseThrow());
        }
        return saved;
    }

    public RunSummary walkForward(LocalDate start, LocalDate end, String costScenario) {
        String cost = cost(costScenario);
        JsonNode body = client.walkForward(symbols(), start, end, cost);
        versions(body);
        SavedRun saved = shell("CTI_TREND_V1", body, text(body, "status"), body.path("metrics"), body);
        repository.save(saved);
        return repository.find(saved.runId()).orElseThrow();
    }

    public RunSummary sensitivity(LocalDate start, LocalDate end, String costScenario) {
        JsonNode body = client.sensitivity(symbols(), start, end, cost(costScenario));
        versions(body);
        SavedRun saved = shell("CTI_TREND_V1", body, "SUCCEEDED", objectMapper.createObjectNode(), body);
        repository.save(saved);
        return repository.find(saved.runId()).orElseThrow();
    }

    private SavedRun fromRun(JsonNode body, String status) {
        versions(body);
        String strategy = text(body, "strategy");
        if (!STRATEGIES.contains(strategy)) {
            throw new IllegalStateException("策略无法识别");
        }
        return shell(strategy, body, status, body.path("metrics"), body.path("report"));
    }

    private SavedRun shell(String strategy, JsonNode body, String status, JsonNode metrics, JsonNode report) {
        LocalDate start = date(body, "startDate");
        LocalDate end = date(body, "endDate");
        if (start == null || end == null) {
            throw new IllegalStateException("回测没有起止日期");
        }
        String runId = "bt-" + UUID.randomUUID().toString().replace("-", "");
        List<TradeRow> trades = trades(body.path("trades"));
        List<EquityRow> equity = equity(body.path("equity"));
        return new SavedRun(
                runId, strategy, Versions.STRATEGY, start, end,
                decimal(report, "initialCapital"), decimal(report, "commissionRate"), decimal(report, "slippageRate"),
                status, config(strategy, text(body, "costScenario"), text(body, "sample")), json(report),
                decimal(metrics, "totalReturn"), decimal(metrics, "annualizedReturn"), decimal(metrics, "annualizedVolatility"),
                decimal(metrics, "sharpeRatio"), decimal(metrics, "sortinoRatio"), decimal(metrics, "maxDrawdown"),
                decimal(metrics, "winRate"), decimal(metrics, "profitFactor"), integer(metrics, "totalTrades"),
                decimal(metrics, "averageHoldingDays"), decimal(metrics, "totalFee"), decimal(metrics, "totalSlippage"),
                decimal(metrics, "benchmarkReturn"), decimal(metrics, "excessReturn"),
                trades, equity);
    }

    private List<String> symbols() {
        List<String> symbols = assets.enabled().stream().map(AssetRow::symbol).toList();
        if (!symbols.contains("BTCUSDT")) {
            throw new IllegalStateException("回测需要 BTCUSDT");
        }
        return symbols;
    }

    private String requiredStrategy(String strategy) {
        String name = strategy == null ? "" : strategy.trim();
        if (!STRATEGIES.contains(name)) {
            throw new IllegalArgumentException("策略无法识别");
        }
        return name;
    }

    private static String cost(String value) {
        String name = value == null || value.isBlank() ? "BASE" : value.trim().toUpperCase();
        if (!Set.of("LOW", "BASE", "HIGH").contains(name)) {
            throw new IllegalArgumentException("成本情景只能是 LOW、BASE 或 HIGH");
        }
        return name;
    }

    private void versions(JsonNode body) {
        if (!Versions.STRATEGY.equals(text(body, "strategyVersion")) || !Versions.FEATURE.equals(text(body, "featureVersion"))) {
            throw new IllegalStateException("Python 版本是 " + text(body, "strategyVersion") + " / " + text(body, "featureVersion"));
        }
    }

    private List<TradeRow> trades(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<TradeRow> rows = new ArrayList<>();
        for (JsonNode item : node) {
            rows.add(new TradeRow(
                    date(item, "tradeDate"), text(item, "symbol"), text(item, "side"),
                    required(item, "quantity"), required(item, "openPrice"), required(item, "fillPrice"),
                    required(item, "fee"), required(item, "slippage"), decimal(item, "pnl"),
                    date(item, "openedOn"), integer(item, "holdingDays")));
        }
        return rows;
    }

    private List<EquityRow> equity(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<EquityRow> rows = new ArrayList<>();
        for (JsonNode item : node) {
            rows.add(new EquityRow(
                    date(item, "tradeDate"), required(item, "cash"), required(item, "equity"),
                    required(item, "peakEquity"), required(item, "drawdown"), required(item, "exposure")));
        }
        return rows;
    }

    private String config(String strategy, String costScenario, String sample) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("strategy", strategy);
        if (costScenario != null) {
            node.put("costScenario", costScenario);
        }
        if (sample != null) {
            node.put("sample", sample);
        }
        return json(node);
    }

    private String json(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node == null || node.isMissingNode() ? objectMapper.createObjectNode() : node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("回测结果无法序列化", ex);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : LocalDate.parse(value);
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : new BigDecimal(value);
    }

    private static BigDecimal required(JsonNode node, String field) {
        BigDecimal value = decimal(node, field);
        if (value == null) {
            throw new IllegalStateException("回测明细缺少 " + field);
        }
        return value;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.asInt();
    }
}
