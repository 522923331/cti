package com.cti.strategy;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ScoreRow(
        String symbol,
        LocalDate tradeDate,
        BigDecimal marketScore,
        BigDecimal trendScore,
        BigDecimal flowScore,
        BigDecimal derivativesScore,
        BigDecimal sentimentScore,
        BigDecimal totalScore,
        String signalLevel,
        String reasonJson,
        String riskJson,
        String featureVersion,
        String strategyVersion
) {
}
