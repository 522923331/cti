package com.cti.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PositionRow(
        String symbol,
        BigDecimal quantity,
        BigDecimal averageEntryPrice,
        BigDecimal stopPrice,
        BigDecimal highestClose,
        LocalDate openedOn,
        LocalDate updatedOn,
        BigDecimal marketValue
) {
}
