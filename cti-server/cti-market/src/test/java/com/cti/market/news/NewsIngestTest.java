package com.cti.market.news;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsIngestTest {

    @Test
    void sameUrlUpdatesAndSameTitleDoesNotCreateAnotherScoredEvent() {
        MemoryNewsEventStore store = new MemoryNewsEventStore();
        AnnouncementClient client = () -> List.of(
                article("a", "Binance Will List Bitcoin"),
                article("b", "binance will list bitcoin!"),
                article("a", "Binance Will List Bitcoin"));
        String result = new NewsIngest().ingest(client, new TitleNewsClassifier(), store, List.of("BTC"));
        assertEquals(1, store.count());
        assertTrue(result.contains("inserted=1"));
        assertTrue(result.contains("skipped=1"));
        assertTrue(result.contains("updated=1"));
        assertEquals("https://www.binance.com/en/support/announcement/a", store.only().sourceUrl());
        assertEquals(List.of("BTC"), store.only().assets());
        assertTrue(!result.contains("BUY"));
    }

    @Test
    void rejectsAClassificationThatNamesATradeAction() {
        MemoryNewsEventStore store = new MemoryNewsEventStore();
        NewsClassifier bad = (title, assets) -> Optional.of(new NewsDraft(
                "EXCHANGE", "BUY", "LOW", new BigDecimal("0.400000"), List.of("MARKET_WIDE"), title));
        String result = new NewsIngest().ingest(() -> List.of(article("a", "Anything")), bad, store, List.of("BTC"));
        assertEquals(0, store.count());
        assertTrue(result.contains("rejected=1"));
    }

    @Test
    void anAnnouncementOutageWritesNothing() {
        MemoryNewsEventStore store = new MemoryNewsEventStore();
        AnnouncementClient client = () -> {
            throw new IllegalStateException("timeout");
        };
        assertThrows(IllegalStateException.class,
                () -> new NewsIngest().ingest(client, new TitleNewsClassifier(), store, List.of("BTC")));
        assertEquals(0, store.count());
    }

    @Test
    void aTitleWithoutAKnownAssetIsMarketWide() {
        MemoryNewsEventStore store = new MemoryNewsEventStore();
        new NewsIngest().ingest(() -> List.of(article("z", "Weekly Operations Note")),
                new TitleNewsClassifier(), store, List.of("ETH"));
        assertEquals(List.of("MARKET_WIDE"), store.only().assets());
    }

    private static Announcement article(String code, String title) {
        return new Announcement(code, title, 10L, "Listing",
                "https://www.binance.com/en/support/announcement/" + code);
    }

    private static final class MemoryNewsEventStore implements NewsEventStore {
        private final List<StoredNews> rows = new ArrayList<>();

        @Override
        public Optional<StoredNews> findByEventId(String eventId) {
            return rows.stream().filter(row -> row.eventId().equals(eventId)).findFirst();
        }

        @Override
        public Optional<StoredNews> findByContentHash(String providerCode, String contentHash) {
            return rows.stream()
                    .filter(row -> row.providerCode().equals(providerCode) && row.contentHash().equals(contentHash))
                    .findFirst();
        }

        @Override
        public void insert(StoredNews event) {
            rows.add(event);
        }

        @Override
        public void update(StoredNews event) {
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).eventId().equals(event.eventId())) {
                    rows.set(index, event);
                    return;
                }
            }
            throw new IllegalStateException("missing " + event.eventId());
        }

        int count() {
            return rows.size();
        }

        StoredNews only() {
            assertEquals(1, rows.size());
            return rows.get(0);
        }
    }
}
