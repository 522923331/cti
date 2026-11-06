package com.cti.market.binance;

public class BinanceBannedException extends RuntimeException {

    public BinanceBannedException(String message) {
        super(message);
    }
}
