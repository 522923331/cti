package com.cti.backtest.query;

public record RunView(
        String runId,
        String strategyName,
        String strategyVersion,
        String startDate,
        String endDate,
        String initialCapital,
        String commissionRate,
        String slippageRate,
        String status,
        String reportJson,
        String totalReturn,
        String annualizedReturn,
        String annualizedVolatility,
        String sharpeRatio,
        String sortinoRatio,
        String maxDrawdown,
        String winRate,
        String profitFactor,
        Integer totalTrades,
        String averageHoldingDays,
        String totalFee,
        String totalSlippage,
        String benchmarkReturn,
        String excessReturn
) {
}
