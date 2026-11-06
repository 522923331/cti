package com.cti.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeRow(
        LocalDate tradeDate,
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal openPrice,
        BigDecimal fillPrice,
        BigDecimal fee,
        BigDecimal slippage,
        BigDecimal pnl,
        LocalDate openedOn,
        Integer holdingDays
) {
}
