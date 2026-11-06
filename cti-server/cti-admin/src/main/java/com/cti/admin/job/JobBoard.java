package com.cti.admin.job;

import java.util.List;

public record JobBoard(List<JobCatalogEntry> jobs, List<JobRunView> recentRuns) {
}
