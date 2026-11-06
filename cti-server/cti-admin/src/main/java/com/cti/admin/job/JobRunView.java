package com.cti.admin.job;

import java.time.Instant;
import java.time.LocalDate;

public record JobRunView(
        long id,
        String jobName,
        LocalDate businessDate,
        String status,
        int attempt,
        String message,
        Instant startedAt,
        Instant finishedAt
) {
}
