package com.cti.data.store;

import com.cti.market.model.KlineBar;

import java.util.List;

public record SaveBatch(int inserted, int updated, int revised, int unchanged, List<KlineBar> changed) {

    public static SaveBatch empty() {
        return new SaveBatch(0, 0, 0, 0, List.of());
    }
}
