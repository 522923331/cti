package com.cti.backtest.query;

public record EquityView(
        String tradeDate,
        String cash,
        String equity,
        String peakEquity,
        String drawdown,
        String exposure
) {
}
