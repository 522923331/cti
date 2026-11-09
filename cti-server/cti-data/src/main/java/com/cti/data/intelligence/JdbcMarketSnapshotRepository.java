package com.cti.data.intelligence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class JdbcMarketSnapshotRepository implements MarketSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMarketSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(String metricCode, long snapshotTimeMs, BigDecimal value, String unit,
                       String providerCode, String dataStatus) {
        jdbcTemplate.update("""
                INSERT INTO ct_market_snapshot
                    (snapshot_time_ms, metric_code, metric_value, metric_unit, provider_code, data_status)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    metric_value = VALUES(metric_value),
                    metric_unit = VALUES(metric_unit),
                    data_status = VALUES(data_status)
                """, snapshotTimeMs, metricCode, value, unit, providerCode, dataStatus);
    }

    @Override
    public List<MarketSnapshotRow> latest() {
        Long latest = jdbcTemplate.queryForObject("SELECT MAX(snapshot_time_ms) FROM ct_market_snapshot", Long.class);
        if (latest == null) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT metric_code, snapshot_time_ms, metric_value, metric_unit, provider_code, data_status
                FROM ct_market_snapshot
                WHERE snapshot_time_ms = ?
                ORDER BY metric_code
                """, (rs, row) -> new MarketSnapshotRow(
                rs.getString("metric_code"),
                rs.getLong("snapshot_time_ms"),
                rs.getBigDecimal("metric_value"),
                rs.getString("metric_unit"),
                rs.getString("provider_code"),
                rs.getString("data_status")), latest);
    }
}
