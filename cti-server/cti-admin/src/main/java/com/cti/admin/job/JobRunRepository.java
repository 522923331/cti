package com.cti.admin.job;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public class JobRunRepository {

    private final JdbcTemplate jdbcTemplate;

    public JobRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insert(String jobName, LocalDate businessDate, String status, String message,
                      Instant startedAt, Instant finishedAt) {
        int attempt = nextAttempt(jobName, businessDate);
        jdbcTemplate.update("""
                INSERT INTO ct_job_run
                    (job_name, business_date, status, attempt, message, started_at, finished_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                jobName,
                Date.valueOf(businessDate),
                status,
                attempt,
                message,
                Timestamp.from(startedAt),
                Timestamp.from(finishedAt));
        return attempt;
    }

    public List<JobRunView> recent(int limit) {
        return jdbcTemplate.query("""
                SELECT id, job_name, business_date, status, attempt, message, started_at, finished_at
                FROM ct_job_run
                ORDER BY id DESC
                LIMIT ?
                """, (rs, rowNum) -> new JobRunView(
                rs.getLong("id"),
                rs.getString("job_name"),
                rs.getDate("business_date").toLocalDate(),
                rs.getString("status"),
                rs.getInt("attempt"),
                rs.getString("message"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant()
        ), limit);
    }

    private int nextAttempt(String jobName, LocalDate businessDate) {
        Integer attempt = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(attempt), 0) + 1 FROM ct_job_run WHERE job_name = ? AND business_date = ?",
                Integer.class,
                jobName,
                Date.valueOf(businessDate));
        return attempt == null ? 1 : attempt;
    }
}
