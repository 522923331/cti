package com.cti.strategy.query;

import com.fasterxml.jackson.databind.JsonNode;

public record ScoreView(
        String symbol,
        String tradeDate,
        String marketScore,
        String trendScore,
        String flowScore,
        String derivativesScore,
        String sentimentScore,
        String totalScore,
        String signalLevel,
        Integer rank,
        JsonNode reason,
        JsonNode risk,
        String featureVersion,
        String strategyVersion
) {
}
