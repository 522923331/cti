package com.cti.admin.report;

import com.cti.common.Versions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DailyReportService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DailyReportService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public StoredReport generate() {
        Date latest = jdbc.queryForObject(
                "SELECT MAX(trade_date) FROM ct_trend_score WHERE strategy_version = ?",
                Date.class, Versions.STRATEGY);
        if (latest == null) {
            throw new IllegalStateException("还没有评分");
        }
        LocalDate day = latest.toLocalDate();
        Map<String, Object> regime = one("""
                SELECT regime, raw_regime, confirm_count, market_score, btc_trend_score, market_breadth,
                       btc_volatility, total_exposure_limit, risk_override, reason_json
                FROM ct_market_regime WHERE trade_date = ? AND strategy_version = ?
                """, day, Versions.STRATEGY);
        List<Map<String, Object>> scores = jdbc.queryForList("""
                SELECT symbol, market_score, trend_score, flow_score, derivatives_score, sentiment_score,
                       total_score, signal_level, feature_version
                FROM ct_trend_score
                WHERE trade_date = ? AND strategy_version = ?
                ORDER BY total_score IS NULL, total_score DESC, symbol
                """, day, Versions.STRATEGY);
        if (scores.isEmpty()) {
            throw new IllegalStateException("还没有评分");
        }
        String featureVersion = str(scores.get(0).get("feature_version"));
        List<Map<String, Object>> features = jdbc.queryForList("""
                SELECT symbol, ema20, ema50, ema200, return_7d, spot_delta, spot_delta_ratio,
                       funding_zscore, oi_change_7d, data_quality_status
                FROM ct_feature_daily
                WHERE trade_date = ? AND feature_version = ?
                """, day, featureVersion);
        long openTime = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        for (Map<String, Object> feature : features) {
            List<BigDecimal> closes = jdbc.query("""
                    SELECT close_price FROM ct_kline
                    WHERE exchange_code = ? AND symbol = ? AND interval_code = '1d' AND is_closed = 1 AND open_time = ?
                    """, (rs, row) -> rs.getBigDecimal("close_price"), Versions.EXCHANGE, str(feature.get("symbol")), openTime);
            feature.put("close_hint", closes.isEmpty() ? null : closes.get(0));
        }
        List<Map<String, Object>> signals = jdbc.queryForList("""
                SELECT signal_id, symbol, signal_type, signal_status, score, feature_version, strategy_version
                FROM ct_trade_signal
                WHERE trade_date = ? AND strategy_version = ?
                ORDER BY symbol, signal_type
                """, day, Versions.STRATEGY);
        Map<String, Object> account = one("""
                SELECT cash_balance, equity, peak_equity, as_of_date
                FROM ct_portfolio_account
                WHERE account_code = ? AND strategy_version = ?
                """, Versions.STRATEGY_NAME, Versions.STRATEGY);
        Integer positions = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ct_portfolio_position
                WHERE account_code = ? AND strategy_version = ?
                """, Integer.class, Versions.STRATEGY_NAME, Versions.STRATEGY);
        List<Map<String, Object>> quality = jdbc.queryForList("""
                SELECT q.symbol, q.interval_code, q.check_date, q.status, q.missing_count, q.anomaly_count
                FROM ct_data_quality_report q
                JOIN (
                    SELECT symbol, interval_code, MAX(check_date) AS check_date
                    FROM ct_data_quality_report
                    GROUP BY symbol, interval_code
                ) latest ON latest.symbol = q.symbol AND latest.interval_code = q.interval_code
                        AND latest.check_date = q.check_date
                ORDER BY q.symbol, q.interval_code
                """);
        StoredReport report = compose(day, featureVersion, regime, scores, features, signals, account,
                positions == null ? 0 : positions, quality);
        jdbc.update("""
                INSERT INTO ct_daily_report (trade_date, strategy_version, feature_version, title, summary, report_json)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    feature_version = VALUES(feature_version),
                    title = VALUES(title),
                    summary = VALUES(summary),
                    report_json = VALUES(report_json)
                """, day, report.strategyVersion(), report.featureVersion(), report.title(), report.summary(), json(report));
        return report;
    }

    public List<ReportSummary> recent() {
        return jdbc.query("""
                SELECT trade_date, strategy_version, feature_version, title, summary
                FROM ct_daily_report
                ORDER BY trade_date DESC
                """, (rs, row) -> new ReportSummary(
                rs.getDate("trade_date").toLocalDate(),
                rs.getString("strategy_version"),
                rs.getString("feature_version"),
                rs.getString("title"),
                rs.getString("summary")));
    }

    public StoredReport find(LocalDate tradeDate) {
        List<String> bodies = jdbc.query("""
                SELECT report_json FROM ct_daily_report
                WHERE trade_date = ? AND strategy_version = ?
                """, (rs, row) -> rs.getString("report_json"), tradeDate, Versions.STRATEGY);
        if (bodies.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(bodies.get(0), StoredReport.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("日报无法读取", ex);
        }
    }

    private StoredReport compose(LocalDate day, String featureVersion, Map<String, Object> regime,
                                 List<Map<String, Object>> scores, List<Map<String, Object>> features,
                                 List<Map<String, Object>> signals, Map<String, Object> account,
                                 int positions, List<Map<String, Object>> quality) {
        Map<String, Map<String, Object>> featureBySymbol = new LinkedHashMap<>();
        for (Map<String, Object> row : features) {
            featureBySymbol.put(str(row.get("symbol")), row);
        }
        Map<String, Object> btc = score(scores, "BTCUSDT");
        Map<String, Object> eth = score(scores, "ETHUSDT");
        long buys = signals.stream().filter(row -> "BUY".equals(str(row.get("signal_type")))).count();
        long badQuality = quality.stream().filter(row -> !"NORMAL".equals(str(row.get("status")))).count();
        BigDecimal cash = dec(account.get("cash_balance"));
        BigDecimal equity = dec(account.get("equity"));
        BigDecimal cashRatio = cash == null || equity == null || equity.signum() == 0 ? null : cash.divide(equity, 8, RoundingMode.HALF_UP);
        String regimeName = str(regime.get("regime"));
        String summary = day + " 确认状态 " + shown(regimeName)
                + "，仓位上限 " + percent(dec(regime.get("total_exposure_limit")))
                + "，现金比例 " + percent(cashRatio)
                + "，新买入 " + buys + " 笔。";
        List<ReportSection> sections = List.of(
                market(day, regime),
                majors(day, btc, eth, featureBySymbol),
                alts(day, scores),
                flow(day, scores, featureBySymbol),
                derivatives(day, scores, featureBySymbol),
                buys(day, signals, buys),
                holdings(day, account, positions, cashRatio),
                exposure(day, regime, positions),
                invalidation(day, regime),
                quality(day, quality, badQuality));
        return new StoredReport(day, Versions.STRATEGY, featureVersion, day + " 研究日报", summary, sections, Instant.now().toString());
    }

    private ReportSection market(LocalDate day, Map<String, Object> regime) {
        String override = str(regime.get("risk_override"));
        String conclusion = "确认后的市场状态是 " + shown(str(regime.get("regime")))
                + "，原始状态 " + shown(str(regime.get("raw_regime")))
                + "，相同原始状态已连续 " + str(regime.get("confirm_count")) + " 天。"
                + " 市场分 " + points(dec(regime.get("market_score")))
                + "，BTC 趋势分 " + points(dec(regime.get("btc_trend_score")))
                + "，广度 " + percent(dec(regime.get("market_breadth")))
                + "，波动率 " + shown(str(regime.get("btc_volatility")))
                + "。风险覆盖是 " + (override == null ? "空值" : override) + "。";
        return section("当前市场状态", conclusion, List.of(
                evidence("ct_market_regime", day + " / regime", shown(str(regime.get("regime")))),
                evidence("ct_market_regime", day + " / market_breadth", shown(str(regime.get("market_breadth")))),
                evidence("ct_market_regime", day + " / total_exposure_limit", shown(str(regime.get("total_exposure_limit"))))));
    }

    private ReportSection majors(LocalDate day, Map<String, Object> btc, Map<String, Object> eth,
                                 Map<String, Map<String, Object>> features) {
        String conclusion = line(btc, features.get("BTCUSDT")) + " " + line(eth, features.get("ETHUSDT"));
        return section("BTC / ETH 趋势", conclusion, List.of(
                evidence("ct_trend_score", "BTCUSDT " + day, shown(str(btc.get("total_score"))) + " / " + shown(str(btc.get("signal_level")))),
                evidence("ct_trend_score", "ETHUSDT " + day, shown(str(eth.get("total_score"))) + " / " + shown(str(eth.get("signal_level"))))));
    }

    private ReportSection alts(LocalDate day, List<Map<String, Object>> scores) {
        List<String> names = List.of("SOLUSDT", "BNBUSDT", "XRPUSDT", "LINKUSDT", "AVAXUSDT");
        List<Evidence> evidence = new ArrayList<>();
        StringBuilder text = new StringBuilder("山寨币按总分从高到低：");
        boolean any = false;
        for (Map<String, Object> row : scores) {
            if (!names.contains(str(row.get("symbol")))) {
                continue;
            }
            any = true;
            text.append(' ').append(row.get("symbol")).append(' ').append(points(dec(row.get("total_score")))).append('、');
            evidence.add(evidence("ct_trend_score", str(row.get("symbol")) + " " + day + " / total_score", shown(str(row.get("total_score")))));
        }
        if (!any) {
            text.append("没有山寨币评分。");
        }
        return section("主流山寨币排行榜", text.toString(), evidence);
    }

    private ReportSection flow(LocalDate day, List<Map<String, Object>> scores, Map<String, Map<String, Object>> features) {
        List<Evidence> evidence = new ArrayList<>();
        StringBuilder text = new StringBuilder("资金流分和当日 Spot Delta：");
        for (Map<String, Object> row : scores) {
            Map<String, Object> feature = features.get(str(row.get("symbol")));
            text.append(' ').append(row.get("symbol"))
                    .append(" 资金流 ").append(points(dec(row.get("flow_score"))))
                    .append("，Spot Delta ").append(shown(feature == null ? null : str(feature.get("spot_delta")))).append('；');
            evidence.add(evidence("ct_trend_score", str(row.get("symbol")) + " " + day + " / flow_score", shown(str(row.get("flow_score")))));
        }
        return section("现货资金流分析", text.toString(), evidence);
    }

    private ReportSection derivatives(LocalDate day, List<Map<String, Object>> scores, Map<String, Map<String, Object>> features) {
        long present = scores.stream().filter(row -> row.get("derivatives_score") != null).count();
        long funding = features.values().stream().filter(row -> row.get("funding_zscore") != null || row.get("oi_change_7d") != null).count();
        String conclusion = present == 0
                ? "七个币的衍生品分都是空值。资金费率和持仓量没有采集，空值没有被当成 0。"
                : present + " 个币有衍生品分。";
        return section("衍生品风险分析", conclusion, List.of(
                evidence("ct_trend_score", day + " / derivatives_score 非空数量", Long.toString(present)),
                evidence("ct_feature_daily", day + " / funding 或 oi 非空数量", Long.toString(funding))));
    }

    private ReportSection buys(LocalDate day, List<Map<String, Object>> signals, long buys) {
        List<Evidence> evidence = new ArrayList<>();
        if (buys == 0) {
            evidence.add(evidence("ct_trade_signal", day + " / BUY", "0"));
            return section("新产生的买入信号", day + " 没有新的买入信号。", evidence);
        }
        StringBuilder text = new StringBuilder(day + " 的买入信号：");
        for (Map<String, Object> row : signals) {
            if (!"BUY".equals(str(row.get("signal_type")))) {
                continue;
            }
            text.append(' ').append(row.get("signal_id")).append('；');
            evidence.add(evidence("ct_trade_signal", str(row.get("signal_id")), str(row.get("signal_status"))));
        }
        return section("新产生的买入信号", text.toString(), evidence);
    }

    private ReportSection holdings(LocalDate day, Map<String, Object> account, int positions, BigDecimal cashRatio) {
        String conclusion = positions == 0
                ? "研究账户没有持仓。现金 " + money(dec(account.get("cash_balance")))
                + "，权益 " + money(dec(account.get("equity")))
                + "，现金比例 " + percent(cashRatio) + "。账户日期 " + shown(str(account.get("as_of_date"))) + "。"
                : "研究账户持有 " + positions + " 个币。现金比例 " + percent(cashRatio) + "。";
        return section("持仓风险", conclusion, List.of(
                evidence("ct_portfolio_position", day.toString(), Integer.toString(positions)),
                evidence("ct_portfolio_account", "equity", shown(str(account.get("equity"))))));
    }

    private ReportSection exposure(LocalDate day, Map<String, Object> regime, int positions) {
        String conclusion = "建议总仓位上限是 " + percent(dec(regime.get("total_exposure_limit")))
                + "，来自确认后的市场状态 " + shown(str(regime.get("regime")))
                + "。当前持仓数是 " + positions + "。";
        return section("建议仓位", conclusion, List.of(
                evidence("ct_market_regime", day + " / total_exposure_limit", shown(str(regime.get("total_exposure_limit"))))));
    }

    private ReportSection invalidation(LocalDate day, Map<String, Object> regime) {
        String conclusion = "新的多头开仓在确认状态变成 BEARISH 时停止。"
                + " 总分、趋势分或资金流分为空，或者数据质量是 INVALID，当日信号记为无效。"
                + " 当前确认状态是 " + shown(str(regime.get("regime"))) + "。";
        return section("关键失效条件", conclusion, List.of(
                evidence("ct_market_regime", day + " / regime", shown(str(regime.get("regime")))),
                evidence("ct_strategy_config", "strategy_version", Versions.STRATEGY)));
    }

    private ReportSection quality(LocalDate day, List<Map<String, Object>> quality, long bad) {
        String conclusion = bad == 0
                ? "最新的 " + quality.size() + " 份 K 线质量报告都是 NORMAL。"
                : bad + " 份质量报告不是 NORMAL。";
        List<Evidence> evidence = new ArrayList<>();
        for (Map<String, Object> row : quality) {
            if (bad == 0 || !"NORMAL".equals(str(row.get("status")))) {
                evidence.add(evidence("ct_data_quality_report",
                        str(row.get("symbol")) + " " + str(row.get("interval_code")) + " " + str(row.get("check_date")),
                        str(row.get("status"))));
            }
        }
        if (evidence.isEmpty()) {
            evidence.add(evidence("ct_data_quality_report", day.toString(), "没有报告"));
        }
        return section("数据质量异常", conclusion, evidence);
    }

    private static String line(Map<String, Object> score, Map<String, Object> feature) {
        return str(score.get("symbol")) + " 总分 " + points(dec(score.get("total_score")))
                + "，趋势分 " + points(dec(score.get("trend_score")))
                + "，级别 " + shown(str(score.get("signal_level")))
                + "，收盘 " + money(dec(feature == null ? null : feature.get("close_hint")))
                + "，EMA20/50/200 "
                + money(dec(feature == null ? null : feature.get("ema20"))) + " / "
                + money(dec(feature == null ? null : feature.get("ema50"))) + " / "
                + money(dec(feature == null ? null : feature.get("ema200"))) + "。";
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) {
            return Map.of();
        }
        return rows.get(0);
    }

    private static Map<String, Object> score(List<Map<String, Object>> scores, String symbol) {
        for (Map<String, Object> row : scores) {
            if (symbol.equals(str(row.get("symbol")))) {
                return row;
            }
        }
        return Map.of("symbol", symbol);
    }

    private String json(StoredReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("日报无法序列化", ex);
        }
    }

    private static ReportSection section(String title, String conclusion, List<Evidence> evidence) {
        return new ReportSection(title, conclusion, evidence);
    }

    private static Evidence evidence(String source, String key, String value) {
        return new Evidence(source, key, value);
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static BigDecimal dec(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private static String shown(String value) {
        return value == null || value.isBlank() ? "空值" : value;
    }

    private static String points(BigDecimal value) {
        return value == null ? "空值" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String money(BigDecimal value) {
        return value == null ? "空值" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String percent(BigDecimal value) {
        if (value == null) {
            return "空值";
        }
        return value.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
