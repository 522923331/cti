package com.cti.admin.report;

import java.time.LocalDate;

public record ReportSummary(
        LocalDate tradeDate,
        String strategyVersion,
        String featureVersion,
        String title,
        String summary
) {
}
