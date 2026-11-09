package com.cti.portfolio.book;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OrderLog {

    private final Map<String, String> orders = new LinkedHashMap<>();

    public String place(String accountId, String strategyCode, String signalId, String side) {
        String key = accountId + "|" + strategyCode + "|" + signalId + "|" + side;
        return orders.computeIfAbsent(key, ignored -> key);
    }

    public int size() {
        return orders.size();
    }
}
