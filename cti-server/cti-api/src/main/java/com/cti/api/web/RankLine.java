package com.cti.api.web;

public record RankLine(
        String symbol,
        String assetName,
        String price,
        String change1d,
        String return7d,
        String return14d,
        String return30d,
        String ema20,
        String ema50,
        String ema200,
        String trendScore,
        String flowScore,
        String derivativesScore,
        String totalScore,
        String signalLevel,
        String signalType,
        String quality
) {
}
