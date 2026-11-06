package com.cti.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountRow(
        String accountCode,
        String strategyName,
        String strategyVersion,
        String quoteAsset,
        BigDecimal initialCapital,
        BigDecimal cash,
        BigDecimal equity,
        BigDecimal peakEquity,
        LocalDate asOfDate,
        String riskJson
) {
}
