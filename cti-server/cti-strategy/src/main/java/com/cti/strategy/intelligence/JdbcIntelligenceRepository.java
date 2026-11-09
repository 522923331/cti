package com.cti.strategy.intelligence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcIntelligenceRepository {

    static final String INSERT_SCORE = """
            INSERT INTO ct_intelligence_score (
                symbol, trade_date, model_version, feature_version, trend_score, risk_score, sentiment_score,
                data_confidence, risk_level, signal_type, data_status, reason_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                trend_score = VALUES(trend_score),
                risk_score = VALUES(risk_score),
                sentiment_score = VALUES(sentiment_score),
                data_confidence = VALUES(data_confidence),
                risk_level = VALUES(risk_level),
                signal_type = VALUES(signal_type),
                data_status = VALUES(data_status),
                reason_json = VALUES(reason_json)
            """;

    static final String INSERT_SIGNAL = """
            INSERT INTO ct_intelligence_signal (
                signal_id, symbol, trade_date, model_version, signal_type, data_confidence, reason_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                signal_type = VALUES(signal_type),
                data_confidence = VALUES(data_confidence),
                reason_json = VALUES(reason_json)
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcIntelligenceRepository(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
    }

    public int saveScores(List<IntelligenceScore> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        transactions.executeWithoutResult(status -> {
            jdbc.batchUpdate(INSERT_SCORE, rows, 200, (statement, row) -> {
                statement.setString(1, row.symbol());
                statement.setDate(2, Date.valueOf(row.tradeDate()));
                statement.setString(3, row.modelVersion());
                statement.setString(4, "features-1.0.0");
                decimal(statement, 5, row.trendScore());
                decimal(statement, 6, row.riskScore());
                decimal(statement, 7, row.sentimentScore());
                decimal(statement, 8, row.confidence());
                statement.setString(9, row.riskLevel());
                statement.setString(10, row.signalType());
                statement.setString(11, row.dataStatus());
                statement.setString(12, row.reasonJson());
            });
            jdbc.batchUpdate(INSERT_SIGNAL, rows, 200, (statement, row) -> {
                statement.setString(1, row.modelVersion() + ":" + row.symbol() + ":" + row.tradeDate());
                statement.setString(2, row.symbol());
                statement.setDate(3, Date.valueOf(row.tradeDate()));
                statement.setString(4, row.modelVersion());
                statement.setString(5, row.signalType());
                decimal(statement, 6, row.confidence());
                statement.setString(7, row.reasonJson());
            });
        });
        return rows.size();
    }

    public Optional<LocalDate> latestScoreDate() {
        List<Date> dates = jdbc.query("SELECT MAX(trade_date) AS trade_date FROM ct_intelligence_score",
                (rs, row) -> rs.getDate("trade_date"));
        if (dates.isEmpty() || dates.get(0) == null) {
            return Optional.empty();
        }
        return Optional.of(dates.get(0).toLocalDate());
    }

    public List<IntelligenceReportComposer.ScoreLine> scoresOn(LocalDate tradeDate) {
        return jdbc.query("""
                SELECT symbol, trend_score, data_confidence, signal_type, data_status
                FROM ct_intelligence_score
                WHERE trade_date = ? AND model_version = 'intelligence-1.1.0'
                ORDER BY trend_score IS NULL, trend_score DESC, symbol
                """, (rs, row) -> new IntelligenceReportComposer.ScoreLine(
                rs.getString("symbol"),
                rs.getBigDecimal("trend_score"),
                rs.getBigDecimal("data_confidence"),
                rs.getString("signal_type"),
                rs.getString("data_status")), Date.valueOf(tradeDate));
    }

    public List<StoredIntelligenceScore> latestScores() {
        return jdbc.query("""
                SELECT symbol, trade_date, trend_score, risk_score, sentiment_score, data_confidence,
                       risk_level, signal_type, data_status
                FROM ct_intelligence_score
                WHERE trade_date = (SELECT MAX(trade_date) FROM ct_intelligence_score)
                  AND model_version = 'intelligence-1.1.0'
                ORDER BY trend_score IS NULL, trend_score DESC, symbol
                """, (rs, row) -> new StoredIntelligenceScore(
                rs.getString("symbol"),
                rs.getDate("trade_date").toLocalDate(),
                rs.getBigDecimal("trend_score"),
                rs.getBigDecimal("risk_score"),
                rs.getBigDecimal("sentiment_score"),
                rs.getBigDecimal("data_confidence"),
                rs.getString("risk_level"),
                rs.getString("signal_type"),
                rs.getString("data_status")));
    }

    public void saveReport(LocalDate tradeDate, String modelVersion, String reportJson) {
        jdbc.update("""
                INSERT INTO ct_intelligence_report (trade_date, model_version, report_json)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE report_json = VALUES(report_json)
                """, Date.valueOf(tradeDate), modelVersion, reportJson);
    }

    public List<StoredAlert> latestAlerts() {
        return jdbc.query("""
                SELECT rule_code, target_code, severity, alert_status, trigger_value, triggered_at_ms, explanation
                FROM ct_market_alert
                ORDER BY triggered_at_ms DESC, rule_code
                LIMIT 50
                """, (rs, row) -> new StoredAlert(
                rs.getString("rule_code"),
                rs.getString("target_code"),
                rs.getString("severity"),
                rs.getString("alert_status"),
                rs.getBigDecimal("trigger_value"),
                rs.getLong("triggered_at_ms"),
                rs.getString("explanation")));
    }

    public Optional<String> latestReport() {
        List<String> rows = jdbc.query("""
                SELECT report_json FROM ct_intelligence_report
                ORDER BY trade_date DESC, id DESC LIMIT 1
                """, (rs, row) -> rs.getString("report_json"));
        return rows.stream().findFirst();
    }

    public String regimeOn(LocalDate tradeDate) {
        List<String> rows = jdbc.query("""
                SELECT regime FROM ct_market_regime
                WHERE trade_date = ? AND strategy_version = '1.0.0'
                """, (rs, row) -> rs.getString("regime"), Date.valueOf(tradeDate));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static void decimal(java.sql.PreparedStatement statement, int index, BigDecimal value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    public record IntelligenceScore(
            String symbol,
            LocalDate tradeDate,
            String modelVersion,
            BigDecimal trendScore,
            BigDecimal riskScore,
            BigDecimal sentimentScore,
            BigDecimal confidence,
            String riskLevel,
            String signalType,
            String dataStatus,
            String reasonJson
    ) {
    }

    public record StoredAlert(
            String ruleCode,
            String targetCode,
            String severity,
            String status,
            BigDecimal triggerValue,
            long triggeredAtMs,
            String explanation
    ) {
    }

    public record StoredIntelligenceScore(
            String symbol,
            LocalDate tradeDate,
            BigDecimal trendScore,
            BigDecimal riskScore,
            BigDecimal sentimentScore,
            BigDecimal confidence,
            String riskLevel,
            String signalType,
            String dataStatus
    ) {
    }
}
