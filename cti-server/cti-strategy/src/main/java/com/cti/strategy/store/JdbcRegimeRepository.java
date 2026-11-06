package com.cti.strategy.store;

import com.cti.strategy.RegimeRepository;
import com.cti.strategy.RegimeRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class JdbcRegimeRepository implements RegimeRepository {

    private static final String INSERT = """
            INSERT INTO ct_market_regime (
                trade_date, regime, raw_regime, confirm_count, market_score, btc_trend_score,
                market_breadth, btc_volatility, total_exposure_limit, risk_override, reason_json, strategy_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                regime = VALUES(regime),
                raw_regime = VALUES(raw_regime),
                confirm_count = VALUES(confirm_count),
                market_score = VALUES(market_score),
                btc_trend_score = VALUES(btc_trend_score),
                market_breadth = VALUES(market_breadth),
                btc_volatility = VALUES(btc_volatility),
                total_exposure_limit = VALUES(total_exposure_limit),
                risk_override = VALUES(risk_override),
                reason_json = VALUES(reason_json)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;

    public JdbcRegimeRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void saveAll(List<RegimeRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        transactions.executeWithoutResult(status -> jdbcTemplate.batchUpdate(INSERT, rows, 200, JdbcRegimeRepository::bind));
    }

    @Override
    public List<RegimeRow> latest(String strategyVersion, int limit) {
        List<RegimeRow> rows = jdbcTemplate.query("""
                SELECT trade_date, regime, raw_regime, confirm_count, market_score, btc_trend_score,
                       market_breadth, btc_volatility, total_exposure_limit, risk_override, reason_json, strategy_version
                FROM ct_market_regime
                WHERE strategy_version = ?
                ORDER BY trade_date DESC
                LIMIT ?
                """, (rs, row) -> new RegimeRow(
                rs.getDate("trade_date").toLocalDate(),
                rs.getString("regime"),
                rs.getString("raw_regime"),
                rs.getInt("confirm_count"),
                rs.getBigDecimal("market_score"),
                rs.getBigDecimal("btc_trend_score"),
                rs.getBigDecimal("market_breadth"),
                rs.getBigDecimal("btc_volatility"),
                rs.getBigDecimal("total_exposure_limit"),
                rs.getString("risk_override"),
                rs.getString("reason_json"),
                rs.getString("strategy_version")), strategyVersion, limit);
        List<RegimeRow> chronological = new ArrayList<>(rows);
        Collections.reverse(chronological);
        return chronological;
    }

    private static void bind(PreparedStatement statement, RegimeRow row) throws SQLException {
        int index = 1;
        statement.setDate(index++, Date.valueOf(row.tradeDate()));
        statement.setString(index++, row.regime());
        statement.setString(index++, row.rawRegime());
        statement.setInt(index++, row.confirmCount());
        index = bindDecimal(statement, index, row.marketScore());
        index = bindDecimal(statement, index, row.btcTrendScore());
        index = bindDecimal(statement, index, row.marketBreadth());
        index = bindDecimal(statement, index, row.btcVolatility());
        index = bindDecimal(statement, index, row.totalExposureLimit());
        if (row.riskOverride() == null) {
            statement.setNull(index++, Types.VARCHAR);
        } else {
            statement.setString(index++, row.riskOverride());
        }
        statement.setString(index++, row.reasonJson());
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
