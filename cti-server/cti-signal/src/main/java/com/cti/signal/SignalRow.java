package com.cti.signal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SignalRow(
        String signalId,
        String symbol,
        LocalDateTime signalTime,
        LocalDate tradeDate,
        String signalType,
        String signalStatus,
        BigDecimal score,
        BigDecimal entryPrice,
        BigDecimal stopPrice,
        BigDecimal suggestedPosition,
        BigDecimal riskAmount,
        String signalReason,
        String invalidationReason,
        String featureVersion,
        String strategyVersion
) {
}
