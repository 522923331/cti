package com.cti.common.job;

import java.time.LocalDate;

public record JobTriggerResult(
        String jobName,
        String status,
        LocalDate businessDate,
        int attempt,
        String message
) {
}
