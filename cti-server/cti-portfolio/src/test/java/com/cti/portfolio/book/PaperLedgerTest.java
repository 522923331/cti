package com.cti.portfolio.book;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperLedgerTest {

    @Test
    void feeEntersCostAndAFullSaleReturnsQuantityToZero() {
        PaperLedger ledger = new PaperLedger();
        ledger.open("paper-a", "模拟甲", "PAPER", "100");
        ledger.fill("paper-a", "buy-1", "BTCUSDT", "BUY", "2", "10", "1");
        assertEquals(new BigDecimal("21.000000000000"), ledger.position("paper-a", "BTCUSDT").costAmount());
        assertEquals(new BigDecimal("10.500000000000"), ledger.position("paper-a", "BTCUSDT").averageCost());
        assertEquals(new BigDecimal("79.000000000000"), ledger.cash("paper-a").available());
        ledger.mark("paper-a", "BTCUSDT", "11");
        assertEquals(new BigDecimal("22.000000000000"), ledger.position("paper-a", "BTCUSDT").marketValue());
        assertEquals(new BigDecimal("1.000000000000"), ledger.position("paper-a", "BTCUSDT").unrealizedPnl());
        ledger.fill("paper-a", "sell-1", "BTCUSDT", "SELL", "2", "12", "1");
        assertEquals(new BigDecimal("0.000000000000"), ledger.position("paper-a", "BTCUSDT").quantity());
        assertEquals(new BigDecimal("0.000000000000"), ledger.position("paper-a", "BTCUSDT").costAmount());
        assertNull(ledger.position("paper-a", "BTCUSDT").averageCost());
        assertEquals(new BigDecimal("102.000000000000"), ledger.cash("paper-a").available());
        assertEquals(new BigDecimal("2.000000000000"), ledger.position("paper-a", "BTCUSDT").realizedPnl());
        assertTrue(ledger.reconciled("paper-a"));
    }

    @Test
    void repeatingAFillDoesNotPostASecondLedgerLine() {
        PaperLedger ledger = new PaperLedger();
        ledger.open("paper-a", "模拟甲", "PAPER", "100");
        ledger.fill("paper-a", "buy-1", "BTCUSDT", "BUY", "1", "10", "0.1");
        ledger.fill("paper-a", "buy-1", "BTCUSDT", "BUY", "1", "10", "0.1");
        assertEquals(2, ledger.entries("paper-a").size());
        assertEquals(new BigDecimal("1.000000000000"), ledger.position("paper-a", "BTCUSDT").quantity());
        assertEquals(new BigDecimal("89.900000000000"), ledger.cash("paper-a").available());
    }

    @Test
    void accountsStayIsolatedAndCashCannotGoNegative() {
        PaperLedger ledger = new PaperLedger();
        ledger.open("paper-a", "模拟甲", "PAPER", "50");
        ledger.open("custom-b", "手工乙", "CUSTOM", "20");
        ledger.deposit("paper-a", "dep-1", "5");
        assertThrows(IllegalStateException.class, () -> ledger.withdraw("custom-b", "out-1", "21"));
        assertEquals(new BigDecimal("55.000000000000"), ledger.cash("paper-a").available());
        assertEquals(new BigDecimal("20.000000000000"), ledger.cash("custom-b").available());
        assertThrows(IllegalStateException.class, () -> ledger.fill("paper-a", "buy-too-big", "ETHUSDT", "BUY", "1", "60", "0"));
        assertTrue(ledger.reconciled("paper-a"));
        assertTrue(ledger.reconciled("custom-b"));
    }

    @Test
    void fractionalAmountsKeepTwelveDecimalPlaces() {
        PaperLedger ledger = new PaperLedger();
        ledger.open("paper-a", "模拟甲", "PAPER", "1.000000000003");
        ledger.fill("paper-a", "buy-1", "BTCUSDT", "BUY", "0.000000000003", "2", "0.000000000001");
        assertEquals(new BigDecimal("0.000000000007"), ledger.position("paper-a", "BTCUSDT").costAmount());
        assertEquals(new BigDecimal("0.999999999996"), ledger.cash("paper-a").available());
        assertEquals(ledger.cash("paper-a").available().add(ledger.cash("paper-a").frozen()), ledger.cash("paper-a").total());
    }
}
