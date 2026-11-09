package com.cti.portfolio.book;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperMatchTest {

    @Test
    void marketBuyAddsSlippageAndFeeToTheCashCost() {
        BigDecimal price = PaperMatch.marketPrice("BUY", new BigDecimal("10"), new BigDecimal("8"));
        BigDecimal fee = PaperMatch.fee(new BigDecimal("0.5").multiply(price), new BigDecimal("0.001"));
        assertEquals(new BigDecimal("10.008000000000"), price);
        assertEquals(new BigDecimal("0.005004000000"), fee);
        PaperLedger ledger = new PaperLedger();
        ledger.open("paper-c", "模拟丙", "PAPER", "10");
        ledger.fill("paper-c", "buy-1", "BTCUSDT", "BUY", "0.5", price.toPlainString(), fee.toPlainString());
        assertEquals(new BigDecimal("4.990996000000"), ledger.cash("paper-c").available());
        assertTrue(ledger.reconciled("paper-c"));
    }

    @Test
    void aBuyLargerThanCashIsRejectedAndTheBalanceStaysPut() {
        PaperLedger ledger = new PaperLedger();
        ledger.open("paper-c", "模拟丙", "PAPER", "10");
        BigDecimal price = PaperMatch.marketPrice("BUY", new BigDecimal("10"), new BigDecimal("8"));
        BigDecimal fee = PaperMatch.fee(new BigDecimal("2").multiply(price), new BigDecimal("0.001"));
        assertThrows(IllegalStateException.class, () -> ledger.fill(
                "paper-c", "buy-too-big", "BTCUSDT", "BUY", "2", price.toPlainString(), fee.toPlainString()));
        assertEquals(new BigDecimal("10.000000000000"), ledger.cash("paper-c").available());
    }

    @Test
    void aLimitBuyDoesNotFillWhenTheBarStaysAboveIt() {
        assertFalse(PaperMatch.limitTouched("BUY", new BigDecimal("10"), new BigDecimal("11"), new BigDecimal("10.5")));
        assertTrue(PaperMatch.limitTouched("BUY", new BigDecimal("10.5"), new BigDecimal("11"), new BigDecimal("10.5")));
    }

    @Test
    void theSameBarHitsTheStopBeforeTheTakeProfit() {
        assertEquals("STOP", PaperMatch.protectiveExit(
                new BigDecimal("90"), new BigDecimal("110"), new BigDecimal("111"), new BigDecimal("89")));
        assertEquals("TAKE_PROFIT", PaperMatch.protectiveExit(
                new BigDecimal("90"), new BigDecimal("110"), new BigDecimal("111"), new BigDecimal("95")));
    }

    @Test
    void aPartialFillCannotExceedTheRemainingQuantity() {
        assertEquals(new BigDecimal("4.000000000000"), PaperMatch.fillable(new BigDecimal("10"), new BigDecimal("4")));
        assertThrows(IllegalArgumentException.class, () -> PaperMatch.fillable(new BigDecimal("10"), new BigDecimal("11")));
    }

    @Test
    void theSameSignalCannotOpenASecondOrder() {
        OrderLog log = new OrderLog();
        String first = log.place("paper-c", "V1_2", "signal-1", "BUY");
        String second = log.place("paper-c", "V1_2", "signal-1", "BUY");
        assertEquals(first, second);
        assertEquals(1, log.size());
    }
}
