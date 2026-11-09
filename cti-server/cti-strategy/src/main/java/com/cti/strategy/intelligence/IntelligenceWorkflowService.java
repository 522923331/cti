package com.cti.strategy.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class IntelligenceWorkflowService {

    private final HttpIntelligenceClient client;
    private final JdbcIntelligenceRepository repository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AlertPolicy policy = new AlertPolicy();
    private final IntelligenceReportComposer reports = new IntelligenceReportComposer();

    public IntelligenceWorkflowService(HttpIntelligenceClient client, JdbcIntelligenceRepository repository,
                                       JdbcTemplate jdbc, ObjectMapper mapper) {
        this.client = client;
        this.repository = repository;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public String score() {
        int saved = repository.saveScores(client.calculate());
        return "rows=" + saved + " version=intelligence-1.1.0";
    }

    public String alerts() {
        LocalDate tradeDate = repository.latestScoreDate().orElseThrow(() -> new IllegalStateException("还没有情报评分"));
        long triggeredAt = tradeDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        int created = 0;
        for (Candidate candidate : candidates(tradeDate)) {
            if (!policy.allow(candidate.enabled(), triggeredAt, candidate.cooldownMinutes(),
                    prior(candidate.ruleCode(), candidate.targetCode()), candidate.alertId())) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO ct_market_alert (
                        alert_id, rule_code, target_type, target_code, severity, alert_status,
                        trigger_value, threshold_value, explanation, triggered_at_ms
                    ) VALUES (?, ?, ?, ?, ?, 'CREATED', ?, ?, ?, ?)
                    """, candidate.alertId(), candidate.ruleCode(), candidate.targetType(), candidate.targetCode(),
                    candidate.severity(), candidate.triggerValue(), candidate.threshold(), candidate.explanation(), triggeredAt);
            created++;
        }
        return "tradeDate=" + tradeDate + " created=" + created;
    }

    public String report() {
        LocalDate tradeDate = repository.latestScoreDate().orElseThrow(() -> new IllegalStateException("还没有情报评分"));
        var body = reports.compose(tradeDate, "intelligence-1.1.0", repository.regimeOn(tradeDate), repository.scoresOn(tradeDate));
        repository.saveReport(tradeDate, "intelligence-1.1.0", body.toString());
        return "tradeDate=" + tradeDate + " conclusions=" + body.path("conclusions").size();
    }

    private List<Candidate> candidates(LocalDate tradeDate) {
        List<Candidate> found = new ArrayList<>();
        String regime = repository.regimeOn(tradeDate);
        BigDecimal breadth = one("SELECT market_breadth FROM ct_market_regime WHERE trade_date = ? AND strategy_version = '1.0.0'", tradeDate);
        BigDecimal btcVol = one("""
                SELECT realized_volatility_20d FROM ct_feature_daily
                WHERE symbol = 'BTCUSDT' AND trade_date = ? AND feature_version = 'features-1.0.0'
                """, tradeDate);
        consider(found, tradeDate, "BTC_TREND_BREAKDOWN", "BTC", "BTCUSDT",
                "BEARISH".equals(regime) ? BigDecimal.ONE : null, "regime", regime, "ct_market_regime", "regime");
        consider(found, tradeDate, "BTC_VOLATILITY_SPIKE", "BTC", "BTCUSDT",
                btcVol, "realized_volatility_20d", text(btcVol), "ct_feature_daily", "realized_volatility_20d");
        consider(found, tradeDate, "ALT_BREADTH_COLLAPSE", "MARKET", "MARKET",
                breadth, "market_breadth", text(breadth), "ct_market_regime", "market_breadth");
        List<MetricRow> volumes = jdbc.query("""
                SELECT symbol, volume_ratio_20d AS metric_value FROM ct_feature_daily
                WHERE trade_date = ? AND feature_version = 'features-1.0.0'
                """, (rs, row) -> new MetricRow(rs.getString("symbol"), rs.getBigDecimal("metric_value")), Date.valueOf(tradeDate));
        for (MetricRow volume : volumes) {
            consider(found, tradeDate, "ASSET_VOLUME_SPIKE", "SYMBOL", volume.symbol(),
                    volume.value(), "volume_ratio_20d", text(volume.value()), "ct_feature_daily", "volume_ratio_20d");
        }
        List<ReasonRow> reasons = jdbc.query("""
                SELECT symbol, reason_json FROM ct_intelligence_score
                WHERE trade_date = ? AND model_version = 'intelligence-1.1.0'
                """, (rs, row) -> new ReasonRow(rs.getString("symbol"), rs.getString("reason_json")), Date.valueOf(tradeDate));
        for (ReasonRow reasonRow : reasons) {
            JsonNode reason = read(reasonRow.json());
            BigDecimal strength = decimal(reason.path("components").path("relative_strength"));
            BigDecimal funding = decimal(reason.path("inputs").path("fundingZ20"));
            consider(found, tradeDate, "ASSET_RELATIVE_STRENGTH_SPIKE", "SYMBOL", reasonRow.symbol(),
                    strength, "relative_strength", text(strength), "ct_intelligence_score", "reason_json");
            consider(found, tradeDate, "FUNDING_EXTREME", "SYMBOL", reasonRow.symbol(),
                    funding == null ? null : funding.abs(), "funding_z20", text(funding), "ct_derivatives_metric", "metric_value");
        }
        long start = tradeDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        long end = tradeDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        List<NewsRow> news = jdbc.query("""
                SELECT event_id, impact_level FROM ct_news_event
                WHERE impact_level IN ('HIGH', 'CRITICAL') AND published_at_ms >= ? AND published_at_ms < ?
                """, (rs, row) -> new NewsRow(rs.getString("event_id"), rs.getString("impact_level")), start, end);
        for (NewsRow event : news) {
            consider(found, tradeDate, "HIGH_IMPACT_NEWS", "EVENT", event.eventId(),
                    BigDecimal.ONE, "impact_level", event.impact(), "ct_news_event", "impact_level");
        }
        return found;
    }

    private void consider(List<Candidate> found, LocalDate tradeDate, String ruleCode, String targetType, String targetCode,
                          BigDecimal trigger, String metric, String value, String table, String field) {
        List<RuleRow> rules = jdbc.query("""
                SELECT enabled, threshold_value, severity, cooldown_minutes
                FROM ct_alert_rule WHERE rule_code = ?
                """, (rs, row) -> new RuleRow(rs.getInt("enabled") == 1, rs.getBigDecimal("threshold_value"),
                rs.getString("severity"), rs.getInt("cooldown_minutes")), ruleCode);
        if (rules.isEmpty() || !breached(ruleCode, rules.get(0).threshold(), trigger)) {
            return;
        }
        RuleRow rule = rules.get(0);
        String explanation = cite(tradeDate, metric, value, table, field);
        found.add(new Candidate(ruleCode, rule.enabled(), rule.cooldown(), targetType, targetCode, rule.severity(),
                trigger, rule.threshold(), explanation, sha(ruleCode + "|" + targetCode + "|" + tradeDate)));
    }

    private static boolean breached(String ruleCode, BigDecimal threshold, BigDecimal trigger) {
        if (trigger == null) {
            return false;
        }
        if ("BTC_TREND_BREAKDOWN".equals(ruleCode) || "HIGH_IMPACT_NEWS".equals(ruleCode)) {
            return true;
        }
        if (threshold == null) {
            return false;
        }
        if ("ALT_BREADTH_COLLAPSE".equals(ruleCode)) {
            return trigger.compareTo(threshold) <= 0;
        }
        return trigger.compareTo(threshold) >= 0;
    }

    private List<AlertPolicy.PriorAlert> prior(String ruleCode, String targetCode) {
        return jdbc.query("""
                SELECT alert_id, alert_status, triggered_at_ms
                FROM ct_market_alert WHERE rule_code = ? AND target_code = ?
                """, (rs, row) -> new AlertPolicy.PriorAlert(rs.getString("alert_id"), rs.getString("alert_status"),
                rs.getLong("triggered_at_ms")), ruleCode, targetCode);
    }

    private BigDecimal one(String sql, LocalDate tradeDate) {
        List<BigDecimal> values = jdbc.query(sql, (rs, row) -> rs.getBigDecimal(1), Date.valueOf(tradeDate));
        return values.isEmpty() ? null : values.get(0);
    }

    private static String cite(LocalDate tradeDate, String metric, String value, String table, String field) {
        return "{\"metric\":\"" + metric + "\",\"value\":" + (value == null ? "null" : "\"" + value + "\"")
                + ",\"dataTime\":\"" + tradeDate + "\",\"table\":\"" + table + "\",\"field\":\"" + field
                + "\",\"modelVersion\":\"intelligence-1.1.0\"}";
    }

    private JsonNode read(String json) {
        try {
            return json == null ? mapper.createObjectNode() : mapper.readTree(json);
        } catch (IOException ex) {
            return mapper.createObjectNode();
        }
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return new BigDecimal(node.asText());
    }

    private static String text(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String sha(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record RuleRow(boolean enabled, BigDecimal threshold, String severity, int cooldown) {
    }

    private record MetricRow(String symbol, BigDecimal value) {
    }

    private record ReasonRow(String symbol, String json) {
    }

    private record NewsRow(String eventId, String impact) {
    }

    private record Candidate(String ruleCode, boolean enabled, int cooldownMinutes, String targetType, String targetCode,
                             String severity, BigDecimal triggerValue, BigDecimal threshold, String explanation, String alertId) {
    }
}
