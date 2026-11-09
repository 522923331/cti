package com.cti.market.binance;

import com.cti.market.model.FuturesPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinanceFuturesParsersTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final long SERVER = 1_793_960_088_002L;

    @Test
    void keepsClosedFundingAndDropsAFutureSettlement() {
        String json = """
                [
                  {"symbol":"BTCUSDT","fundingTime":1793952000000,"fundingRate":"-0.00005491","markPrice":"1"},
                  {"symbol":"BTCUSDT","fundingTime":1793980800000,"fundingRate":"0.0001","markPrice":"1"}
                ]
                """;
        List<FuturesPoint> points = BinanceFuturesParsers.fundingRates(json, mapper, SERVER);
        assertEquals(1, points.size());
        assertEquals("FUNDING_RATE", points.get(0).metricCode());
        assertEquals("CLOSED", points.get(0).dataStatus());
        assertEquals(0, new BigDecimal("-0.00005491").compareTo(points.get(0).value()));
    }

    @Test
    void keepsOnlyClosedOpenInterestBarsAndMarksANegativeValue() {
        String json = """
                [
                  {"symbol":"BTCUSDT","sumOpenInterest":"-1","sumOpenInterestValue":"10","timestamp":1793937600000},
                  {"symbol":"BTCUSDT","sumOpenInterest":"5","sumOpenInterestValue":"20","timestamp":1793952000000}
                ]
                """;
        List<FuturesPoint> points = BinanceFuturesParsers.openInterestHistory(json, mapper, SERVER);
        assertEquals(2, points.size());
        assertTrue(points.stream().allMatch(point -> point.eventTimeMs() == 1793937600000L));
        assertEquals("INVALID", points.get(0).dataStatus());
        assertEquals("CLOSED", points.get(1).dataStatus());
        assertEquals("OPEN_INTEREST_VALUE", points.get(1).metricCode());
    }

    @Test
    void readsTakerVolumesWithoutComputingTheRatio() {
        String json = """
                [{"buySellRatio":"1.0154","sellVol":"10","buyVol":"30","timestamp":1793937600000}]
                """;
        List<FuturesPoint> points = BinanceFuturesParsers.takerVolume("BTCUSDT", json, mapper, SERVER);
        assertEquals(List.of("TAKER_BUY_VOLUME", "TAKER_SELL_VOLUME"),
                points.stream().map(FuturesPoint::metricCode).toList());
        assertEquals(0, new BigDecimal("30").compareTo(points.get(0).value()));
    }
}
