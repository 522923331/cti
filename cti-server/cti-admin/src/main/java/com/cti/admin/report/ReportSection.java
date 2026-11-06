package com.cti.admin.report;

import java.util.List;

public record ReportSection(String title, String conclusion, List<Evidence> evidence) {
}
