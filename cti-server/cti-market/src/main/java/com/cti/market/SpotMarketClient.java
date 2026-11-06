package com.cti.market;

import com.cti.market.model.ExchangeRules;
import com.cti.market.model.KlineBar;

import java.util.List;

public interface SpotMarketClient {

    long serverTime();

    ExchangeRules exchangeRules();

    List<KlineBar> klines(String symbol, String interval, long startTime, Long endTime, int limit);
}
