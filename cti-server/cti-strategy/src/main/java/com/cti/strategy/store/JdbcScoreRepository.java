package com.cti.strategy.store;

import com.cti.strategy.ScoreRepository;
import com.cti.strategy.ScoreRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class JdbcScoreRepository implements ScoreRepository {

    private static final String INSERT = """
            INSERT INTO ct_trend_score (
                symbol, trade_date, market_score, trend_score, flow_score, derivatives_score, sentiment_score,
                total_score, signal_level, reason_json, risk_json, feature_version, strategy_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                market_score = VALUES(market_score),
                trend_score = VALUES(trend_score),
                flow_score = VALUES(flow_score),
                derivatives_score = VALUES(derivatives_score),
                sentiment_score = VALUES(sentiment_score),
                total_score = VALUES(total_score),
                signal_level = VALUES(signal_level),
                reason_json = VALUES(reason_json),
                risk_json = VALUES(risk_json),
                feature_version = VALUES(feature_version)
            """;

    private static final RowMapper<ScoreRow> MAPPER = (rs, row) -> map(rs);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;

    public JdbcScoreRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void saveAll(List<ScoreRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        transactions.executeWithoutResult(status -> jdbcTemplate.batchUpdate(INSERT, rows, 200, JdbcScoreRepository::bind));
    }

    @Override
    public List<ScoreRow> history(String symbol, String strategyVersion, int limit) {
        List<ScoreRow> rows = jdbcTemplate.query("""
                SELECT symbol, trade_date, market_score, trend_score, flow_score, derivatives_score, sentiment_score,
                       total_score, signal_level, reason_json, risk_json, feature_version, strategy_version
                FROM ct_trend_score
                WHERE symbol = ? AND strategy_version = ?
                ORDER BY trade_date DESC
                LIMIT ?
                """, MAPPER, symbol, strategyVersion, limit);
        List<ScoreRow> chronological = new ArrayList<>(rows);
        Collections.reverse(chronological);
        return chronological;
    }

    @Override
    public List<ScoreRow> ranking(LocalDate tradeDate, String strategyVersion) {
        return jdbcTemplate.query("""
                SELECT symbol, trade_date, market_score, trend_score, flow_score, derivatives_score, sentiment_score,
                       total_score, signal_level, reason_json, risk_json, feature_version, strategy_version
                FROM ct_trend_score
                WHERE trade_date = ? AND strategy_version = ?
                ORDER BY total_score IS NULL, total_score DESC, symbol
                """, MAPPER, Date.valueOf(tradeDate), strategyVersion);
    }

    @Override
    public LocalDate latestDate(String strategyVersion) {
        return jdbcTemplate.query("""
                SELECT MAX(trade_date) AS trade_date FROM ct_trend_score WHERE strategy_version = ?
                """, rs -> rs.next() && rs.getDate("trade_date") != null ? rs.getDate("trade_date").toLocalDate() : null,
                strategyVersion);
    }

    private static ScoreRow map(ResultSet rs) throws SQLException {
        return new ScoreRow(
                rs.getString("symbol"),
                rs.getDate("trade_date").toLocalDate(),
                rs.getBigDecimal("market_score"),
                rs.getBigDecimal("trend_score"),
                rs.getBigDecimal("flow_score"),
                rs.getBigDecimal("derivatives_score"),
                rs.getBigDecimal("sentiment_score"),
                rs.getBigDecimal("total_score"),
                rs.getString("signal_level"),
                rs.getString("reason_json"),
                rs.getString("risk_json"),
                rs.getString("feature_version"),
                rs.getString("strategy_version"));
    }

    private static void bind(PreparedStatement statement, ScoreRow row) throws SQLException {
        int index = 1;
        statement.setString(index++, row.symbol());
        statement.setDate(index++, Date.valueOf(row.tradeDate()));
        index = bindDecimal(statement, index, row.marketScore());
        index = bindDecimal(statement, index, row.trendScore());
        index = bindDecimal(statement, index, row.flowScore());
        index = bindDecimal(statement, index, row.derivativesScore());
        index = bindDecimal(statement, index, row.sentimentScore());
        index = bindDecimal(statement, index, row.totalScore());
        if (row.signalLevel() == null) {
            statement.setNull(index++, Types.VARCHAR);
        } else {
            statement.setString(index++, row.signalLevel());
        }
        statement.setString(index++, row.reasonJson());
        statement.setString(index++, row.riskJson());
        statement.setString(index++, row.featureVersion());
        statement.setString(index, row.strategyVersion());
    }

    private static int bindDecimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
        return index + 1;
    }
}
