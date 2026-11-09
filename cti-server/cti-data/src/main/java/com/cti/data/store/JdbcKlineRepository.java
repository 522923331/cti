package com.cti.data.store;

import com.cti.common.Versions;
import com.cti.data.kline.SaveAction;
import com.cti.data.kline.StoredKline;
import com.cti.market.model.KlineBar;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcKlineRepository implements KlineRepository {

    private static final String SELECT_COLUMNS = """
            open_time, close_time, open_price, high_price, low_price, close_price, volume, quote_volume,
            trade_count, taker_buy_base_volume, taker_buy_quote_volume, is_closed
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcKlineRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SaveBatch saveAll(List<KlineBar> bars) {
        if (bars.isEmpty()) {
            return SaveBatch.empty();
        }
        String symbol = bars.get(0).symbol();
        String interval = bars.get(0).intervalCode();
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (KlineBar bar : bars) {
            if (!symbol.equals(bar.symbol()) || !interval.equals(bar.intervalCode())) {
                throw new IllegalArgumentException("一批 K 线必须是同一个交易对和周期");
            }
            min = Math.min(min, bar.openTime());
            max = Math.max(max, bar.openTime());
        }
        Map<Long, StoredKline> existing = loadStored(symbol, interval, min, max);
        List<KlineBar> inserts = new ArrayList<>();
        List<KlineBar> updates = new ArrayList<>();
        List<KlineBar> revisions = new ArrayList<>();
        List<KlineBar> changed = new ArrayList<>();
        int unchanged = 0;
        for (KlineBar bar : bars) {
            StoredKline stored = existing.get(bar.openTime());
            switch (SaveAction.decide(stored, bar)) {
                case INSERT -> {
                    inserts.add(bar);
                    changed.add(bar);
                }
                case UPDATE -> {
                    updates.add(bar);
                    changed.add(bar);
                }
                case REVISE -> {
                    revisions.add(bar);
                    updates.add(bar);
                    changed.add(bar);
                }
                case UNCHANGED -> unchanged++;
            }
        }
        insertAll(inserts);
        updateAll(updates);
        reviseAll(revisions, existing);
        return new SaveBatch(inserts.size(), updates.size() - revisions.size(), revisions.size(), unchanged, changed);
    }

    @Override
    public List<Long> openTimes(String symbol, String interval, long fromOpen, long toOpen) {
        return jdbcTemplate.query("""
                SELECT open_time FROM ct_kline
                WHERE exchange_code = ? AND symbol = ? AND interval_code = ? AND open_time BETWEEN ? AND ?
                ORDER BY open_time
                """, (rs, row) -> rs.getLong("open_time"), Versions.EXCHANGE, symbol, interval, fromOpen, toOpen);
    }

    @Override
    public List<KlineBar> load(String symbol, String interval, long fromOpen, long toOpen) {
        return jdbcTemplate.query("""
                SELECT %s FROM ct_kline
                WHERE exchange_code = ? AND symbol = ? AND interval_code = ? AND open_time BETWEEN ? AND ?
                ORDER BY open_time
                """.formatted(SELECT_COLUMNS), (rs, row) -> mapBar(symbol, interval, rs),
                Versions.EXCHANGE, symbol, interval, fromOpen, toOpen);
    }

    @Override
    public List<KlineBar> pending(String symbol, String interval) {
        return jdbcTemplate.query("""
                SELECT %s FROM ct_kline
                WHERE exchange_code = ? AND symbol = ? AND interval_code = ? AND archived = 0
                ORDER BY open_time
                """.formatted(SELECT_COLUMNS), (rs, row) -> mapBar(symbol, interval, rs),
                Versions.EXCHANGE, symbol, interval);
    }

    @Override
    public void markArchived(String symbol, String interval, List<Long> openTimes) {
        jdbcTemplate.batchUpdate("""
                UPDATE ct_kline SET archived = 1
                WHERE exchange_code = ? AND symbol = ? AND interval_code = ? AND open_time = ?
                """, openTimes, openTimes.size(), (ps, openTime) -> {
            ps.setString(1, Versions.EXCHANGE);
            ps.setString(2, symbol);
            ps.setString(3, interval);
            ps.setLong(4, openTime);
        });
    }

    @Override
    public List<KlineStat> stats() {
        return jdbcTemplate.query("""
                SELECT symbol, interval_code, COUNT(*) AS cnt,
                       SUM(is_closed = 0) AS unclosed, MAX(open_time) AS latest_open
                FROM ct_kline
                WHERE exchange_code = ?
                GROUP BY symbol, interval_code
                """, (rs, row) -> new KlineStat(
                rs.getString("symbol"),
                rs.getString("interval_code"),
                rs.getLong("cnt"),
                rs.getLong("unclosed"),
                rs.getLong("latest_open")), Versions.EXCHANGE);
    }

    @Override
    public List<KlineBar> latest(String symbol, String interval, int limit) {
        List<KlineBar> descending = jdbcTemplate.query("""
                SELECT %s FROM ct_kline
                WHERE exchange_code = ? AND symbol = ? AND interval_code = ?
                ORDER BY open_time DESC
                LIMIT ?
                """.formatted(SELECT_COLUMNS), (rs, row) -> mapBar(symbol, interval, rs),
                Versions.EXCHANGE, symbol, interval, limit);
        java.util.Collections.reverse(descending);
        return descending;
    }

    private Map<Long, StoredKline> loadStored(String symbol, String interval, long fromOpen, long toOpen) {
        Map<Long, StoredKline> stored = new HashMap<>();
        jdbcTemplate.query("""
                SELECT %s FROM ct_kline
                WHERE exchange_code = ? AND symbol = ? AND interval_code = ? AND open_time BETWEEN ? AND ?
                """.formatted(SELECT_COLUMNS), rs -> {
            stored.put(rs.getLong("open_time"), new StoredKline(
                    rs.getLong("open_time"),
                    rs.getBigDecimal("open_price"),
                    rs.getBigDecimal("high_price"),
                    rs.getBigDecimal("low_price"),
                    rs.getBigDecimal("close_price"),
                    rs.getBigDecimal("volume"),
                    rs.getBigDecimal("quote_volume"),
                    nullableLong(rs, "trade_count"),
                    rs.getBigDecimal("taker_buy_base_volume"),
                    rs.getBigDecimal("taker_buy_quote_volume"),
                    rs.getInt("is_closed") == 1));
        }, Versions.EXCHANGE, symbol, interval, fromOpen, toOpen);
        return stored;
    }

    private void insertAll(List<KlineBar> bars) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO ct_kline (
                    exchange_code, symbol, interval_code, open_time, close_time,
                    open_price, high_price, low_price, close_price, volume, quote_volume,
                    trade_count, taker_buy_base_volume, taker_buy_quote_volume, is_closed, archived
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, bars, bars.size(), (ps, bar) -> bindBar(ps, bar, true));
    }

    private void updateAll(List<KlineBar> bars) {
        jdbcTemplate.batchUpdate("""
                UPDATE ct_kline SET
                    close_time = ?, open_price = ?, high_price = ?, low_price = ?, close_price = ?,
                    volume = ?, quote_volume = ?, trade_count = ?, taker_buy_base_volume = ?,
                    taker_buy_quote_volume = ?, is_closed = ?, archived = 0
                WHERE exchange_code = ? AND symbol = ? AND interval_code = ? AND open_time = ?
                """, bars, bars.size(), (ps, bar) -> {
            int i = 1;
            ps.setLong(i++, bar.closeTime());
            ps.setBigDecimal(i++, bar.open());
            ps.setBigDecimal(i++, bar.high());
            ps.setBigDecimal(i++, bar.low());
            ps.setBigDecimal(i++, bar.close());
            ps.setBigDecimal(i++, bar.volume());
            setDecimal(ps, i++, bar.quoteVolume());
            if (bar.tradeCount() == null) {
                ps.setNull(i++, java.sql.Types.BIGINT);
            } else {
                ps.setLong(i++, bar.tradeCount());
            }
            setDecimal(ps, i++, bar.takerBuyBase());
            setDecimal(ps, i++, bar.takerBuyQuote());
            ps.setInt(i++, bar.closed() ? 1 : 0);
            ps.setString(i++, bar.exchangeCode());
            ps.setString(i++, bar.symbol());
            ps.setString(i++, bar.intervalCode());
            ps.setLong(i, bar.openTime());
        });
    }

    private void reviseAll(List<KlineBar> bars, Map<Long, StoredKline> existing) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO ct_kline_revision (
                    exchange_code, symbol, interval_code, open_time,
                    old_close_price, new_close_price, old_quote_volume, new_quote_volume, revised_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, bars, bars.size(), (ps, bar) -> {
            StoredKline previous = existing.get(bar.openTime());
            ps.setString(1, bar.exchangeCode());
            ps.setString(2, bar.symbol());
            ps.setString(3, bar.intervalCode());
            ps.setLong(4, bar.openTime());
            ps.setBigDecimal(5, previous.close());
            ps.setBigDecimal(6, bar.close());
            setDecimal(ps, 7, previous.quoteVolume());
            setDecimal(ps, 8, bar.quoteVolume());
            ps.setTimestamp(9, Timestamp.from(Instant.now()));
        });
    }

    private static void bindBar(java.sql.PreparedStatement ps, KlineBar bar, boolean insert) throws java.sql.SQLException {
        int i = 1;
        ps.setString(i++, bar.exchangeCode());
        ps.setString(i++, bar.symbol());
        ps.setString(i++, bar.intervalCode());
        ps.setLong(i++, bar.openTime());
        ps.setLong(i++, bar.closeTime());
        ps.setBigDecimal(i++, bar.open());
        ps.setBigDecimal(i++, bar.high());
        ps.setBigDecimal(i++, bar.low());
        ps.setBigDecimal(i++, bar.close());
        ps.setBigDecimal(i++, bar.volume());
        setDecimal(ps, i++, bar.quoteVolume());
        if (bar.tradeCount() == null) {
            ps.setNull(i++, java.sql.Types.BIGINT);
        } else {
            ps.setLong(i++, bar.tradeCount());
        }
        setDecimal(ps, i++, bar.takerBuyBase());
        setDecimal(ps, i++, bar.takerBuyQuote());
        ps.setInt(i, bar.closed() ? 1 : 0);
    }

    private static void setDecimal(java.sql.PreparedStatement ps, int index, BigDecimal value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.DECIMAL);
        } else {
            ps.setBigDecimal(index, value);
        }
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static KlineBar mapBar(String symbol, String interval, java.sql.ResultSet rs) throws java.sql.SQLException {
        return new KlineBar(
                Versions.EXCHANGE,
                symbol,
                interval,
                rs.getLong("open_time"),
                rs.getLong("close_time"),
                rs.getBigDecimal("open_price"),
                rs.getBigDecimal("high_price"),
                rs.getBigDecimal("low_price"),
                rs.getBigDecimal("close_price"),
                rs.getBigDecimal("volume"),
                rs.getBigDecimal("quote_volume"),
                nullableLong(rs, "trade_count"),
                rs.getBigDecimal("taker_buy_base_volume"),
                rs.getBigDecimal("taker_buy_quote_volume"),
                rs.getInt("is_closed") == 1);
    }
}
