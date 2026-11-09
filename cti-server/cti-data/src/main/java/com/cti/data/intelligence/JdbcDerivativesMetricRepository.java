package com.cti.data.intelligence;

import com.cti.market.model.FuturesPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcDerivativesMetricRepository implements DerivativesMetricRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDerivativesMetricRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int upsertAll(List<FuturesPoint> points, long collectTimeMs, String providerCode) {
        if (points.isEmpty()) {
            return 0;
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO ct_derivatives_metric
                    (symbol, metric_code, interval_code, event_time_ms, collect_time_ms,
                     metric_value, provider_code, data_status, raw_data)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    collect_time_ms = VALUES(collect_time_ms),
                    metric_value = VALUES(metric_value),
                    data_status = VALUES(data_status),
                    raw_data = VALUES(raw_data)
                """, points, 200, (statement, point) -> {
            statement.setString(1, point.symbol());
            statement.setString(2, point.metricCode());
            statement.setString(3, point.intervalCode());
            statement.setLong(4, point.eventTimeMs());
            statement.setLong(5, collectTimeMs);
            if (point.value() == null) {
                statement.setNull(6, java.sql.Types.DECIMAL);
            } else {
                statement.setBigDecimal(6, point.value());
            }
            statement.setString(7, providerCode);
            statement.setString(8, point.dataStatus());
            if (point.rawJson() == null) {
                statement.setNull(9, java.sql.Types.VARCHAR);
            } else {
                statement.setString(9, point.rawJson());
            }
        });
        return points.size();
    }

    @Override
    public int count(String symbol) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ct_derivatives_metric WHERE symbol = ?", Integer.class, symbol);
        return count == null ? 0 : count;
    }

    @Override
    public List<DerivativesMetricRow> latest(String symbol, int limit) {
        return jdbcTemplate.query("""
                SELECT symbol, metric_code, interval_code, event_time_ms, metric_value, data_status
                FROM ct_derivatives_metric
                WHERE symbol = ?
                ORDER BY event_time_ms DESC, metric_code
                LIMIT ?
                """, (rs, row) -> new DerivativesMetricRow(
                rs.getString("symbol"),
                rs.getString("metric_code"),
                rs.getString("interval_code"),
                rs.getLong("event_time_ms"),
                rs.getBigDecimal("metric_value"),
                rs.getString("data_status")), symbol, limit);
    }
}
