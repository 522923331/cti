package com.cti.data.kline;

import com.cti.market.model.KlineBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KlineRulesTest {

    @Test
    void closedBarWithANewPriceIsARevision() {
        KlineBar original = bar(1, true, "10");
        StoredKline stored = new StoredKline(1, original.open(), original.high(), original.low(), original.close(),
                original.volume(), original.quoteVolume(), original.tradeCount(), original.takerBuyBase(),
                original.takerBuyQuote(), true);
        assertEquals(SaveAction.UNCHANGED, SaveAction.decide(stored, original));
        assertEquals(SaveAction.REVISE, SaveAction.decide(stored, bar(1, true, "11")));
        assertEquals(SaveAction.UPDATE, SaveAction.decide(
                new StoredKline(1, original.open(), original.high(), original.low(), original.close(),
                        original.volume(), original.quoteVolume(), original.tradeCount(), original.takerBuyBase(),
                        original.takerBuyQuote(), false),
                bar(1, true, "11")));
    }

    @Test
    void findsAHoleAndKeepsTheFormingBarOutOfTheMissingSet() {
        long step = 100;
        List<Long> missing = KlineGapFinder.missing(0, 300, Set.of(0L, 200L, 300L), step);
        assertEquals(List.of(100L), missing);
        assertEquals(1, KlineGapFinder.ranges(missing, step).size());
    }

    @Test
    void missingClosedBarIsInvalidAndAFormingBarIsNot() {
        long step = 86_400_000L;
        KlineBar closed = bar(0, true, "10").closedAt(step + 10);
        KlineBar forming = bar(step, false, "11");
        QualityReport healthy = QualityReport.check("BTCUSDT", "1d", List.of(closed, forming),
                0, 0, step, "TRADING", LocalDate.of(2026, 11, 6));
        assertEquals(QualityReport.NORMAL, healthy.status());
        assertEquals(1, healthy.unclosedCount());

        QualityReport gap = QualityReport.check("BTCUSDT", "1d", List.of(forming),
                0, 0, step, "TRADING", LocalDate.of(2026, 11, 6));
        assertEquals(QualityReport.INVALID, gap.status());
        assertEquals(1, gap.missingCount());
    }

    private static KlineBar bar(long open, boolean closed, String close) {
        return new KlineBar("BINANCE", "BTCUSDT", "1d", open, open + 86_400_000L - 1,
                new BigDecimal("9"), new BigDecimal("12"), new BigDecimal("8"), new BigDecimal(close),
                new BigDecimal("1"), new BigDecimal("10"), 3L, new BigDecimal("0.4"), new BigDecimal("4"), closed);
    }
}
