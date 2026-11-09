package com.cti.market;

import com.cti.market.model.FuturesPoint;

import java.util.List;

public interface FuturesMarketClient {

    long serverTime();

    List<FuturesPoint> collect(String symbol, long startTime, long serverTime);
}
