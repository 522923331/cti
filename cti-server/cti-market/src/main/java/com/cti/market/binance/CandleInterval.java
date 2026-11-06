package com.cti.market.binance;

public enum CandleInterval {
    H4("4h", 4 * 60 * 60 * 1000L),
    D1("1d", 24 * 60 * 60 * 1000L);

    private final String code;
    private final long millis;

    CandleInterval(String code, long millis) {
        this.code = code;
        this.millis = millis;
    }

    public String code() {
        return code;
    }

    public long millis() {
        return millis;
    }

    public static CandleInterval fromCode(String code) {
        for (CandleInterval interval : values()) {
            if (interval.code.equals(code)) {
                return interval;
            }
        }
        throw new IllegalArgumentException("只支持 4h 和 1d: " + code);
    }

    public static long alignDown(long epochMs, long step) {
        return Math.floorDiv(epochMs, step) * step;
    }
}
