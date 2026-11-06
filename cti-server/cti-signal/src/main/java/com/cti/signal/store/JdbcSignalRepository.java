package com.cti.signal.store;

import com.cti.signal.SignalRepository;
import com.cti.signal.SignalRow;
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
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcSignalRepository implements SignalRepository {

    private static final String INSERT = """
            INSERT INTO ct_trade_signal (
                signal_id, symbol, signal_time, trade_date, signal_type, signal_status, score,
                entry_price, stop_price, suggested_position, risk_amount, signal_reason,
                invalidation_reason, feature_version, strategy_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final RowMapper<SignalRow> MAPPER = (rs, row) -> map(rs);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;

    public JdbcSignalRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void replaceAll(String strategyVersion, List<SignalRow> rows) {
        transactions.executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM ct_trade_signal WHERE strategy_version = ?", strategyVersion);
            if (!rows.isEmpty()) {
                jdbcTemplate.batchUpdate(INSERT, rows, 200, JdbcSignalRepository::bind);
            }
        });
    }

    @Override
    public List<SignalRow> latest(String strategyVersion) {
        return jdbcTemplate.query("""
                SELECT signal_id, symbol, signal_time, trade_date, signal_type, signal_status, score,
                       entry_price, stop_price, suggested_position, risk_amount, signal_reason,
                       invalidation_reason, feature_version, strategy_version
                FROM ct_trade_signal
                WHERE strategy_version = ?
                  AND trade_date = (
                      SELECT MAX(trade_date) FROM ct_trade_signal WHERE strategy_version = ?
                  )
                ORDER BY symbol
                """, MAPPER, strategyVersion, strategyVersion);
    }

    @Override
    public List<SignalRow> history(String strategyVersion, String symbol, int limit) {
        if (symbol == null) {
            return jdbcTemplate.query("""
                    SELECT signal_id, symbol, signal_time, trade_date, signal_type, signal_status, score,
                           entry_price, stop_price, suggested_position, risk_amount, signal_reason,
                           invalidation_reason, feature_version, strategy_version
                    FROM ct_trade_signal
                    WHERE strategy_version = ?
                    ORDER BY trade_date DESC, symbol, signal_type
                    LIMIT ?
                    """, MAPPER, strategyVersion, limit);
        }
        return jdbcTemplate.query("""
                SELECT signal_id, symbol, signal_time, trade_date, signal_type, signal_status, score,
                       entry_price, stop_price, suggested_position, risk_amount, signal_reason,
                       invalidation_reason, feature_version, strategy_version
                FROM ct_trade_signal
                WHERE strategy_version = ? AND symbol = ?
                ORDER BY trade_date DESC, signal_type
                LIMIT ?
                """, MAPPER, strategyVersion, symbol, limit);
    }

    @Override
    public List<SignalRow> actionable(String strategyVersion, String symbol, int limit) {
        if (symbol == null || symbol.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT signal_id, symbol, signal_time, trade_date, signal_type, signal_status, score,
                           entry_price, stop_price, suggested_position, risk_amount, signal_reason,
                           invalidation_reason, feature_version, strategy_version
                    FROM ct_trade_signal
                    WHERE strategy_version = ? AND signal_type IN ('BUY', 'SELL', 'REDUCE')
                    ORDER BY trade_date DESC, symbol, signal_type
                    LIMIT ?
                    """, MAPPER, strategyVersion, limit);
        }
        return jdbcTemplate.query("""
                SELECT signal_id, symbol, signal_time, trade_date, signal_type, signal_status, score,
                       entry_price, stop_price, suggested_position, risk_amount, signal_reason,
                       invalidation_reason, feature_version, strategy_version
                FROM ct_trade_signal
                WHERE strategy_version = ? AND symbol = ? AND signal_type <> 'NO_TRADE'
                ORDER BY trade_date DESC, signal_type
                LIMIT ?
                """, MAPPER, strategyVersion, symbol, limit);
    }

    @Override
    public Optional<SignalRow> find(String signalId) {
        List<SignalRow> rows = jdbcTemplate.query("""
                SELECT signal_id, symbol, signal_time, trade_date, signal_type, signal_status, score,
                       entry_price, stop_price, suggested_position, risk_amount, signal_reason,
                       invalidation_reason, feature_version, strategy_version
                FROM ct_trade_signal
                WHERE signal_id = ?
                """, MAPPER, signalId);
        return rows.stream().findFirst();
    }

    private static SignalRow map(ResultSet rs) throws SQLException {
        return new SignalRow(
                rs.getString("signal_id"),
                rs.getString("symbol"),
                rs.getTimestamp("signal_time").toLocalDateTime(),
                rs.getDate("trade_date").toLocalDate(),
                rs.getString("signal_type"),
                rs.getString("signal_status"),
                rs.getBigDecimal("score"),
                rs.getBigDecimal("entry_price"),
                rs.getBigDecimal("stop_price"),
                rs.getBigDecimal("suggested_position"),
                rs.getBigDecimal("risk_amount"),
                rs.getString("signal_reason"),
                rs.getString("invalidation_reason"),
                rs.getString("feature_version"),
                rs.getString("strategy_version"));
    }

    private static void bind(PreparedStatement statement, SignalRow row) throws SQLException {
        int index = 1;
        statement.setString(index++, row.signalId());
        statement.setString(index++, row.symbol());
        statement.setTimestamp(index++, Timestamp.valueOf(row.signalTime()));
        statement.setDate(index++, Date.valueOf(row.tradeDate()));
        statement.setString(index++, row.signalType());
        statement.setString(index++, row.signalStatus());
        index = decimal(statement, index, row.score());
        index = decimal(statement, index, row.entryPrice());
        index = decimal(statement, index, row.stopPrice());
        index = decimal(statement, index, row.suggestedPosition());
        index = decimal(statement, index, row.riskAmount());
        statement.setString(index++, row.signalReason());
        if (row.invalidationReason() == null) {
            statement.setNull(index++, Types.VARCHAR);
        } else {
            statement.setString(index++, row.invalidationReason());
        }
        statement.setString(index++, row.featureVersion());
        statement.setString(index, row.strategyVersion());
    }

    private static int decimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
        return index + 1;
    }
}
