package com.cti.market.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BinanceParsersTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesKlineRowWithoutMarkingItClosed() {
        String json = """
                [[1700000000000,"100.1","110","90.5","105.25","12.5",1700086399999,"1300.5",42,"7.5","780.25","0"]]
                """;
        var bars = BinanceParsers.klines("BTCUSDT", "1d", json, mapper);
        assertEquals(1, bars.size());
        assertEquals(0, new BigDecimal("105.25").compareTo(bars.get(0).close()));
        assertEquals(0, new BigDecimal("780.25").compareTo(bars.get(0).takerBuyQuote()));
        assertEquals(42L, bars.get(0).tradeCount());
        assertEquals(false, bars.get(0).closed());
    }

    @Test
    void readsRequestWeightFromExchangeInfo() {
        String json = """
                {
                  "rateLimits": [
                    {"rateLimitType": "REQUEST_WEIGHT", "interval": "MINUTE", "intervalNum": 1, "limit": 6000}
                  ],
                  "symbols": [{
                    "symbol": "BTCUSDT",
                    "status": "TRADING",
                    "baseAsset": "BTC",
                    "quoteAsset": "USDT",
                    "filters": [
                      {"filterType": "LOT_SIZE", "minQty": "0.00001", "stepSize": "0.00001"},
                      {"filterType": "NOTIONAL", "minNotional": "5"}
                    ]
                  }]
                }
                """;
        var rules = BinanceParsers.exchangeRules(json, mapper);
        assertEquals(6000L, rules.requestWeightPerMinute());
        assertEquals("TRADING", rules.symbols().get("BTCUSDT").status());
        assertEquals(0, new BigDecimal("5").compareTo(rules.symbols().get("BTCUSDT").minNotional()));
        assertNull(rules.symbols().get("ETHUSDT"));
    }
}
