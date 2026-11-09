package com.cti.data.store;

public record KlineStat(String symbol, String intervalCode, long count, long unclosed, long latestOpenTime) {
}
