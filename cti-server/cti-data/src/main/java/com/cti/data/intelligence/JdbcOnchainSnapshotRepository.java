package com.cti.data.intelligence;

import com.cti.market.onchain.OnchainMetric;
import com.cti.market.onchain.OnchainSnapshotStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.Instant;
import java.util.List;

@Repository
public class JdbcOnchainSnapshotRepository implements OnchainSnapshotStore {

    private final JdbcTemplate jdbc;

    public JdbcOnchainSnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int saveAll(List<OnchainMetric> metrics, String providerCode) {
        int written = 0;
        long collectTime = Instant.now().toEpochMilli();
        for (OnchainMetric metric : metrics) {
            written += jdbc.update("""
                    INSERT IGNORE INTO ct_onchain_snapshot
                        (asset_code, metric_code, interval_code, event_time_ms, collect_time_ms,
                         metric_value, metric_unit, provider_code, data_status, is_delayed)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, statement -> {
                statement.setString(1, metric.asset());
                statement.setString(2, metric.metricCode());
                statement.setString(3, metric.intervalCode());
                statement.setLong(4, metric.eventTimeMs());
                statement.setLong(5, collectTime);
                if (metric.value() == null) {
                    statement.setNull(6, Types.DECIMAL);
                } else {
                    statement.setBigDecimal(6, metric.value());
                }
                statement.setString(7, metric.unit());
                statement.setString(8, providerCode);
                statement.setString(9, metric.dataStatus());
                statement.setInt(10, metric.delayed() ? 1 : 0);
            });
        }
        return written;
    }

    public List<OnchainRow> latest(int limit) {
        return jdbc.query("""
                SELECT asset_code, metric_code, interval_code, event_time_ms, metric_value,
                       metric_unit, provider_code, data_status, is_delayed
                FROM ct_onchain_snapshot
                ORDER BY event_time_ms DESC, asset_code, metric_code
                LIMIT ?
                """, (rs, row) -> new OnchainRow(
                rs.getString("asset_code"),
                rs.getString("metric_code"),
                rs.getString("interval_code"),
                rs.getLong("event_time_ms"),
                rs.getBigDecimal("metric_value"),
                rs.getString("metric_unit"),
                rs.getString("provider_code"),
                rs.getString("data_status"),
                rs.getInt("is_delayed") == 1), limit);
    }
}
