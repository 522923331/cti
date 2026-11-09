package com.cti.data.query;

import java.util.Map;

public record AssetSnapshot(
        String assetCode,
        String assetName,
        String symbol,
        String assetType,
        boolean enabled,
        String exchangeStatus,
        Map<String, KlineStatView> klines
) {
}
