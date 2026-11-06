package com.cti.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EquityRow(
        LocalDate tradeDate,
        BigDecimal cash,
        BigDecimal equity,
        BigDecimal peakEquity,
        BigDecimal drawdown,
        BigDecimal exposure
) {
}
