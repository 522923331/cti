package com.cti.market.news;

import java.util.Optional;

public interface NewsEventStore {

    Optional<StoredNews> findByEventId(String eventId);

    Optional<StoredNews> findByContentHash(String providerCode, String contentHash);

    void insert(StoredNews event);

    void update(StoredNews event);
}
