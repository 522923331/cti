package com.cti.signal.query;

import com.fasterxml.jackson.databind.JsonNode;

public record SignalView(
        String signalId,
        String symbol,
        String signalTime,
        String tradeDate,
        String signalType,
        String signalStatus,
        String score,
        String entryPrice,
        String stopPrice,
        String suggestedPosition,
        String riskAmount,
        JsonNode signalReason,
        JsonNode invalidationReason,
        String featureVersion,
        String strategyVersion
) {
}
