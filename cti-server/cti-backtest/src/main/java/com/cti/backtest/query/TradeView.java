package com.cti.backtest.query;

public record TradeView(
        String tradeDate,
        String symbol,
        String side,
        String quantity,
        String openPrice,
        String fillPrice,
        String fee,
        String slippage,
        String pnl,
        String openedOn,
        Integer holdingDays
) {
}
