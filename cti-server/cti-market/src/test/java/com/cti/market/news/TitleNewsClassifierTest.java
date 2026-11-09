package com.cti.market.news;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TitleNewsClassifierTest {

    private final TitleNewsClassifier classifier = new TitleNewsClassifier();

    @Test
    void classifiesAListingFromTheTitleOnly() {
        NewsDraft draft = classifier.classify("Binance Will List Bitcoin (BTC)", List.of("BTC", "ETH")).orElseThrow();
        assertEquals("EXCHANGE", draft.category());
        assertEquals("POSITIVE", draft.sentiment());
        assertEquals("MEDIUM", draft.impactLevel());
        assertEquals(0, new BigDecimal("0.400000").compareTo(draft.confidence()));
        assertEquals(List.of("BTC"), draft.assets());
        assertFalse(draft.sentiment().equals("BUY"));
        assertEquals("Binance Will List Bitcoin (BTC)", draft.summary());
    }

    @Test
    void leavesAnUnknownTitleUncertainAndMarketWide() {
        NewsDraft draft = classifier.classify("Weekly Operations Note", List.of("BTC")).orElseThrow();
        assertEquals("OTHER", draft.category());
        assertEquals("UNCERTAIN", draft.sentiment());
        assertEquals("LOW", draft.impactLevel());
        assertEquals(0, new BigDecimal("0.200000").compareTo(draft.confidence()));
        assertEquals(List.of("MARKET_WIDE"), draft.assets());
    }

    @Test
    void doesNotTreatTheWordBuyAsATradeSignal() {
        NewsDraft draft = classifier.classify("Fee Discount When You Buy Bitcoin", List.of("BTC")).orElseThrow();
        assertEquals("UNCERTAIN", draft.sentiment());
        assertEquals("OTHER", draft.category());
        assertFalse(draft.assets().contains("BUY"));
    }

    @Test
    void doesNotTreatAHyphenatedTokenAsAKnownAsset() {
        NewsDraft draft = classifier.classify(
                "Binance COIN-M Futures WebSocket System Upgrade Notice", List.of("BNB")).orElseThrow();
        assertEquals(List.of("MARKET_WIDE"), draft.assets());
        NewsDraft reward = classifier.classify("Trade to Share Up to 400 BNB Token Vouchers", List.of("BNB")).orElseThrow();
        assertEquals(List.of("BNB"), reward.assets());
    }

    @Test
    void normalizesATitleBeforeHashing() {
        assertEquals(NewsIds.contentHash("Hello  World!"), NewsIds.contentHash("hello world"));
    }
}
