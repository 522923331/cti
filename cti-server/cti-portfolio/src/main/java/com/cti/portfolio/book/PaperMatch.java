package com.cti.portfolio.book;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PaperMatch {

    private static final int SCALE = 12;
    private static final RoundingMode ROUND = RoundingMode.HALF_UP;

    private PaperMatch() {
    }

    public static BigDecimal marketPrice(String side, BigDecimal open, BigDecimal slippageBps) {
        BigDecimal slip = slippageBps.movePointLeft(4);
        BigDecimal factor = "SELL".equals(side) ? BigDecimal.ONE.subtract(slip) : BigDecimal.ONE.add(slip);
        return open.multiply(factor).setScale(SCALE, ROUND);
    }

    public static BigDecimal fee(BigDecimal notional, BigDecimal rate) {
        return notional.multiply(rate).setScale(SCALE, ROUND);
    }

    public static BigDecimal fillable(BigDecimal remaining, BigDecimal requested) {
        if (requested == null) {
            return remaining.setScale(SCALE, ROUND);
        }
        BigDecimal quantity = requested.setScale(SCALE, ROUND);
        if (quantity.signum() <= 0 || quantity.compareTo(remaining) > 0) {
            throw new IllegalArgumentException("成交数量不合法");
        }
        return quantity;
    }

    public static boolean limitTouched(String side, BigDecimal limit, BigDecimal high, BigDecimal low) {
        if ("SELL".equals(side)) {
            return high.compareTo(limit) >= 0;
        }
        return low.compareTo(limit) <= 0;
    }

    public static String protectiveExit(BigDecimal stop, BigDecimal takeProfit, BigDecimal high, BigDecimal low) {
        boolean stopHit = stop != null && low.compareTo(stop) <= 0;
        boolean targetHit = takeProfit != null && high.compareTo(takeProfit) >= 0;
        if (stopHit) {
            return "STOP";
        }
        if (targetHit) {
            return "TAKE_PROFIT";
        }
        return "NONE";
    }
}
