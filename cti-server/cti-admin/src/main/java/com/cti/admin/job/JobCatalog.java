package com.cti.admin.job;

import java.util.List;

public final class JobCatalog {

    public static final String HEALTH_PROBE = "cti_health_probe_job";

    private JobCatalog() {
    }

    public static List<JobCatalogEntry> registered() {
        return List.of(new JobCatalogEntry(
                HEALTH_PROBE,
                "探测 MySQL 与 Redis：写一条任务记录，并刷新 Redis 键 cti:health:probe",
                "0"));
    }
}
