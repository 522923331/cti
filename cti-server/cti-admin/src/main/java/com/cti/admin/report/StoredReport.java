package com.cti.admin.report;

import java.time.LocalDate;
import java.util.List;

public record StoredReport(
        LocalDate tradeDate,
        String strategyVersion,
        String featureVersion,
        String title,
        String summary,
        List<ReportSection> sections,
        String generatedAt
) {
}
