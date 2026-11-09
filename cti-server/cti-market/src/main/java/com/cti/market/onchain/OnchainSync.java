package com.cti.market.onchain;

public final class OnchainSync {

    public String sync(OnchainDataProvider provider, OnchainSnapshotStore store) {
        if (!provider.configured()) {
            return "onchain=UNAVAILABLE provider=" + provider.providerCode() + " rows=0";
        }
        int rows = 0;
        for (String asset : new String[]{"BTC", "ETH"}) {
            rows += store.saveAll(provider.fetchMetrics(asset, 0L, 0L), provider.providerCode());
        }
        return "onchain=CONFIGURED provider=" + provider.providerCode() + " rows=" + rows;
    }
}
