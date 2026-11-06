package com.cti.market.model;

import com.cti.common.Versions;

import java.math.BigDecimal;

public record KlineBar(
        String exchangeCode,
        String symbol,
        String intervalCode,
        long openTime,
        long closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        BigDecimal quoteVolume,
        Long tradeCount,
        BigDecimal takerBuyBase,
        BigDecimal takerBuyQuote,
        boolean closed
) {

    public KlineBar {
        exchangeCode = exchangeCode == null ? Versions.EXCHANGE : exchangeCode;
    }

    public KlineBar closedAt(long serverTime) {
        boolean nowClosed = closeTime < serverTime;
        if (nowClosed == closed) {
            return this;
        }
        return new KlineBar(exchangeCode, symbol, intervalCode, openTime, closeTime,
                open, high, low, close, volume, quoteVolume, tradeCount, takerBuyBase, takerBuyQuote, nowClosed);
    }
}
