package com.cti.portfolio.book;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PaperLedger {

    private static final int SCALE = 12;
    private static final RoundingMode ROUND = RoundingMode.HALF_UP;

    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Map<String, Cash> cash = new LinkedHashMap<>();
    private final Map<String, Map<String, Position>> positions = new LinkedHashMap<>();
    private final Map<String, List<LedgerEntry>> entries = new LinkedHashMap<>();
    private final Map<String, Set<String>> requests = new LinkedHashMap<>();

    public void open(String accountId, String name, String type, String initial) {
        requireText(accountId, "账户");
        if (!"PAPER".equals(type) && !"CUSTOM".equals(type)) {
            throw new IllegalArgumentException("账户类型只接受 PAPER 或 CUSTOM");
        }
        if (accounts.containsKey(accountId)) {
            throw new IllegalStateException("账户已存在");
        }
        BigDecimal amount = money(initial, false);
        accounts.put(accountId, new Account(accountId, name, type, "ACTIVE"));
        cash.put(accountId, new Cash(amount, money("0", false)));
        positions.put(accountId, new LinkedHashMap<>());
        entries.put(accountId, new ArrayList<>());
        requests.put(accountId, new java.util.LinkedHashSet<>());
        post(accountId, "open:" + accountId, "OPENING", null, null, null, money("0", false), amount, amount, null);
    }

    public void deposit(String accountId, String requestId, String amount) {
        Account account = active(accountId);
        if (!first(account.accountId(), requestId)) {
            return;
        }
        BigDecimal credit = money(amount, true);
        Cash next = cash.get(accountId).add(credit);
        cash.put(accountId, next);
        post(accountId, requestId, "DEPOSIT", null, null, null, money("0", false), credit, next.available(), null);
    }

    public void withdraw(String accountId, String requestId, String amount) {
        active(accountId);
        if (!first(accountId, requestId)) {
            return;
        }
        BigDecimal debit = money(amount, true);
        Cash current = cash.get(accountId);
        if (current.available().compareTo(debit) < 0) {
            throw new IllegalStateException("可用余额不足");
        }
        Cash next = current.add(debit.negate());
        cash.put(accountId, next);
        post(accountId, requestId, "WITHDRAW", null, null, null, money("0", false), debit.negate(), next.available(), null);
    }

    public void fill(String accountId, String requestId, String symbol, String side, String quantity, String price, String fee) {
        active(accountId);
        if (!first(accountId, requestId)) {
            return;
        }
        BigDecimal qty = money(quantity, true);
        BigDecimal px = money(price, true);
        BigDecimal costFee = money(fee, false);
        requireText(symbol, "交易对");
        Map<String, Position> book = positions.get(accountId);
        Position current = book.getOrDefault(symbol, Position.empty(symbol));
        if ("BUY".equals(side)) {
            BigDecimal traded = qty.multiply(px).setScale(SCALE, ROUND);
            BigDecimal totalCost = traded.add(costFee).setScale(SCALE, ROUND);
            if (cash.get(accountId).available().compareTo(totalCost) < 0) {
                throw new IllegalStateException("可用余额不足");
            }
            BigDecimal costAmount = current.costAmount().add(totalCost).setScale(SCALE, ROUND);
            BigDecimal owned = current.quantity().add(qty).setScale(SCALE, ROUND);
            Position next = current.bought(owned, costAmount, costAmount.divide(owned, SCALE, ROUND));
            book.put(symbol, next);
            Cash after = cash.get(accountId).add(totalCost.negate());
            cash.put(accountId, after);
            post(accountId, requestId, "BUY", symbol, px, qty, costFee, totalCost.negate(), after.available(), next.quantity());
            return;
        }
        if (!"SELL".equals(side)) {
            throw new IllegalArgumentException("方向只接受 BUY 或 SELL");
        }
        if (current.quantity().compareTo(qty) < 0) {
            throw new IllegalStateException("持仓数量不足");
        }
        BigDecimal proceeds = qty.multiply(px).setScale(SCALE, ROUND).subtract(costFee).setScale(SCALE, ROUND);
        if (proceeds.signum() < 0) {
            throw new IllegalArgumentException("手续费不能大于成交额");
        }
        BigDecimal relieved = current.quantity().compareTo(qty) == 0
                ? current.costAmount()
                : current.costAmount().multiply(qty).divide(current.quantity(), SCALE, ROUND);
        BigDecimal owned = current.quantity().subtract(qty).setScale(SCALE, ROUND);
        BigDecimal costAmount = current.costAmount().subtract(relieved).setScale(SCALE, ROUND);
        BigDecimal average = owned.signum() == 0 ? null : costAmount.divide(owned, SCALE, ROUND);
        BigDecimal realized = proceeds.subtract(relieved).setScale(SCALE, ROUND);
        Position next = current.sold(owned, costAmount, average, realized);
        book.put(symbol, next);
        Cash after = cash.get(accountId).add(proceeds);
        cash.put(accountId, after);
        post(accountId, requestId, "SELL", symbol, px, qty, costFee, proceeds, after.available(), next.quantity());
    }

    public Cash cash(String accountId) {
        return cash.get(accountId);
    }

    public Position position(String accountId, String symbol) {
        return positions.get(accountId).get(symbol);
    }

    public List<LedgerEntry> entries(String accountId) {
        return List.copyOf(entries.get(accountId));
    }

    public void mark(String accountId, String symbol, String price) {
        active(accountId);
        Position current = positions.get(accountId).get(symbol);
        if (current == null) {
            throw new IllegalArgumentException("没有这个持仓");
        }
        BigDecimal px = money(price, true);
        positions.get(accountId).put(symbol, new Position(symbol, current.quantity(), current.costAmount(),
                current.averageCost(), current.realizedPnl(), px));
    }

    public void disable(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new IllegalArgumentException("没有这个账户");
        }
        accounts.put(accountId, new Account(account.accountId(), account.name(), account.type(), "DISABLED"));
    }

    public Account account(String accountId) {
        return accounts.get(accountId);
    }

    public Map<String, Position> positions(String accountId) {
        return Map.copyOf(positions.get(accountId));
    }

    public static PaperLedger resume(String accountId, String name, String type, String status, Cash cashBalance,
                                     Map<String, Position> held, Set<String> appliedRequests) {
        PaperLedger ledger = new PaperLedger();
        ledger.accounts.put(accountId, new Account(accountId, name, type, status));
        ledger.cash.put(accountId, cashBalance);
        ledger.positions.put(accountId, new LinkedHashMap<>(held));
        ledger.entries.put(accountId, new ArrayList<>());
        ledger.requests.put(accountId, new java.util.LinkedHashSet<>(appliedRequests));
        return ledger;
    }

    public boolean reconciled(String accountId) {
        BigDecimal sum = money("0", false);
        for (LedgerEntry entry : entries.get(accountId)) {
            sum = sum.add(entry.cashDelta());
        }
        Cash current = cash.get(accountId);
        return sum.compareTo(current.available()) == 0 && current.frozen().signum() == 0
                && current.total().compareTo(current.available().add(current.frozen())) == 0;
    }

    private boolean first(String accountId, String requestId) {
        requireText(requestId, "请求号");
        return !requests.get(accountId).contains(requestId);
    }

    private Account active(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new IllegalArgumentException("没有这个账户");
        }
        if (!"ACTIVE".equals(account.status())) {
            throw new IllegalStateException("账户已停用");
        }
        return account;
    }

    private void post(String accountId, String requestId, String type, String symbol, BigDecimal price,
                      BigDecimal fillQuantity, BigDecimal fee, BigDecimal cashDelta, BigDecimal balanceAfter,
                      BigDecimal quantityAfter) {
        requests.get(accountId).add(requestId);
        entries.get(accountId).add(new LedgerEntry(requestId, type, symbol, price, fee, cashDelta.setScale(SCALE, ROUND),
                balanceAfter.setScale(SCALE, ROUND), quantityAfter == null ? null : quantityAfter.setScale(SCALE, ROUND),
                fillQuantity == null ? null : fillQuantity.setScale(SCALE, ROUND)));
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }

    private static BigDecimal money(String value, boolean positive) {
        BigDecimal parsed = new BigDecimal(value).setScale(SCALE, ROUND);
        if (positive && parsed.signum() <= 0) {
            throw new IllegalArgumentException("数量和价格必须大于 0");
        }
        if (parsed.signum() < 0) {
            throw new IllegalArgumentException("金额不能为负");
        }
        return parsed;
    }

    public record Account(String accountId, String name, String type, String status) {
    }

    public record Cash(BigDecimal available, BigDecimal frozen) {
        public Cash add(BigDecimal availableDelta) {
            return new Cash(available.add(availableDelta).setScale(SCALE, ROUND), frozen);
        }

        public BigDecimal total() {
            return available.add(frozen).setScale(SCALE, ROUND);
        }
    }

    public record Position(String symbol, BigDecimal quantity, BigDecimal costAmount, BigDecimal averageCost,
                           BigDecimal realizedPnl, BigDecimal marketPrice) {
        static Position empty(String symbol) {
            return new Position(symbol, new BigDecimal("0.000000000000"), new BigDecimal("0.000000000000"),
                    null, new BigDecimal("0.000000000000"), null);
        }

        Position bought(BigDecimal quantity, BigDecimal costAmount, BigDecimal averageCost) {
            return new Position(symbol, quantity, costAmount, averageCost, realizedPnl, marketPrice);
        }

        Position sold(BigDecimal quantity, BigDecimal costAmount, BigDecimal averageCost, BigDecimal realizedDelta) {
            return new Position(symbol, quantity, costAmount, averageCost,
                    realizedPnl.add(realizedDelta).setScale(SCALE, ROUND), marketPrice);
        }

        public BigDecimal marketValue() {
            if (marketPrice == null) {
                return null;
            }
            return quantity.multiply(marketPrice).setScale(SCALE, ROUND);
        }

        public BigDecimal unrealizedPnl() {
            if (marketPrice == null) {
                return null;
            }
            return marketValue().subtract(costAmount).setScale(SCALE, ROUND);
        }
    }

    public record LedgerEntry(String requestId, String type, String symbol, BigDecimal price, BigDecimal fee,
                              BigDecimal cashDelta, BigDecimal balanceAfter, BigDecimal quantityAfter,
                              BigDecimal fillQuantity) {
    }
}
