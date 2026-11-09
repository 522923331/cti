package com.cti.data.sync;

import com.cti.data.kline.KlineGapFinder;
import com.cti.data.kline.QualityReport;
import com.cti.data.kline.QualityReportRepository;
import com.cti.data.parquet.KlineArchive;
import com.cti.data.store.AssetRepository;
import com.cti.data.store.AssetRow;
import com.cti.data.store.ExchangeSymbolRepository;
import com.cti.data.store.KlineRepository;
import com.cti.data.store.SaveBatch;
import com.cti.market.SpotMarketClient;
import com.cti.market.binance.BinanceMarketProperties;
import com.cti.market.binance.CandleInterval;
import com.cti.market.model.ExchangeRules;
import com.cti.market.model.KlineBar;
import com.cti.market.model.SymbolRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
public class MarketSyncService {

    private static final Logger log = LoggerFactory.getLogger(MarketSyncService.class);
    private static final int PAGE = 1000;

    private final SpotMarketClient client;
    private final AssetRepository assets;
    private final ExchangeSymbolRepository symbols;
    private final KlineRepository klines;
    private final QualityReportRepository quality;
    private final KlineArchive archive;
    private final int historyDays;

    @Autowired
    public MarketSyncService(SpotMarketClient client, AssetRepository assets, ExchangeSymbolRepository symbols,
                             KlineRepository klines, QualityReportRepository quality, KlineArchive archive,
                             BinanceMarketProperties properties) {
        this(client, assets, symbols, klines, quality, archive, properties.getHistoryDays());
    }

    public MarketSyncService(SpotMarketClient client, AssetRepository assets, ExchangeSymbolRepository symbols,
                             KlineRepository klines, QualityReportRepository quality, KlineArchive archive,
                             int historyDays) {
        this.client = client;
        this.assets = assets;
        this.symbols = symbols;
        this.klines = klines;
        this.quality = quality;
        this.archive = archive;
        this.historyDays = historyDays;
    }

    public String syncExchangeInfo() {
        ExchangeRules rules = client.exchangeRules();
        Instant now = Instant.now();
        int trading = 0;
        int missing = 0;
        for (AssetRow asset : assets.enabled()) {
            SymbolRule rule = rules.symbols().get(asset.symbol());
            if (rule == null) {
                symbols.markMissing(asset.symbol(), asset.assetCode(), "USDT", now);
                missing++;
            } else {
                symbols.upsert(rule, now);
                if ("TRADING".equals(rule.status())) {
                    trading++;
                }
            }
        }
        return "exchangeInfo symbols=" + rules.symbols().size()
                + " weightLimit=" + rules.requestWeightPerMinute()
                + " trading=" + trading + " missing=" + missing;
    }

    public String syncReference(String symbol, String intervalCode) {
        CandleInterval interval = CandleInterval.fromCode(intervalCode);
        long serverTime = client.serverTime();
        long step = interval.millis();
        long historyStart = CandleInterval.alignDown(serverTime - historyDays * 86_400_000L, step);
        long lastClosed = CandleInterval.alignDown(serverTime, step) - step;
        return syncSymbol(symbol, interval.code(), serverTime, historyStart, lastClosed, step);
    }

    public String syncKlines(String intervalCode) {
        String exchangeSummary = syncExchangeInfo();
        CandleInterval interval = CandleInterval.fromCode(intervalCode);
        long serverTime = client.serverTime();
        long step = interval.millis();
        long historyStart = CandleInterval.alignDown(serverTime - historyDays * 86_400_000L, step);
        long lastClosed = CandleInterval.alignDown(serverTime, step) - step;
        StringBuilder summary = new StringBuilder(exchangeSummary);
        List<String> failures = new ArrayList<>();
        for (AssetRow asset : assets.enabled()) {
            try {
                summary.append("; ").append(syncSymbol(asset.symbol(), interval.code(), serverTime, historyStart, lastClosed, step));
            } catch (RuntimeException ex) {
                log.error("kline sync failed for {} {}", asset.symbol(), interval.code(), ex);
                failures.add(asset.symbol() + ": " + ex.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException(summary + "; FAILED " + String.join(" | ", failures));
        }
        return summary.toString();
    }

    public String checkQuality() {
        long serverTime = client.serverTime();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        StringBuilder summary = new StringBuilder();
        for (CandleInterval interval : CandleInterval.values()) {
            long step = interval.millis();
            long historyStart = CandleInterval.alignDown(serverTime - historyDays * 86_400_000L, step);
            long lastClosed = CandleInterval.alignDown(serverTime, step) - step;
            for (AssetRow asset : assets.enabled()) {
                List<KlineBar> bars = klines.load(asset.symbol(), interval.code(), historyStart, serverTime);
                QualityReport report = QualityReport.check(asset.symbol(), interval.code(), bars,
                        historyStart, lastClosed, step, symbols.statusOf(asset.symbol()), today);
                quality.save(report);
                if (!summary.isEmpty()) {
                    summary.append("; ");
                }
                summary.append(asset.symbol()).append(' ').append(interval.code()).append(' ').append(report.status())
                        .append(" missing=").append(report.missingCount());
            }
        }
        return summary.toString();
    }

    private String syncSymbol(String symbol, String interval, long serverTime, long historyStart, long lastClosed, long step) {
        List<Long> existing = klines.openTimes(symbol, interval, historyStart, serverTime);
        long cursor = existing.isEmpty() ? historyStart : existing.get(existing.size() - 1);
        Totals totals = new Totals();
        fetchFrom(symbol, interval, serverTime, cursor, null, step, totals);
        List<Long> opens = klines.openTimes(symbol, interval, historyStart, lastClosed);
        List<Long> missing = KlineGapFinder.missing(historyStart, lastClosed, new HashSet<>(opens), step);
        for (long[] range : KlineGapFinder.ranges(missing, step)) {
            fetchFrom(symbol, interval, serverTime, range[0], range[1] + step - 1, step, totals);
        }
        List<KlineBar> pending = klines.pending(symbol, interval);
        if (!pending.isEmpty()) {
            archive.write(symbol, interval, pending);
            klines.markArchived(symbol, interval, pending.stream().map(KlineBar::openTime).toList());
        }
        String line = symbol + " inserted=" + totals.inserted + " updated=" + totals.updated
                + " revised=" + totals.revised + " unchanged=" + totals.unchanged
                + " gaps=" + missing.size() + " archived=" + pending.size();
        log.info("kline sync {}", line);
        return line;
    }

    private void fetchFrom(String symbol, String interval, long serverTime, long start, Long end, long step, Totals totals) {
        long cursor = start;
        while (cursor <= serverTime) {
            List<KlineBar> page = client.klines(symbol, interval, cursor, end, PAGE).stream()
                    .map(bar -> bar.closedAt(serverTime))
                    .toList();
            if (page.isEmpty()) {
                return;
            }
            SaveBatch batch = klines.saveAll(page);
            totals.add(batch);
            long last = page.get(page.size() - 1).openTime();
            if (page.size() < PAGE || last + step <= cursor) {
                return;
            }
            if (end != null && last >= end) {
                return;
            }
            cursor = last + step;
        }
    }

    private static final class Totals {
        private int inserted;
        private int updated;
        private int revised;
        private int unchanged;

        private void add(SaveBatch batch) {
            inserted += batch.inserted();
            updated += batch.updated();
            revised += batch.revised();
            unchanged += batch.unchanged();
        }
    }
}
