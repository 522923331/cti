package com.cti.data.kline;

import com.cti.market.model.KlineBar;

public enum SaveAction {
    INSERT,
    UPDATE,
    REVISE,
    UNCHANGED;

    public static SaveAction decide(StoredKline existing, KlineBar incoming) {
        if (existing == null) {
            return INSERT;
        }
        boolean same = existing.sameMarketData(incoming);
        if (same && existing.closed() == incoming.closed()) {
            return UNCHANGED;
        }
        if (existing.closed() && !same) {
            return REVISE;
        }
        return UPDATE;
    }
}
