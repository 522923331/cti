package com.cti.market.onchain;

import java.util.List;

public interface OnchainSnapshotStore {

    int saveAll(List<OnchainMetric> metrics, String providerCode);
}
