package com.cti.backtest.store;

import com.cti.backtest.BacktestRepository;
import com.cti.backtest.EquityRow;
import com.cti.backtest.RunSummary;
import com.cti.backtest.SavedRun;
import com.cti.backtest.TradeRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcBacktestRepository implements BacktestRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;

    public JdbcBacktestRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void save(SavedRun run) {
        transactions.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO ct_backtest_run (
                        run_id, strategy_name, strategy_version, start_date, end_date, initial_capital,
                        commission_rate, slippage_rate, status, config_json, finished_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, statement -> {
                statement.setString(1, run.runId());
                statement.setString(2, run.strategyName());
                statement.setString(3, run.strategyVersion());
                statement.setDate(4, Date.valueOf(run.startDate()));
                statement.setDate(5, Date.valueOf(run.endDate()));
                decimal(statement, 6, run.initialCapital());
                decimal(statement, 7, run.commissionRate());
                decimal(statement, 8, run.slippageRate());
                statement.setString(9, run.status());
                statement.setString(10, run.configJson());
                statement.setTimestamp(11, Timestamp.from(Instant.now()));
            });
            jdbcTemplate.update("""
                    INSERT INTO ct_backtest_result (
                        run_id, total_return, annualized_return, annualized_volatility, sharpe_ratio, sortino_ratio,
                        max_drawdown, win_rate, profit_factor, total_trades, average_holding_days, total_fee,
                        total_slippage, benchmark_return, excess_return, report_json
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, statement -> bindResult(statement, run));
            jdbcTemplate.batchUpdate("""
                    INSERT INTO ct_backtest_trade (
                        run_id, trade_date, symbol, side, quantity, open_price, fill_price, fee, slippage, pnl, opened_on, holding_days
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, run.trades(), 200, (statement, row) -> bindTrade(statement, run.runId(), row));
            jdbcTemplate.batchUpdate("""
                    INSERT INTO ct_backtest_equity (
                        run_id, trade_date, cash_balance, equity, peak_equity, drawdown, exposure
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, run.equity(), 200, (statement, row) -> bindEquity(statement, run.runId(), row));
        });
    }

    @Override
    public List<RunSummary> recent(int limit) {
        return jdbcTemplate.query(SELECT + """
                 ORDER BY r.finished_at DESC
                 LIMIT ?
                """, (rs, row) -> map(rs), limit);
    }

    @Override
    public Optional<RunSummary> find(String runId) {
        List<RunSummary> rows = jdbcTemplate.query(SELECT + " WHERE r.run_id = ?", (rs, row) -> map(rs), runId);
        return rows.stream().findFirst();
    }

    @Override
    public List<TradeRow> trades(String runId) {
        return jdbcTemplate.query("""
                SELECT trade_date, symbol, side, quantity, open_price, fill_price, fee, slippage, pnl, opened_on, holding_days
                FROM ct_backtest_trade
                WHERE run_id = ?
                ORDER BY trade_date, symbol, side
                """, (rs, row) -> new TradeRow(
                rs.getDate("trade_date").toLocalDate(),
                rs.getString("symbol"),
                rs.getString("side"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("open_price"),
                rs.getBigDecimal("fill_price"),
                rs.getBigDecimal("fee"),
                rs.getBigDecimal("slippage"),
                rs.getBigDecimal("pnl"),
                rs.getDate("opened_on") == null ? null : rs.getDate("opened_on").toLocalDate(),
                rs.getObject("holding_days") == null ? null : rs.getInt("holding_days")), runId);
    }

    @Override
    public List<EquityRow> equity(String runId, int limit) {
        return jdbcTemplate.query("""
                SELECT trade_date, cash_balance, equity, peak_equity, drawdown, exposure
                FROM ct_backtest_equity
                WHERE run_id = ?
                ORDER BY trade_date DESC
                LIMIT ?
                """, (rs, row) -> new EquityRow(
                rs.getDate("trade_date").toLocalDate(),
                rs.getBigDecimal("cash_balance"),
                rs.getBigDecimal("equity"),
                rs.getBigDecimal("peak_equity"),
                rs.getBigDecimal("drawdown"),
                rs.getBigDecimal("exposure")), runId, limit);
    }

    private static final String SELECT = """
            SELECT r.run_id, r.strategy_name, r.strategy_version, r.start_date, r.end_date, r.initial_capital,
                   r.commission_rate, r.slippage_rate, r.status, r.config_json,
                   s.report_json, s.total_return, s.annualized_return, s.annualized_volatility, s.sharpe_ratio,
                   s.sortino_ratio, s.max_drawdown, s.win_rate, s.profit_factor, s.total_trades,
                   s.average_holding_days, s.total_fee, s.total_slippage, s.benchmark_return, s.excess_return
            FROM ct_backtest_run r
            LEFT JOIN ct_backtest_result s ON s.run_id = r.run_id
            """;

    private static RunSummary map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RunSummary(
                rs.getString("run_id"), rs.getString("strategy_name"), rs.getString("strategy_version"),
                rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(),
                rs.getBigDecimal("initial_capital"), rs.getBigDecimal("commission_rate"), rs.getBigDecimal("slippage_rate"),
                rs.getString("status"), rs.getString("config_json"), rs.getString("report_json"),
                rs.getBigDecimal("total_return"), rs.getBigDecimal("annualized_return"), rs.getBigDecimal("annualized_volatility"),
                rs.getBigDecimal("sharpe_ratio"), rs.getBigDecimal("sortino_ratio"), rs.getBigDecimal("max_drawdown"),
                rs.getBigDecimal("win_rate"), rs.getBigDecimal("profit_factor"),
                rs.getObject("total_trades") == null ? null : rs.getInt("total_trades"),
                rs.getBigDecimal("average_holding_days"), rs.getBigDecimal("total_fee"), rs.getBigDecimal("total_slippage"),
                rs.getBigDecimal("benchmark_return"), rs.getBigDecimal("excess_return"));
    }

    private static void bindResult(PreparedStatement statement, SavedRun run) throws java.sql.SQLException {
        statement.setString(1, run.runId());
        decimal(statement, 2, run.totalReturn());
        decimal(statement, 3, run.annualizedReturn());
        decimal(statement, 4, run.annualizedVolatility());
        decimal(statement, 5, run.sharpeRatio());
        decimal(statement, 6, run.sortinoRatio());
        decimal(statement, 7, run.maxDrawdown());
        decimal(statement, 8, run.winRate());
        decimal(statement, 9, run.profitFactor());
        if (run.totalTrades() == null) {
            statement.setNull(10, Types.INTEGER);
        } else {
            statement.setInt(10, run.totalTrades());
        }
        decimal(statement, 11, run.averageHoldingDays());
        decimal(statement, 12, run.totalFee());
        decimal(statement, 13, run.totalSlippage());
        decimal(statement, 14, run.benchmarkReturn());
        decimal(statement, 15, run.excessReturn());
        statement.setString(16, run.reportJson());
    }

    private static void bindTrade(PreparedStatement statement, String runId, TradeRow row) throws java.sql.SQLException {
        statement.setString(1, runId);
        statement.setDate(2, Date.valueOf(row.tradeDate()));
        statement.setString(3, row.symbol());
        statement.setString(4, row.side());
        statement.setBigDecimal(5, row.quantity());
        statement.setBigDecimal(6, row.openPrice());
        statement.setBigDecimal(7, row.fillPrice());
        statement.setBigDecimal(8, row.fee());
        statement.setBigDecimal(9, row.slippage());
        decimal(statement, 10, row.pnl());
        if (row.openedOn() == null) {
            statement.setNull(11, Types.DATE);
        } else {
            statement.setDate(11, Date.valueOf(row.openedOn()));
        }
        if (row.holdingDays() == null) {
            statement.setNull(12, Types.INTEGER);
        } else {
            statement.setInt(12, row.holdingDays());
        }
    }

    private static void bindEquity(PreparedStatement statement, String runId, EquityRow row) throws java.sql.SQLException {
        statement.setString(1, runId);
        statement.setDate(2, Date.valueOf(row.tradeDate()));
        statement.setBigDecimal(3, row.cash());
        statement.setBigDecimal(4, row.equity());
        statement.setBigDecimal(5, row.peakEquity());
        statement.setBigDecimal(6, row.drawdown());
        statement.setBigDecimal(7, row.exposure());
    }

    private static void decimal(PreparedStatement statement, int index, BigDecimal value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
    }
}
