package com.cti.data.intelligence;

import com.cti.market.news.NewsEventStore;
import com.cti.market.news.StoredNews;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcNewsEventRepository implements NewsEventStore, NewsEventQuery {

    private static final String COLUMNS = """
            event_id, provider_code, title, source_url, source_name, url_hash, content_hash,
            category, sentiment, impact_level, confidence, summary, published_at_ms
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcNewsEventRepository(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
    }

    @Override
    public Optional<StoredNews> findByEventId(String eventId) {
        return one("SELECT " + COLUMNS + " FROM ct_news_event WHERE event_id = ?", eventId);
    }

    @Override
    public Optional<StoredNews> findByContentHash(String providerCode, String contentHash) {
        return one("SELECT " + COLUMNS + " FROM ct_news_event WHERE provider_code = ? AND content_hash = ?",
                providerCode, contentHash);
    }

    @Override
    public void insert(StoredNews event) {
        transactions.executeWithoutResult(status -> {
            write(event, false);
            replaceAssets(event);
        });
    }

    @Override
    public void update(StoredNews event) {
        transactions.executeWithoutResult(status -> {
            write(event, true);
            replaceAssets(event);
        });
    }

    @Override
    public List<NewsEventRow> latest(String assetCode, int offset, int limit) {
        List<StoredNews> events = jdbc.query("""
                SELECT event_id, provider_code, title, source_url, source_name, url_hash, content_hash,
                       category, sentiment, impact_level, confidence, summary, published_at_ms
                FROM ct_news_event
                WHERE (? IS NULL OR EXISTS (
                    SELECT 1 FROM ct_news_asset_rela rela
                    WHERE rela.event_id = ct_news_event.event_id AND rela.asset_code = ?))
                ORDER BY published_at_ms DESC, event_id
                LIMIT ? OFFSET ?
                """, (rs, row) -> map(rs), assetCode, assetCode, limit, offset);
        return events.stream().map(this::view).toList();
    }

    @Override
    public Optional<NewsEventRow> find(String eventId) {
        return findByEventId(eventId).map(this::view);
    }

    private Optional<StoredNews> one(String sql, Object... args) {
        List<StoredNews> rows = jdbc.query(sql, (rs, row) -> map(rs), args);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        StoredNews event = rows.get(0);
        return Optional.of(withAssets(event, assetsOf(event.eventId())));
    }

    private void write(StoredNews event, boolean update) {
        if (update) {
            jdbc.update("""
                    UPDATE ct_news_event
                    SET title = ?, source_name = ?, content_hash = ?, category = ?, sentiment = ?,
                        impact_level = ?, confidence = ?, summary = ?, published_at_ms = ?, event_at_ms = ?
                    WHERE event_id = ?
                    """, event.title(), event.sourceName(), event.contentHash(), event.category(), event.sentiment(),
                    event.impactLevel(), event.confidence(), event.summary(), event.publishedAtMs(),
                    event.publishedAtMs(), event.eventId());
            return;
        }
        jdbc.update("""
                INSERT INTO ct_news_event
                    (event_id, provider_code, title, source_url, source_name, url_hash, content_hash,
                     category, sentiment, impact_level, confidence, summary, published_at_ms, event_at_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, event.eventId(), event.providerCode(), event.title(), event.sourceUrl(), event.sourceName(),
                event.urlHash(), event.contentHash(), event.category(), event.sentiment(), event.impactLevel(),
                event.confidence(), event.summary(), event.publishedAtMs(), event.publishedAtMs());
    }

    private void replaceAssets(StoredNews event) {
        jdbc.update("DELETE FROM ct_news_asset_rela WHERE event_id = ?", event.eventId());
        for (String asset : event.assets()) {
            jdbc.update("""
                    INSERT INTO ct_news_asset_rela (event_id, asset_code, relevance_score)
                    VALUES (?, ?, ?)
                    """, event.eventId(), asset, "MARKET_WIDE".equals(asset) ? null : new java.math.BigDecimal("1.000000"));
        }
    }

    private NewsEventRow view(StoredNews event) {
        List<String> assets = event.assets() == null || event.assets().isEmpty()
                ? assetsOf(event.eventId()) : event.assets();
        return new NewsEventRow(event.eventId(), event.title(), event.sourceUrl(), event.sourceName(),
                event.category(), event.sentiment(), event.impactLevel(), event.confidence(), event.summary(),
                event.publishedAtMs(), assets);
    }

    private List<String> assetsOf(String eventId) {
        return jdbc.query("SELECT asset_code FROM ct_news_asset_rela WHERE event_id = ? ORDER BY id",
                (rs, row) -> rs.getString("asset_code"), eventId);
    }

    private static StoredNews map(ResultSet rs) throws SQLException {
        return new StoredNews(
                rs.getString("event_id"),
                rs.getString("provider_code"),
                rs.getString("title"),
                rs.getString("source_url"),
                rs.getString("source_name"),
                rs.getString("url_hash"),
                rs.getString("content_hash"),
                rs.getString("category"),
                rs.getString("sentiment"),
                rs.getString("impact_level"),
                rs.getBigDecimal("confidence"),
                rs.getString("summary"),
                rs.getLong("published_at_ms"),
                List.of());
    }

    private static StoredNews withAssets(StoredNews event, List<String> assets) {
        return new StoredNews(event.eventId(), event.providerCode(), event.title(), event.sourceUrl(),
                event.sourceName(), event.urlHash(), event.contentHash(), event.category(), event.sentiment(),
                event.impactLevel(), event.confidence(), event.summary(), event.publishedAtMs(), assets);
    }
}
