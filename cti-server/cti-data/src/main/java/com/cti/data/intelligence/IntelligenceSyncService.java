package com.cti.data.intelligence;

import com.cti.data.store.AssetRepository;
import com.cti.data.store.AssetRow;
import com.cti.data.store.KlineRepository;
import com.cti.data.sync.MarketSyncService;
import com.cti.market.FuturesMarketClient;
import com.cti.market.model.FuturesPoint;
import com.cti.market.model.KlineBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class IntelligenceSyncService {

    private static final Logger log = LoggerFactory.getLogger(IntelligenceSyncService.class);
    private static final String FUTURES_PROVIDER = "BINANCE_USD_M";
    private static final String SPOT_PROVIDER = "BINANCE";
    private static final long HISTORY_MS = 30L * 86_400_000L;

    private final FuturesMarketClient futures;
    private final AssetRepository assets;
    private final KlineRepository klines;
    private final DerivativesMetricRepository metrics;
    private final MarketSnapshotRepository snapshots;
    private final MarketSyncService marketSync;

    public IntelligenceSyncService(FuturesMarketClient futures, AssetRepository assets, KlineRepository klines,
                                   DerivativesMetricRepository metrics, MarketSnapshotRepository snapshots,
                                   MarketSyncService marketSync) {
        this.futures = futures;
        this.assets = assets;
        this.klines = klines;
        this.metrics = metrics;
        this.snapshots = snapshots;
        this.marketSync = marketSync;
    }

    public String syncDerivatives() {
        long serverTime = futures.serverTime();
        long start = serverTime - HISTORY_MS;
        int rows = 0;
        List<String> failures = new ArrayList<>();
        List<AssetRow> enabled = assets.enabled();
        for (AssetRow asset : enabled) {
            try {
                List<FuturesPoint> points = futures.collect(asset.symbol(), start, serverTime);
                rows += metrics.upsertAll(points, Instant.now().toEpochMilli(), FUTURES_PROVIDER);
            } catch (RuntimeException ex) {
                log.error("derivatives sync failed for {}", asset.symbol(), ex);
                failures.add(asset.symbol() + ": " + ex.getMessage());
            }
        }
        String summary = "symbols=" + enabled.size() + " rows=" + rows + " provider=" + FUTURES_PROVIDER;
        if (!failures.isEmpty()) {
            throw new IllegalStateException(summary + "; FAILED " + String.join(" | ", failures));
        }
        return summary;
    }

    public String syncMarketOverview() {
        String kline = marketSync.syncReference("ETHBTC", "1d");
        KlineBar closed = latestClosed();
        long snapshotTime = closed == null
                ? LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                : closed.openTime();
        snapshots.upsert("ETHBTC_CLOSE", snapshotTime, closed == null ? null : closed.close(), "BTC",
                SPOT_PROVIDER, closed == null ? "ABSENT" : "CLOSED");
        snapshots.upsert("BTC_DOMINANCE", snapshotTime, null, "RATIO", "NONE", "ABSENT");
        snapshots.upsert("TOTAL", snapshotTime, null, "USD", "NONE", "ABSENT");
        snapshots.upsert("TOTAL3", snapshotTime, null, "USD", "NONE", "ABSENT");
        return kline + "; snapshotTime=" + snapshotTime + " ethbtc=" + (closed == null ? "ABSENT" : "CLOSED")
                + " dominance=ABSENT total=ABSENT total3=ABSENT";
    }

    private KlineBar latestClosed() {
        List<KlineBar> bars = klines.latest("ETHBTC", "1d", 3);
        for (int index = bars.size() - 1; index >= 0; index--) {
            if (bars.get(index).closed()) {
                return bars.get(index);
            }
        }
        return null;
    }
}
