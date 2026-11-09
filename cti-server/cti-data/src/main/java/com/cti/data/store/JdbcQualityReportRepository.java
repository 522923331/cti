package com.cti.data.store;

import com.cti.common.Versions;
import com.cti.data.kline.QualityReport;
import com.cti.data.kline.QualityReportRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;

@Repository
public class JdbcQualityReportRepository implements QualityReportRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcQualityReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(QualityReport report) {
        jdbcTemplate.update("""
                INSERT INTO ct_data_quality_report (
                    exchange_code, symbol, interval_code, check_date, status,
                    missing_count, duplicate_count, unclosed_count, anomaly_count, detail_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    status = VALUES(status),
                    missing_count = VALUES(missing_count),
                    duplicate_count = VALUES(duplicate_count),
                    unclosed_count = VALUES(unclosed_count),
                    anomaly_count = VALUES(anomaly_count),
                    detail_json = VALUES(detail_json)
                """, Versions.EXCHANGE, report.symbol(), report.intervalCode(), Date.valueOf(report.checkDate()),
                report.status(), report.missingCount(), report.duplicateCount(), report.unclosedCount(),
                report.anomalyCount(), report.detailJson());
    }

    @Override
    public List<QualityReport> latest() {
        return jdbcTemplate.query("""
                SELECT symbol, interval_code, check_date, status, missing_count, duplicate_count,
                       unclosed_count, anomaly_count, detail_json
                FROM ct_data_quality_report
                WHERE exchange_code = ?
                ORDER BY check_date DESC, symbol, interval_code
                LIMIT 50
                """, (rs, row) -> new QualityReport(
                rs.getString("symbol"),
                rs.getString("interval_code"),
                rs.getDate("check_date").toLocalDate(),
                rs.getString("status"),
                rs.getInt("missing_count"),
                rs.getInt("duplicate_count"),
                rs.getInt("unclosed_count"),
                rs.getInt("anomaly_count"),
                rs.getString("detail_json")), Versions.EXCHANGE);
    }
}
