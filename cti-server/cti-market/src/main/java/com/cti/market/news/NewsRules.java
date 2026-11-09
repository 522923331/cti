package com.cti.market.news;

import java.math.BigDecimal;
import java.util.Set;

public final class NewsRules {

    public static final String PROVIDER = "BINANCE_ANN";

    private static final Set<String> CATEGORIES = Set.of(
            "MACRO", "REGULATION", "ETF", "EXCHANGE", "PROJECT",
            "STABLECOIN", "SECURITY", "TOKEN_UNLOCK", "OTHER");
    private static final Set<String> SENTIMENTS = Set.of("POSITIVE", "NEGATIVE", "NEUTRAL", "UNCERTAIN");
    private static final Set<String> IMPACTS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> TRADE_ACTIONS = Set.of(
            "BUY", "SELL", "STRONG_BUY", "EXIT", "NO_TRADE", "REDUCE", "HOLD", "WATCH");

    private NewsRules() {
    }

    public static boolean accept(NewsDraft draft) {
        if (draft == null || draft.confidence() == null || draft.summary() == null || draft.summary().isBlank()) {
            return false;
        }
        if (!CATEGORIES.contains(draft.category()) || !SENTIMENTS.contains(draft.sentiment())
                || !IMPACTS.contains(draft.impactLevel())) {
            return false;
        }
        if (TRADE_ACTIONS.contains(draft.category()) || TRADE_ACTIONS.contains(draft.sentiment())
                || TRADE_ACTIONS.contains(draft.impactLevel())) {
            return false;
        }
        if (draft.assets() == null || draft.assets().isEmpty()) {
            return false;
        }
        for (String asset : draft.assets()) {
            if (asset == null || asset.isBlank() || TRADE_ACTIONS.contains(asset)) {
                return false;
            }
        }
        return draft.confidence().compareTo(BigDecimal.ZERO) >= 0
                && draft.confidence().compareTo(BigDecimal.ONE) <= 0;
    }
}
