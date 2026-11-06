package com.cti.market.binance;

public class BinanceRejectedException extends RuntimeException {

    public BinanceRejectedException(String message) {
        super(message);
    }
}
