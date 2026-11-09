package com.cti.data.sync;

import com.cti.data.kline.QualityReportRepository;
import com.cti.data.kline.SaveAction;
import com.cti.data.kline.StoredKline;
import com.cti.data.store.AssetRepository;
import com.cti.data.store.AssetRow;
import com.cti.data.store.ExchangeSymbolRepository;
import com.cti.data.parquet.KlineArchive;
import com.cti.data.store.KlineRepository;
import com.cti.data.store.KlineStat;
import com.cti.data.store.SaveBatch;
import com.cti.market.SpotMarketClient;
import com.cti.market.binance.CandleInterval;
import com.cti.market.model.ExchangeRules;
import com.cti.market.model.KlineBar;
import com.cti.market.model.SymbolRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketSyncServiceTest {

    @Test
    void backfillFillsTheHoleAndASecondRunDoesNotDuplicate() {
        long step = CandleInterval.D1.millis();
        long serverTime = 20 * step + 5_000;
        long hole = CandleInterval.alignDown(serverTime, step) - 2 * step;
        MemoryKlines memory = new MemoryKlines();
        SpotMarketClient client = new FakeClient(serverTime, hole);
        List<List<KlineBar>> archived = new ArrayList<>();
        MarketSyncService service = new MarketSyncService(client, new OneAsset(), new MemorySymbols(), memory,
                new QualityReportRepository() {
                    @Override
                    public void save(com.cti.data.kline.QualityReport report) {
                    }

                    @Override
                    public List<com.cti.data.kline.QualityReport> latest() {
                        return List.of();
                    }
                },
                new KlineArchive() {
                    @Override
                    public void write(String symbol, String interval, List<KlineBar> bars) {
                        archived.add(List.copyOf(bars));
                    }
                }, 5);

        service.syncKlines("1d");
        long count = memory.rows.size();
        assertTrue(count > 3);
        assertTrue(memory.rows.containsKey(hole));
        assertEquals(1, archived.size());

        service.syncKlines("1d");
        assertEquals(count, memory.rows.size());
        assertEquals(1, archived.size());
        assertEquals(1, memory.rows.values().stream().filter(row -> !row.bar().closed()).count());
    }

    private static KlineBar bar(long open, long serverTime) {
        long step = CandleInterval.D1.millis();
        return new KlineBar("BINANCE", "BTCUSDT", "1d", open, open + step - 1,
                new BigDecimal("10"), new BigDecimal("12"), new BigDecimal("9"), new BigDecimal("11"),
                new BigDecimal("1"), new BigDecimal("11"), 2L, new BigDecimal("0.5"), new BigDecimal("5"), false)
                .closedAt(serverTime);
    }

    private static final class FakeClient implements SpotMarketClient {
        private final long serverTime;
        private final long hole;

        private FakeClient(long serverTime, long hole) {
            this.serverTime = serverTime;
            this.hole = hole;
        }

        @Override
        public long serverTime() {
            return serverTime;
        }

        @Override
        public ExchangeRules exchangeRules() {
            return new ExchangeRules(6000L, Map.of("BTCUSDT",
                    new SymbolRule("BTCUSDT", "BTC", "USDT", "TRADING", null, null, null)));
        }

        @Override
        public List<KlineBar> klines(String symbol, String interval, long startTime, Long endTime, int limit) {
            long step = CandleInterval.D1.millis();
            long historyStart = CandleInterval.alignDown(serverTime - 5 * 86_400_000L, step);
            long current = CandleInterval.alignDown(serverTime, step);
            List<KlineBar> page = new ArrayList<>();
            for (long open = startTime; open <= current && page.size() < limit; open += step) {
                if (endTime != null && open > endTime) {
                    break;
                }
                if (open < historyStart) {
                    continue;
                }
                if (open == hole && startTime != hole) {
                    continue;
                }
                page.add(bar(open, serverTime));
            }
            return page;
        }
    }

    private static final class OneAsset implements AssetRepository {
        @Override
        public List<AssetRow> enabled() {
            return List.of(new AssetRow("BTC", "Bitcoin", "BTCUSDT", "CORE", true));
        }

        @Override
        public Optional<AssetRow> find(String symbolOrCode) {
            return Optional.of(enabled().get(0));
        }
    }

    private static final class MemorySymbols implements ExchangeSymbolRepository {
        private final Map<String, String> statuses = new HashMap<>();

        @Override
        public void upsert(SymbolRule rule, Instant syncedAt) {
            statuses.put(rule.symbol(), rule.status());
        }

        @Override
        public void markMissing(String symbol, String baseAsset, String quoteAsset, Instant syncedAt) {
            statuses.put(symbol, "MISSING");
        }

        @Override
        public String statusOf(String symbol) {
            return statuses.get(symbol);
        }

        @Override
        public Map<String, String> statuses() {
            return statuses;
        }
    }

    private static final class MemoryKlines implements KlineRepository {
        private final Map<Long, Row> rows = new TreeMap<>();

        @Override
        public SaveBatch saveAll(List<KlineBar> bars) {
            int inserted = 0;
            int updated = 0;
            int revised = 0;
            int unchanged = 0;
            List<KlineBar> changed = new ArrayList<>();
            for (KlineBar bar : bars) {
                Row existing = rows.get(bar.openTime());
                StoredKline stored = existing == null ? null : existing.stored();
                SaveAction action = SaveAction.decide(stored, bar);
                switch (action) {
                    case INSERT -> inserted++;
                    case UPDATE -> updated++;
                    case REVISE -> revised++;
                    case UNCHANGED -> unchanged++;
                }
                if (action != SaveAction.UNCHANGED) {
                    rows.put(bar.openTime(), new Row(bar, false));
                    changed.add(bar);
                }
            }
            return new SaveBatch(inserted, updated, revised, unchanged, changed);
        }

        @Override
        public List<Long> openTimes(String symbol, String interval, long fromOpen, long toOpen) {
            return rows.keySet().stream().filter(open -> open >= fromOpen && open <= toOpen).sorted().toList();
        }

        @Override
        public List<KlineBar> load(String symbol, String interval, long fromOpen, long toOpen) {
            return rows.entrySet().stream()
                    .filter(entry -> entry.getKey() >= fromOpen && entry.getKey() <= toOpen)
                    .map(entry -> entry.getValue().bar)
                    .toList();
        }

        @Override
        public List<KlineBar> pending(String symbol, String interval) {
            return rows.values().stream().filter(row -> !row.archived).map(row -> row.bar)
                    .sorted(Comparator.comparingLong(KlineBar::openTime)).toList();
        }

        @Override
        public void markArchived(String symbol, String interval, List<Long> openTimes) {
            for (Long open : openTimes) {
                Row row = rows.get(open);
                rows.put(open, new Row(row.bar, true));
            }
        }

        @Override
        public List<KlineStat> stats() {
            return List.of();
        }

        @Override
        public List<KlineBar> latest(String symbol, String interval, int limit) {
            return List.of();
        }

        private record Row(KlineBar bar, boolean archived) {
            private StoredKline stored() {
                return new StoredKline(bar.openTime(), bar.open(), bar.high(), bar.low(), bar.close(),
                        bar.volume(), bar.quoteVolume(), bar.tradeCount(), bar.takerBuyBase(), bar.takerBuyQuote(),
                        bar.closed());
            }
        }
    }
}
