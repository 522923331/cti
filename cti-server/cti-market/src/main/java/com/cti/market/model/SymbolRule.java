package com.cti.market.model;

import java.math.BigDecimal;

public record SymbolRule(
        String symbol,
        String baseAsset,
        String quoteAsset,
        String status,
        BigDecimal minQty,
        BigDecimal stepSize,
        BigDecimal minNotional
) {
}
