package com.cti.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SavedRun(
        String runId,
        String strategyName,
        String strategyVersion,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal initialCapital,
        BigDecimal commissionRate,
        BigDecimal slippageRate,
        String status,
        String configJson,
        String reportJson,
        BigDecimal totalReturn,
        BigDecimal annualizedReturn,
        BigDecimal annualizedVolatility,
        BigDecimal sharpeRatio,
        BigDecimal sortinoRatio,
        BigDecimal maxDrawdown,
        BigDecimal winRate,
        BigDecimal profitFactor,
        Integer totalTrades,
        BigDecimal averageHoldingDays,
        BigDecimal totalFee,
        BigDecimal totalSlippage,
        BigDecimal benchmarkReturn,
        BigDecimal excessReturn,
        List<TradeRow> trades,
        List<EquityRow> equity
) {
}
