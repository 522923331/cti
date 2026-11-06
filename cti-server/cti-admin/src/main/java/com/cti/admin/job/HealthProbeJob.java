package com.cti.admin.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
public class HealthProbeJob {

    public static final String REDIS_KEY = "cti:health:probe";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    private static final Logger log = LoggerFactory.getLogger(HealthProbeJob.class);

    private final StringRedisTemplate redis;
    private final JobRunRepository jobRunRepository;

    public HealthProbeJob(StringRedisTemplate redis, JobRunRepository jobRunRepository) {
        this.redis = redis;
        this.jobRunRepository = jobRunRepository;
    }

    @XxlJob(JobCatalog.HEALTH_PROBE)
    public void job() {
        ProbeResult result = execute();
        XxlJobHelper.log(result.message());
    }

    public ProbeResult execute() {
        Instant started = Instant.now();
        LocalDate businessDate = LocalDate.now(ZoneOffset.UTC);
        try {
            String stamp = started.toString();
            redis.opsForValue().set(REDIS_KEY, stamp);
            String message = "probe ok at " + stamp;
            int attempt = jobRunRepository.insert(
                    JobCatalog.HEALTH_PROBE, businessDate, SUCCESS, message, started, Instant.now());
            log.info("health probe succeeded, businessDate={}, attempt={}", businessDate, attempt);
            return new ProbeResult(JobCatalog.HEALTH_PROBE, SUCCESS, businessDate, attempt, message);
        } catch (RuntimeException ex) {
            log.error("health probe failed", ex);
            recordFailure(businessDate, started, ex);
            throw ex;
        }
    }

    private void recordFailure(LocalDate businessDate, Instant started, RuntimeException ex) {
        try {
            jobRunRepository.insert(
                    JobCatalog.HEALTH_PROBE,
                    businessDate,
                    FAILED,
                    ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    started,
                    Instant.now());
        } catch (RuntimeException recordEx) {
            log.error("failed to record health probe failure", recordEx);
        }
    }

    public record ProbeResult(String jobName, String status, LocalDate businessDate, int attempt, String message) {
    }
}
