package com.cti.portfolio.book;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BookService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public BookService(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
    }

    public String open(String accountId, String name, String type, String initial) {
        return transactions.execute(status -> {
            if (accountRow(accountId) != null) {
                return "duplicate";
            }
            PaperLedger ledger = new PaperLedger();
            ledger.open(accountId, name, type, initial);
            jdbc.update("""
                    INSERT INTO ct_book_account (account_id, account_name, account_type, quote_currency, status, version)
                    VALUES (?, ?, ?, 'USDT', 'ACTIVE', 0)
                    """, accountId, name, type);
            saveCash(accountId, ledger.cash(accountId), 0);
            saveEntry(accountId, ledger.entries(accountId).get(0));
            return "opened";
        });
    }

    public String deposit(String accountId, String requestId, String amount) {
        return change(accountId, requestId, ledger -> ledger.deposit(accountId, requestId, amount));
    }

    public String withdraw(String accountId, String requestId, String amount) {
        return change(accountId, requestId, ledger -> ledger.withdraw(accountId, requestId, amount));
    }

    public String fill(String accountId, String requestId, String symbol, String side, String quantity, String price, String fee) {
        return change(accountId, requestId, ledger -> ledger.fill(accountId, requestId, symbol, side, quantity, price, fee));
    }

    public String mark(String accountId, String symbol, String price) {
        return transactions.execute(status -> {
            lock(accountId);
            PaperLedger ledger = load(accountId);
            ledger.mark(accountId, symbol, price);
            savePosition(accountId, ledger.position(accountId, symbol));
            return "marked";
        });
    }

    public String disable(String accountId) {
        return transactions.execute(status -> {
            lock(accountId);
            int updated = jdbc.update("UPDATE ct_book_account SET status = 'DISABLED' WHERE account_id = ? AND status = 'ACTIVE'", accountId);
            return updated == 0 ? "duplicate" : "disabled";
        });
    }

    public List<AccountView> accounts() {
        List<String> ids = jdbc.query("SELECT account_id FROM ct_book_account ORDER BY id",
                (rs, index) -> rs.getString("account_id"));
        List<AccountView> views = new java.util.ArrayList<>();
        for (String id : ids) {
            views.add(account(id));
        }
        return views;
    }

    public AccountView account(String accountId) {
        AccountRow row = accountRow(accountId);
        if (row == null) {
            return null;
        }
        PaperLedger.Cash cash = cashRow(accountId);
        return new AccountView(row.accountId(), row.name(), row.type(), row.status(), cash.available(), cash.frozen(), cash.total());
    }

    public List<PaperLedger.LedgerEntry> ledger(String accountId) {
        if (accountRow(accountId) == null) {
            return null;
        }
        return jdbc.query("""
                SELECT request_id, entry_type, symbol, price, fee, cash_delta, balance_after, quantity_after
                FROM ct_portfolio_ledger WHERE account_id = ? ORDER BY id
                """,                 (rs, index) -> new PaperLedger.LedgerEntry(
                rs.getString("request_id"), rs.getString("entry_type"), rs.getString("symbol"),
                rs.getBigDecimal("price"), rs.getBigDecimal("fee"), rs.getBigDecimal("cash_delta"),
                rs.getBigDecimal("balance_after"), rs.getBigDecimal("quantity_after"), null), accountId);
    }

    public List<PaperLedger.Position> positions(String accountId) {
        if (accountRow(accountId) == null) {
            return null;
        }
        return jdbc.query("""
                SELECT symbol, quantity, cost_amount, average_cost, realized_pnl, market_price
                FROM ct_book_position WHERE account_id = ? ORDER BY symbol
                """, (rs, index) -> new PaperLedger.Position(
                rs.getString("symbol"), rs.getBigDecimal("quantity"), rs.getBigDecimal("cost_amount"),
                rs.getBigDecimal("average_cost"), rs.getBigDecimal("realized_pnl"), rs.getBigDecimal("market_price")), accountId);
    }

    private String change(String accountId, String requestId, Change change) {
        return transactions.execute(status -> {
            lock(accountId);
            if (jdbc.queryForObject("""
                    SELECT COUNT(*) FROM ct_portfolio_ledger WHERE account_id = ? AND request_id = ?
                    """, Integer.class, accountId, requestId) > 0) {
                return "duplicate";
            }
            PaperLedger ledger = load(accountId);
            int before = ledger.entries(accountId).size();
            change.apply(ledger);
            if (ledger.entries(accountId).size() == before) {
                return "duplicate";
            }
            PaperLedger.LedgerEntry entry = ledger.entries(accountId).get(ledger.entries(accountId).size() - 1);
            saveEntry(accountId, entry);
            if ("BUY".equals(entry.type()) || "SELL".equals(entry.type())) {
                saveLot(accountId, requestId, entry);
                savePosition(accountId, ledger.position(accountId, entry.symbol()));
            }
            saveCash(accountId, ledger.cash(accountId), cashVersion(accountId) + 1);
            return "applied";
        });
    }

    private PaperLedger load(String accountId) {
        AccountRow account = accountRow(accountId);
        Set<String> requests = new LinkedHashSet<>(jdbc.query("""
                SELECT request_id FROM ct_portfolio_ledger WHERE account_id = ?
                """, (rs, index) -> rs.getString("request_id"), accountId));
        Map<String, PaperLedger.Position> held = new LinkedHashMap<>();
        for (PaperLedger.Position position : positions(accountId)) {
            held.put(position.symbol(), position);
        }
        return PaperLedger.resume(account.accountId(), account.name(), account.type(), account.status(),
                cashRow(accountId), held, requests);
    }

    private void lock(String accountId) {
        List<String> rows = jdbc.query("SELECT account_id FROM ct_book_account WHERE account_id = ? FOR UPDATE",
                (rs, index) -> rs.getString("account_id"), accountId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("没有这个账户");
        }
    }

    private void saveCash(String accountId, PaperLedger.Cash cash, int version) {
        if (version == 0) {
            jdbc.update("""
                    INSERT INTO ct_portfolio_balance
                        (account_id, asset_code, available_balance, frozen_balance, total_balance, version)
                    VALUES (?, 'USDT', ?, ?, ?, 0)
                    """, accountId, cash.available(), cash.frozen(), cash.total());
            return;
        }
        int updated = jdbc.update("""
                UPDATE ct_portfolio_balance
                SET available_balance = ?, frozen_balance = ?, total_balance = ?, version = ?
                WHERE account_id = ? AND asset_code = 'USDT' AND version = ?
                """, cash.available(), cash.frozen(), cash.total(), version, accountId, version - 1);
        if (updated != 1) {
            throw new IllegalStateException("余额版本冲突");
        }
    }

    private void savePosition(String accountId, PaperLedger.Position position) {
        jdbc.update("""
                INSERT INTO ct_book_position (
                    account_id, symbol, quantity, available_quantity, average_cost, cost_amount,
                    market_price, market_value, unrealized_pnl, realized_pnl, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                ON DUPLICATE KEY UPDATE
                    quantity = VALUES(quantity),
                    available_quantity = VALUES(available_quantity),
                    average_cost = VALUES(average_cost),
                    cost_amount = VALUES(cost_amount),
                    market_price = VALUES(market_price),
                    market_value = VALUES(market_value),
                    unrealized_pnl = VALUES(unrealized_pnl),
                    realized_pnl = VALUES(realized_pnl),
                    version = version + 1
                """, statement -> {
            statement.setString(1, accountId);
            statement.setString(2, position.symbol());
            statement.setBigDecimal(3, position.quantity());
            statement.setBigDecimal(4, position.quantity());
            decimal(statement, 5, position.averageCost());
            statement.setBigDecimal(6, position.costAmount());
            decimal(statement, 7, position.marketPrice());
            decimal(statement, 8, position.marketValue());
            decimal(statement, 9, position.unrealizedPnl());
            statement.setBigDecimal(10, position.realizedPnl());
        });
    }

    private void saveEntry(String accountId, PaperLedger.LedgerEntry entry) {
        jdbc.update("""
                INSERT INTO ct_portfolio_ledger (
                    ledger_id, account_id, request_id, entry_type, symbol, price, fee,
                    cash_delta, balance_after, quantity_after
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, statement -> {
            statement.setString(1, sha(accountId + "|" + entry.requestId()));
            statement.setString(2, accountId);
            statement.setString(3, entry.requestId());
            statement.setString(4, entry.type());
            statement.setString(5, entry.symbol());
            decimal(statement, 6, entry.price());
            statement.setBigDecimal(7, entry.fee());
            statement.setBigDecimal(8, entry.cashDelta());
            statement.setBigDecimal(9, entry.balanceAfter());
            decimal(statement, 10, entry.quantityAfter());
        });
    }

    private void saveLot(String accountId, String requestId, PaperLedger.LedgerEntry entry) {
        jdbc.update("""
                INSERT INTO ct_position_lot (lot_id, account_id, symbol, side, quantity, price, fee, request_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,                 sha(accountId + "|lot|" + requestId), accountId, entry.symbol(), entry.type(),
                entry.fillQuantity(), entry.price(), entry.fee(), requestId);
    }

    private AccountRow accountRow(String accountId) {
        List<AccountRow> rows = jdbc.query("""
                SELECT account_id, account_name, account_type, status FROM ct_book_account WHERE account_id = ?
                """, (rs, index) -> new AccountRow(rs.getString("account_id"), rs.getString("account_name"),
                rs.getString("account_type"), rs.getString("status")), accountId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private PaperLedger.Cash cashRow(String accountId) {
        return jdbc.queryForObject("""
                SELECT available_balance, frozen_balance FROM ct_portfolio_balance
                WHERE account_id = ? AND asset_code = 'USDT'
                """, (rs, index) -> new PaperLedger.Cash(rs.getBigDecimal("available_balance"),
                rs.getBigDecimal("frozen_balance")), accountId);
    }

    private int cashVersion(String accountId) {
        return jdbc.queryForObject("""
                SELECT version FROM ct_portfolio_balance WHERE account_id = ? AND asset_code = 'USDT'
                """, Integer.class, accountId);
    }

    private static void decimal(java.sql.PreparedStatement statement, int index, BigDecimal value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
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

    private interface Change {
        void apply(PaperLedger ledger);
    }

    private record AccountRow(String accountId, String name, String type, String status) {
    }

    public record AccountView(String accountId, String name, String type, String status,
                              BigDecimal available, BigDecimal frozen, BigDecimal total) {
    }
}
