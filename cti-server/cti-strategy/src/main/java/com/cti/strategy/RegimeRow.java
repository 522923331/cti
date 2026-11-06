package com.cti.strategy;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RegimeRow(
        LocalDate tradeDate,
        String regime,
        String rawRegime,
        int confirmCount,
        BigDecimal marketScore,
        BigDecimal btcTrendScore,
        BigDecimal marketBreadth,
        BigDecimal btcVolatility,
        BigDecimal totalExposureLimit,
        String riskOverride,
        String reasonJson,
        String strategyVersion
) {
}
