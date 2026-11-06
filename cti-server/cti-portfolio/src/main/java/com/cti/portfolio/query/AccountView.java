package com.cti.portfolio.query;

import com.fasterxml.jackson.databind.JsonNode;

public record AccountView(
        String accountCode,
        String strategyName,
        String strategyVersion,
        String quoteAsset,
        String initialCapital,
        String cash,
        String equity,
        String peakEquity,
        String asOfDate,
        JsonNode risk
) {
}
