package com.cti.strategy.query;

import com.fasterxml.jackson.databind.JsonNode;

public record RegimeView(
        String tradeDate,
        String regime,
        String rawRegime,
        int confirmCount,
        String marketScore,
        String btcTrendScore,
        String marketBreadth,
        String btcVolatility,
        String totalExposureLimit,
        String riskOverride,
        JsonNode reason,
        String strategyVersion
) {
}
