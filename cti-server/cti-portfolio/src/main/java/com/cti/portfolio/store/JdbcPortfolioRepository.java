package com.cti.portfolio.store;

import com.cti.portfolio.AccountRow;
import com.cti.portfolio.EquityRow;
import com.cti.portfolio.PortfolioRepository;
import com.cti.portfolio.PositionRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcPortfolioRepository implements PortfolioRepository {

    private static final String ACCOUNT = """
            INSERT INTO ct_portfolio_account (
                account_code, strategy_name, strategy_version, quote_asset, initial_capital,
                cash_balance, equity, peak_equity, as_of_date, risk_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                strategy_name = VALUES(strategy_name),
                quote_asset = VALUES(quote_asset),
                initial_capital = VALUES(initial_capital),
                cash_balance = VALUES(cash_balance),
                equity = VALUES(equity),
                peak_equity = VALUES(peak_equity),
                as_of_date = VALUES(as_of_date),
                risk_json = VALUES(risk_json)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;

    public JdbcPortfolioRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void replace(AccountRow account, List<PositionRow> positions, List<EquityRow> equity) {
        transactions.executeWithoutResult(status -> {
            jdbcTemplate.update(ACCOUNT, statement -> bindAccount(statement, account));
            jdbcTemplate.update(
                    "DELETE FROM ct_portfolio_position WHERE account_code = ? AND strategy_version = ?",
                    account.accountCode(), account.strategyVersion());
            jdbcTemplate.update(
                    "DELETE FROM ct_portfolio_equity WHERE account_code = ? AND strategy_version = ?",
                    account.accountCode(), account.strategyVersion());
            jdbcTemplate.batchUpdate("""
                    INSERT INTO ct_portfolio_position (
                        account_code, strategy_version, symbol, quantity, avg_entry_price, stop_price,
                        highest_close, opened_on, updated_on, market_value
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, positions, 200, (statement, row) -> bindPosition(statement, account, row));
            jdbcTemplate.batchUpdate("""
                    INSERT INTO ct_portfolio_equity (
                        account_code, strategy_version, trade_date, cash_balance, equity, peak_equity, drawdown, exposure
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, equity, 200, (statement, row) -> bindEquity(statement, account, row));
        });
    }

    @Override
    public void updateRisk(String accountCode, String strategyVersion, String riskJson) {
        int updated = jdbcTemplate.update(
                "UPDATE ct_portfolio_account SET risk_json = ? WHERE account_code = ? AND strategy_version = ?",
                riskJson, accountCode, strategyVersion);
        if (updated != 1) {
            throw new IllegalStateException("没有研究账户可更新");
        }
    }

    @Override
    public Optional<AccountRow> account(String accountCode, String strategyVersion) {
        List<AccountRow> rows = jdbcTemplate.query("""
                SELECT account_code, strategy_name, strategy_version, quote_asset, initial_capital,
                       cash_balance, equity, peak_equity, as_of_date, risk_json
                FROM ct_portfolio_account
                WHERE account_code = ? AND strategy_version = ?
                """, (rs, row) -> new AccountRow(
                rs.getString("account_code"),
                rs.getString("strategy_name"),
                rs.getString("strategy_version"),
                rs.getString("quote_asset"),
                rs.getBigDecimal("initial_capital"),
                rs.getBigDecimal("cash_balance"),
                rs.getBigDecimal("equity"),
                rs.getBigDecimal("peak_equity"),
                rs.getDate("as_of_date") == null ? null : rs.getDate("as_of_date").toLocalDate(),
                rs.getString("risk_json")), accountCode, strategyVersion);
        return rows.stream().findFirst();
    }

    @Override
    public List<PositionRow> positions(String accountCode, String strategyVersion) {
        return jdbcTemplate.query("""
                SELECT symbol, quantity, avg_entry_price, stop_price, highest_close, opened_on, updated_on, market_value
                FROM ct_portfolio_position
                WHERE account_code = ? AND strategy_version = ?
                ORDER BY symbol
                """, (rs, row) -> new PositionRow(
                rs.getString("symbol"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("avg_entry_price"),
                rs.getBigDecimal("stop_price"),
                rs.getBigDecimal("highest_close"),
                rs.getDate("opened_on").toLocalDate(),
                rs.getDate("updated_on").toLocalDate(),
                rs.getBigDecimal("market_value")), accountCode, strategyVersion);
    }

    @Override
    public List<EquityRow> equity(String accountCode, String strategyVersion, int limit) {
        return jdbcTemplate.query("""
                SELECT trade_date, cash_balance, equity, peak_equity, drawdown, exposure
                FROM ct_portfolio_equity
                WHERE account_code = ? AND strategy_version = ?
                ORDER BY trade_date DESC
                LIMIT ?
                """, (rs, row) -> new EquityRow(
                rs.getDate("trade_date").toLocalDate(),
                rs.getBigDecimal("cash_balance"),
                rs.getBigDecimal("equity"),
                rs.getBigDecimal("peak_equity"),
                rs.getBigDecimal("drawdown"),
                rs.getBigDecimal("exposure")), accountCode, strategyVersion, limit);
    }

    private static void bindAccount(PreparedStatement statement, AccountRow account) throws java.sql.SQLException {
        statement.setString(1, account.accountCode());
        statement.setString(2, account.strategyName());
        statement.setString(3, account.strategyVersion());
        statement.setString(4, account.quoteAsset());
        statement.setBigDecimal(5, account.initialCapital());
        statement.setBigDecimal(6, account.cash());
        statement.setBigDecimal(7, account.equity());
        statement.setBigDecimal(8, account.peakEquity());
        if (account.asOfDate() == null) {
            statement.setNull(9, Types.DATE);
        } else {
            statement.setDate(9, Date.valueOf(account.asOfDate()));
        }
        if (account.riskJson() == null) {
            statement.setNull(10, Types.VARCHAR);
        } else {
            statement.setString(10, account.riskJson());
        }
    }

    private static void bindPosition(PreparedStatement statement, AccountRow account, PositionRow row) throws java.sql.SQLException {
        statement.setString(1, account.accountCode());
        statement.setString(2, account.strategyVersion());
        statement.setString(3, row.symbol());
        statement.setBigDecimal(4, row.quantity());
        statement.setBigDecimal(5, row.averageEntryPrice());
        statement.setBigDecimal(6, row.stopPrice());
        statement.setBigDecimal(7, row.highestClose());
        statement.setDate(8, Date.valueOf(row.openedOn()));
        statement.setDate(9, Date.valueOf(row.updatedOn()));
        statement.setBigDecimal(10, row.marketValue());
    }

    private static void bindEquity(PreparedStatement statement, AccountRow account, EquityRow row) throws java.sql.SQLException {
        statement.setString(1, account.accountCode());
        statement.setString(2, account.strategyVersion());
        statement.setDate(3, Date.valueOf(row.tradeDate()));
        statement.setBigDecimal(4, row.cash());
        statement.setBigDecimal(5, row.equity());
        statement.setBigDecimal(6, row.peakEquity());
        statement.setBigDecimal(7, row.drawdown());
        statement.setBigDecimal(8, row.exposure());
    }
}
