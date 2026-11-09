package com.cti.data.query;

public record KlinePoint(
        long openTime,
        long closeTime,
        String open,
        String high,
        String low,
        String close,
        String volume,
        boolean closed
) {
}
