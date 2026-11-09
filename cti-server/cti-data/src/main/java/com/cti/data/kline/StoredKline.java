package com.cti.data.kline;

import com.cti.market.model.KlineBar;

import java.math.BigDecimal;

public record StoredKline(
        long openTime,
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

    public boolean sameMarketData(KlineBar bar) {
        return same(open, bar.open())
                && same(high, bar.high())
                && same(low, bar.low())
                && same(close, bar.close())
                && same(volume, bar.volume())
                && same(quoteVolume, bar.quoteVolume())
                && same(takerBuyBase, bar.takerBuyBase())
                && same(takerBuyQuote, bar.takerBuyQuote())
                && java.util.Objects.equals(tradeCount, bar.tradeCount());
    }

    private static boolean same(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.compareTo(right) == 0;
    }
}
