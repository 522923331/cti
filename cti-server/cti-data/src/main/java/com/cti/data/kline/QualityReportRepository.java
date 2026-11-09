package com.cti.data.kline;

import java.util.List;

public interface QualityReportRepository {

    void save(QualityReport report);

    List<QualityReport> latest();
}
