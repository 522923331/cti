package com.cti.data.store;

import com.cti.common.Versions;
import com.cti.market.model.SymbolRule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Repository
public class JdbcExchangeSymbolRepository implements ExchangeSymbolRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcExchangeSymbolRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(SymbolRule rule, Instant syncedAt) {
        write(rule.symbol(), rule.baseAsset(), rule.quoteAsset(), rule.status(),
                rule.minQty(), rule.stepSize(), rule.minNotional(), syncedAt);
    }

    @Override
    public void markMissing(String symbol, String baseAsset, String quoteAsset, Instant syncedAt) {
        write(symbol, baseAsset, quoteAsset, "MISSING", null, null, null, syncedAt);
    }

    @Override
    public String statusOf(String symbol) {
        return statuses().get(symbol);
    }

    @Override
    public Map<String, String> statuses() {
        Map<String, String> statuses = new HashMap<>();
        jdbcTemplate.query("""
                SELECT symbol, status FROM ct_exchange_symbol WHERE exchange_code = ?
                """, rs -> {
            statuses.put(rs.getString("symbol"), rs.getString("status"));
        }, Versions.EXCHANGE);
        return statuses;
    }

    private void write(String symbol, String base, String quote, String status,
                       java.math.BigDecimal minQty, java.math.BigDecimal step, java.math.BigDecimal minNotional,
                       Instant syncedAt) {
        jdbcTemplate.update("""
                INSERT INTO ct_exchange_symbol
                    (exchange_code, symbol, base_asset, quote_asset, status, is_spot,
                     min_qty, step_size, min_notional, last_sync_time)
                VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    base_asset = VALUES(base_asset),
                    quote_asset = VALUES(quote_asset),
                    status = VALUES(status),
                    min_qty = VALUES(min_qty),
                    step_size = VALUES(step_size),
                    min_notional = VALUES(min_notional),
                    last_sync_time = VALUES(last_sync_time)
                """, Versions.EXCHANGE, symbol, base, quote, status, minQty, step, minNotional, Timestamp.from(syncedAt));
    }
}
