package com.cti.data.query;

import com.cti.data.kline.QualityReport;
import com.cti.data.kline.QualityReportRepository;
import com.cti.data.store.AssetRepository;
import com.cti.data.store.AssetRow;
import com.cti.data.store.ExchangeSymbolRepository;
import com.cti.data.store.KlineRepository;
import com.cti.data.store.KlineStat;
import com.cti.market.binance.CandleInterval;
import com.cti.market.model.KlineBar;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MarketQueryService {

    private final AssetRepository assets;
    private final ExchangeSymbolRepository symbols;
    private final KlineRepository klines;
    private final QualityReportRepository quality;

    public MarketQueryService(AssetRepository assets, ExchangeSymbolRepository symbols,
                              KlineRepository klines, QualityReportRepository quality) {
        this.assets = assets;
        this.symbols = symbols;
        this.klines = klines;
        this.quality = quality;
    }

    public List<AssetSnapshot> assets() {
        Map<String, String> statuses = symbols.statuses();
        Map<String, Map<String, KlineStatView>> stats = statsBySymbol();
        List<AssetSnapshot> views = new ArrayList<>();
        for (AssetRow asset : assets.enabled()) {
            views.add(snapshot(asset, statuses.get(asset.symbol()), stats.getOrDefault(asset.symbol(), Map.of())));
        }
        return views;
    }

    public Optional<AssetSnapshot> asset(String symbolOrCode) {
        return assets.find(symbolOrCode).map(asset -> snapshot(
                asset,
                symbols.statusOf(asset.symbol()),
                statsBySymbol().getOrDefault(asset.symbol(), Map.of())));
    }

    public List<KlinePoint> klines(String symbol, String interval, int limit) {
        CandleInterval.fromCode(interval);
        List<KlinePoint> points = new ArrayList<>();
        for (KlineBar bar : klines.latest(symbol, interval, limit)) {
            points.add(new KlinePoint(
                    bar.openTime(),
                    bar.closeTime(),
                    bar.open().toPlainString(),
                    bar.high().toPlainString(),
                    bar.low().toPlainString(),
                    bar.close().toPlainString(),
                    bar.volume().toPlainString(),
                    bar.closed()));
        }
        return points;
    }

    public List<QualityReport> quality() {
        return quality.latest();
    }

    private AssetSnapshot snapshot(AssetRow asset, String status, Map<String, KlineStatView> stats) {
        return new AssetSnapshot(asset.assetCode(), asset.assetName(), asset.symbol(), asset.assetType(),
                asset.enabled(), status, stats);
    }

    private Map<String, Map<String, KlineStatView>> statsBySymbol() {
        Map<String, Map<String, KlineStatView>> grouped = new LinkedHashMap<>();
        for (KlineStat stat : klines.stats()) {
            grouped.computeIfAbsent(stat.symbol(), key -> new LinkedHashMap<>())
                    .put(stat.intervalCode(), new KlineStatView(stat.count(), stat.unclosed(), stat.latestOpenTime()));
        }
        return grouped;
    }
}
