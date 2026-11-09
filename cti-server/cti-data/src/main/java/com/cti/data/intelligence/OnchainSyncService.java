package com.cti.data.intelligence;

import com.cti.market.onchain.OnchainDataProvider;
import com.cti.market.onchain.OnchainSnapshotStore;
import com.cti.market.onchain.OnchainSync;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OnchainSyncService {

    private final OnchainDataProvider provider;
    private final OnchainSnapshotStore store;
    private final JdbcOnchainSnapshotRepository snapshots;

    public OnchainSyncService(OnchainDataProvider provider, OnchainSnapshotStore store,
                               JdbcOnchainSnapshotRepository snapshots) {
        this.provider = provider;
        this.store = store;
        this.snapshots = snapshots;
    }

    public String sync() {
        return new OnchainSync().sync(provider, store);
    }

    public OnchainStatus status() {
        if (!provider.configured()) {
            return new OnchainStatus("UNAVAILABLE", provider.providerCode(), List.of());
        }
        return new OnchainStatus("CONFIGURED", provider.providerCode(), snapshots.latest(50));
    }
}
