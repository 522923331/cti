package com.cti.feature.query;

import com.cti.feature.FeatureRow;

import java.math.BigDecimal;

public record FeatureView(
        String symbol,
        String tradeDate,
        String ema20,
        String ema50,
        String ema200,
        String return7d,
        String return14d,
        String return30d,
        String return90d,
        String atr14,
        String atrPct,
        String adx14,
        String rsi14,
        String realizedVolatility20d,
        String volumeRatio20d,
        String spotDelta,
        String spotDeltaRatio,
        String oiChange1d,
        String oiChange7d,
        String fundingRate,
        String fundingZscore,
        String featureVersion,
        String dataQualityStatus
) {

    public static FeatureView from(FeatureRow row) {
        return new FeatureView(
                row.symbol(),
                row.tradeDate().toString(),
                plain(row.ema20()),
                plain(row.ema50()),
                plain(row.ema200()),
                plain(row.return7d()),
                plain(row.return14d()),
                plain(row.return30d()),
                plain(row.return90d()),
                plain(row.atr14()),
                plain(row.atrPct()),
                plain(row.adx14()),
                plain(row.rsi14()),
                plain(row.realizedVolatility20d()),
                plain(row.volumeRatio20d()),
                plain(row.spotDelta()),
                plain(row.spotDeltaRatio()),
                plain(row.oiChange1d()),
                plain(row.oiChange7d()),
                plain(row.fundingRate()),
                plain(row.fundingZscore()),
                row.featureVersion(),
                row.dataQualityStatus());
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
