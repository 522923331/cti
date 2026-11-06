package com.cti.feature;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FeatureRow(
        String symbol,
        LocalDate tradeDate,
        BigDecimal ema20,
        BigDecimal ema50,
        BigDecimal ema200,
        BigDecimal return7d,
        BigDecimal return14d,
        BigDecimal return30d,
        BigDecimal return90d,
        BigDecimal atr14,
        BigDecimal atrPct,
        BigDecimal adx14,
        BigDecimal rsi14,
        BigDecimal realizedVolatility20d,
        BigDecimal volumeRatio20d,
        BigDecimal spotDelta,
        BigDecimal spotDeltaRatio,
        BigDecimal oiChange1d,
        BigDecimal oiChange7d,
        BigDecimal fundingRate,
        BigDecimal fundingZscore,
        String featureVersion,
        String dataQualityStatus
) {
}
