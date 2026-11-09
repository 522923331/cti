package com.cti.portfolio.book;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaperOrderService {

    private static final int SCALE = 12;
    private static final RoundingMode ROUND = RoundingMode.HALF_UP;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final BookService books;
    private final AllocationClient allocations;

    public PaperOrderService(JdbcTemplate jdbc, PlatformTransactionManager manager, BookService books, AllocationClient allocations) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.books = books;
        this.allocations = allocations;
    }

    public Map<String, String> submit(String accountId, Map<String, String> body) {
        return transactions.execute(status -> {
            lock(accountId);
            String strategy = required(body, "strategyCode");
            String signal = required(body, "signalId");
            String side = required(body, "side");
            String type = required(body, "orderType");
            String symbol = required(body, "symbol");
            if (!"BUY".equals(side) && !"SELL".equals(side)) {
                throw new IllegalArgumentException("方向只接受 BUY 或 SELL");
            }
            if (!"MARKET".equals(type) && !"LIMIT".equals(type)) {
                throw new IllegalArgumentException("订单类型只接受 MARKET 或 LIMIT");
            }
            String existing = findOrder(accountId, strategy, signal, side);
            if (existing != null) {
                Map<String, String> view = order(existing);
                view.put("result", "duplicate");
                return view;
            }
            BigDecimal quantity = money(required(body, "quantity"), false);
            BigDecimal feeRate = body.get("feeRate") == null ? new BigDecimal("0.001") : money(body.get("feeRate"), true);
            BigDecimal slippage = body.get("slippageBps") == null ? new BigDecimal("8") : money(body.get("slippageBps"), true);
            String orderId = sha(accountId + "|" + strategy + "|" + signal + "|" + side);
            if ("LIMIT".equals(type)) {
                BigDecimal limit = money(required(body, "limitPrice"), false);
                if (body.get("barLow") != null && PaperMatch.limitTouched(side, limit, money(body.get("barHigh"), false), money(body.get("barLow"), true))) {
                    return execute(accountId, orderId, strategy, signal, symbol, side, type, quantity, limit, feeRate, slippage, null, null);
                }
                insertOrder(orderId, accountId, strategy, signal, symbol, side, type, "SUBMITTED", quantity, BigDecimal.ZERO.setScale(SCALE, ROUND), limit, null, null, feeRate, slippage, null);
                return viewOf(orderId, "created");
            }
            if (body.get("barOpen") == null) {
                throw new IllegalArgumentException("市价单需要下一根 K 线的开盘价");
            }
            BigDecimal price = PaperMatch.marketPrice(side, money(body.get("barOpen"), false), slippage);
            return execute(accountId, orderId, strategy, signal, symbol, side, type, quantity, price, feeRate, slippage, null, null);
        });
    }

    public Map<String, String> match(String accountId, String orderId, Map<String, String> body) {
        return transactions.execute(status -> {
            lock(accountId);
            OrderRow row = requireOrder(accountId, orderId);
            if (!"LIMIT".equals(row.type()) || !"SUBMITTED".equals(row.status()) && !"PARTIALLY_FILLED".equals(row.status())) {
                Map<String, String> view = order(orderId);
                view.put("result", "ignored");
                return view;
            }
            BigDecimal high = money(required(body, "barHigh"), false);
            BigDecimal low = money(required(body, "barLow"), true);
            if (!PaperMatch.limitTouched(row.side(), row.limitPrice(), high, low)) {
                Map<String, String> view = order(orderId);
                view.put("result", "untouched");
                return view;
            }
            BigDecimal remaining = row.quantity().subtract(row.filled());
            BigDecimal quantity = PaperMatch.fillable(remaining, body.get("quantity") == null ? null : new BigDecimal(body.get("quantity")));
            String requestId = orderId + ":" + row.filled().toPlainString();
            return execute(accountId, orderId, row.strategy(), row.signal(), row.symbol(), row.side(), row.type(), quantity, row.limitPrice(), row.feeRate(), BigDecimal.ZERO.setScale(SCALE, ROUND), row, requestId);
        });
    }

    public Map<String, String> protect(String accountId, Map<String, String> body) {
        BigDecimal stop = body.get("stopPrice") == null ? null : money(body.get("stopPrice"), false);
        BigDecimal target = body.get("takeProfitPrice") == null ? null : money(body.get("takeProfitPrice"), false);
        String decision = PaperMatch.protectiveExit(stop, target, money(required(body, "barHigh"), false), money(required(body, "barLow"), true));
        if ("NONE".equals(decision)) {
            return Map.of("result", "none");
        }
        BigDecimal trigger = "STOP".equals(decision) ? stop : target;
        Map<String, String> order = new LinkedHashMap<>(body);
        order.put("side", "SELL");
        order.put("orderType", "MARKET");
        order.put("signalId", required(body, "signalId") + ":" + decision);
        order.put("barOpen", trigger.toPlainString());
        Map<String, String> placed = submit(accountId, order);
        placed.put("exit", decision);
        return placed;
    }

    public Map<String, String> cancel(String accountId, String orderId) {
        return transactions.execute(status -> {
            lock(accountId);
            OrderRow row = requireOrder(accountId, orderId);
            if ("FILLED".equals(row.status()) || "REJECTED".equals(row.status())) {
                throw new IllegalStateException("这个订单不能取消");
            }
            if (!"CANCELED".equals(row.status())) {
                jdbc.update("UPDATE ct_paper_order SET status = 'CANCELED' WHERE order_id = ?", orderId);
            }
            Map<String, String> view = order(orderId);
            view.put("result", "CANCELED".equals(row.status()) ? "duplicate" : "canceled");
            return view;
        });
    }

    public List<Map<String, String>> orders(String accountId) {
        if (books.account(accountId) == null) {
            return null;
        }
        return jdbc.query("""
                SELECT order_id FROM ct_paper_order WHERE account_id = ? ORDER BY id
                """, (rs, index) -> order(rs.getString("order_id")), accountId);
    }

    public JsonNode allocate(String accountId, JsonNode payload) {
        return transactions.execute(status -> {
            lock(accountId);
            JsonNode plan = allocations.allocate(payload);
            String planId = sha(accountId + "|" + plan.toString());
            Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM ct_allocation_plan WHERE plan_id = ?", Integer.class, planId);
            if (existing != null && existing > 0) {
                ((ObjectNode) plan).put("result", "duplicate");
                ((ObjectNode) plan).put("planId", planId);
                return plan;
            }
            jdbc.update("""
                    INSERT INTO ct_allocation_plan (plan_id, account_id, regime, reason, cash_weight, config_json)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, planId, accountId, payload.path("regime").asText(), plan.path("reason").asText(),
                    new BigDecimal(plan.path("cashWeight").asText()), payload.path("config").toString());
            BigDecimal btc = BigDecimal.ZERO;
            BigDecimal eth = BigDecimal.ZERO;
            BigDecimal alt = BigDecimal.ZERO;
            BigDecimal concentration = BigDecimal.ZERO;
            for (JsonNode target : plan.path("targets")) {
                BigDecimal weight = new BigDecimal(target.path("targetWeight").asText());
                jdbc.update("""
                        INSERT INTO ct_allocation_detail (plan_id, symbol, asset_group, target_weight, target_value)
                        VALUES (?, ?, ?, ?, ?)
                        """, planId, target.path("symbol").asText(), target.path("group").asText(), weight,
                        new BigDecimal(target.path("targetValue").asText()));
                if ("BTC".equals(target.path("group").asText())) {
                    btc = btc.add(weight);
                } else if ("ETH".equals(target.path("group").asText())) {
                    eth = eth.add(weight);
                } else {
                    alt = alt.add(weight);
                }
                if (weight.compareTo(concentration) > 0) {
                    concentration = weight;
                }
            }
            BigDecimal cashWeight = new BigDecimal(plan.path("cashWeight").asText());
            String correlation = plan.path("correlation").isMissingNode() ? null : plan.path("correlation").toString();
            jdbc.update("""
                    INSERT INTO ct_risk_snapshot (
                        snapshot_id, account_id, plan_id, exposure, cash_weight, btc_weight, eth_weight,
                        alt_weight, concentration, correlation_json, var_value, cvar_value
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)
                    """, sha(planId + "|risk"), accountId, planId, BigDecimal.ONE.subtract(cashWeight).setScale(SCALE, ROUND),
                    cashWeight, btc, eth, alt, concentration, correlation);
            ((ObjectNode) plan).put("result", "accepted");
            ((ObjectNode) plan).put("planId", planId);
            return plan;
        });
    }

    private Map<String, String> execute(String accountId, String orderId, String strategy, String signal, String symbol,
                                        String side, String type, BigDecimal quantity, BigDecimal price, BigDecimal feeRate,
                                        BigDecimal slippage, OrderRow existing, String requestId) {
        BigDecimal notional = price.multiply(quantity).setScale(SCALE, ROUND);
        BigDecimal fee = PaperMatch.fee(notional, feeRate);
        if ("BUY".equals(side) && notional.add(fee).compareTo(available(accountId)) > 0) {
            return reject(accountId, orderId, strategy, signal, symbol, side, type, quantity, price, feeRate, slippage, "现金不足");
        }
        if ("SELL".equals(side) && quantity.compareTo(held(accountId, symbol)) > 0) {
            return reject(accountId, orderId, strategy, signal, symbol, side, type, quantity, price, feeRate, slippage, "持仓数量不足");
        }
        String ledgerRequest = requestId == null ? orderId : sha(requestId);
        String applied = books.fill(accountId, ledgerRequest, symbol, side, quantity.toPlainString(), price.toPlainString(), fee.toPlainString());
        if (!"applied".equals(applied)) {
            throw new IllegalStateException("成交没有入账");
        }
        BigDecimal filled = existing == null ? quantity : existing.filled().add(quantity).setScale(SCALE, ROUND);
        BigDecimal ordered = existing == null ? quantity : existing.quantity();
        String nextStatus = filled.compareTo(ordered) == 0 ? "FILLED" : "PARTIALLY_FILLED";
        if (existing == null) {
            insertOrder(orderId, accountId, strategy, signal, symbol, side, type, nextStatus, ordered, filled, "LIMIT".equals(type) ? price : null, null, null, feeRate, slippage, null);
        } else {
            jdbc.update("UPDATE ct_paper_order SET status = ?, filled_quantity = ? WHERE order_id = ?", nextStatus, filled, orderId);
        }
        String fillId = sha(orderId + "|" + ledgerRequest);
        jdbc.update("""
                INSERT INTO ct_paper_fill (fill_id, order_id, account_id, request_id, price, quantity, fee, slippage_bps)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, fillId, orderId, accountId, ledgerRequest, price, quantity, fee, slippage);
        jdbc.update("""
                INSERT INTO ct_portfolio_trade (trade_id, account_id, order_id, fill_id, ledger_request_id)
                VALUES (?, ?, ?, ?, ?)
                """, sha(fillId + "|trade"), accountId, orderId, fillId, ledgerRequest);
        Map<String, String> view = order(orderId);
        view.put("result", "filled");
        view.put("fillPrice", price.toPlainString());
        view.put("fee", fee.toPlainString());
        return view;
    }

    private Map<String, String> reject(String accountId, String orderId, String strategy, String signal, String symbol,
                                       String side, String type, BigDecimal quantity, BigDecimal price, BigDecimal feeRate,
                                       BigDecimal slippage, String reason) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ct_paper_order WHERE order_id = ?", Integer.class, orderId);
        if (count == null || count == 0) {
            insertOrder(orderId, accountId, strategy, signal, symbol, side, type, "REJECTED", quantity, BigDecimal.ZERO.setScale(SCALE, ROUND), "LIMIT".equals(type) ? price : null, null, null, feeRate, slippage, reason);
        }
        Map<String, String> view = order(orderId);
        view.put("result", "rejected");
        return view;
    }

    private void insertOrder(String orderId, String accountId, String strategy, String signal, String symbol, String side,
                             String type, String status, BigDecimal quantity, BigDecimal filled, BigDecimal limit,
                             BigDecimal stop, BigDecimal target, BigDecimal feeRate, BigDecimal slippage, String reason) {
        jdbc.update("""
                INSERT INTO ct_paper_order (
                    order_id, account_id, strategy_code, signal_id, symbol, side, order_type, status,
                    quantity, filled_quantity, limit_price, stop_price, take_profit_price, fee_rate, slippage_bps, reject_reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, orderId, accountId, strategy, signal, symbol, side, type, status, quantity, filled, limit, stop, target, feeRate, slippage, reason);
    }

    private Map<String, String> viewOf(String orderId, String result) {
        Map<String, String> view = order(orderId);
        view.put("result", result);
        return view;
    }

    private Map<String, String> order(String orderId) {
        return jdbc.queryForObject("""
                SELECT order_id, status, symbol, side, order_type, quantity, filled_quantity, reject_reason
                FROM ct_paper_order WHERE order_id = ?
                """, (rs, index) -> {
            Map<String, String> view = new LinkedHashMap<>();
            view.put("orderId", rs.getString("order_id"));
            view.put("status", rs.getString("status"));
            view.put("symbol", rs.getString("symbol"));
            view.put("side", rs.getString("side"));
            view.put("orderType", rs.getString("order_type"));
            view.put("quantity", rs.getBigDecimal("quantity").toPlainString());
            view.put("filledQuantity", rs.getBigDecimal("filled_quantity").toPlainString());
            if (rs.getString("reject_reason") != null) {
                view.put("rejectReason", rs.getString("reject_reason"));
            }
            return view;
        }, orderId);
    }

    private OrderRow requireOrder(String accountId, String orderId) {
        List<OrderRow> rows = jdbc.query("""
                SELECT order_id, account_id, strategy_code, signal_id, symbol, side, order_type, status,
                       quantity, filled_quantity, limit_price, fee_rate
                FROM ct_paper_order WHERE order_id = ? AND account_id = ?
                """, (rs, index) -> new OrderRow(rs.getString("order_id"), rs.getString("strategy_code"), rs.getString("signal_id"),
                rs.getString("symbol"), rs.getString("side"), rs.getString("order_type"), rs.getString("status"),
                rs.getBigDecimal("quantity"), rs.getBigDecimal("filled_quantity"), rs.getBigDecimal("limit_price"),
                rs.getBigDecimal("fee_rate")), orderId, accountId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("没有这个订单");
        }
        return rows.get(0);
    }

    private String findOrder(String accountId, String strategy, String signal, String side) {
        List<String> rows = jdbc.query("""
                SELECT order_id FROM ct_paper_order
                WHERE account_id = ? AND strategy_code = ? AND signal_id = ? AND side = ?
                """, (rs, index) -> rs.getString("order_id"), accountId, strategy, signal, side);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private BigDecimal available(String accountId) {
        return jdbc.queryForObject("""
                SELECT available_balance FROM ct_portfolio_balance WHERE account_id = ? AND asset_code = 'USDT'
                """, BigDecimal.class, accountId);
    }

    private BigDecimal held(String accountId, String symbol) {
        List<BigDecimal> rows = jdbc.query("""
                SELECT quantity FROM ct_book_position WHERE account_id = ? AND symbol = ?
                """, (rs, index) -> rs.getBigDecimal("quantity"), accountId, symbol);
        return rows.isEmpty() ? BigDecimal.ZERO : rows.get(0);
    }

    private void lock(String accountId) {
        List<String> rows = jdbc.query("""
                SELECT status FROM ct_book_account WHERE account_id = ? FOR UPDATE
                """, (rs, index) -> rs.getString("status"), accountId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("没有这个账户");
        }
        if (!"ACTIVE".equals(rows.get(0))) {
            throw new IllegalStateException("账户已停用");
        }
    }

    private static String required(Map<String, String> body, String key) {
        String value = body.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + "不能为空");
        }
        return value;
    }

    private static BigDecimal money(String value, boolean allowZero) {
        BigDecimal parsed = new BigDecimal(value).setScale(SCALE, ROUND);
        if (parsed.signum() < 0 || (parsed.signum() == 0 && !allowZero)) {
            throw new IllegalArgumentException("数量和价格必须大于 0");
        }
        return parsed;
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

    private record OrderRow(String orderId, String strategy, String signal, String symbol, String side, String type,
                            String status, BigDecimal quantity, BigDecimal filled, BigDecimal limitPrice, BigDecimal feeRate) {
    }
}
