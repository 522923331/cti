package com.cti.portfolio.query;

public record PositionView(
        String symbol,
        String quantity,
        String averageEntryPrice,
        String stopPrice,
        String highestClose,
        String openedOn,
        String updatedOn,
        String marketValue
) {
}
