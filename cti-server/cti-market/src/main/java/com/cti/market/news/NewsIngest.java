package com.cti.market.news;

import java.util.List;
import java.util.Optional;

public final class NewsIngest {

    public String ingest(AnnouncementClient client, NewsClassifier classifier, NewsEventStore store, List<String> knownAssets) {
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int rejected = 0;
        for (Announcement article : client.latest()) {
            Optional<NewsDraft> draft = classifier.classify(article.title(), knownAssets);
            if (draft.isEmpty() || !NewsRules.accept(draft.get())) {
                rejected++;
                continue;
            }
            StoredNews event = event(article, draft.get());
            Optional<StoredNews> byId = store.findByEventId(event.eventId());
            Optional<StoredNews> byHash = store.findByContentHash(event.providerCode(), event.contentHash());
            if (byId.isPresent()) {
                if (byHash.isPresent() && !byHash.get().eventId().equals(event.eventId())) {
                    skipped++;
                    continue;
                }
                store.update(event);
                updated++;
            } else if (byHash.isPresent()) {
                skipped++;
            } else {
                store.insert(event);
                inserted++;
            }
        }
        return "provider=" + NewsRules.PROVIDER
                + " inserted=" + inserted
                + " updated=" + updated
                + " skipped=" + skipped
                + " rejected=" + rejected;
    }

    private static StoredNews event(Announcement article, NewsDraft draft) {
        String eventId = NewsIds.eventId(article.sourceUrl());
        return new StoredNews(
                eventId,
                NewsRules.PROVIDER,
                article.title(),
                article.sourceUrl(),
                article.catalogName(),
                eventId,
                NewsIds.contentHash(article.title()),
                draft.category(),
                draft.sentiment(),
                draft.impactLevel(),
                draft.confidence(),
                draft.summary(),
                article.publishedAtMs(),
                draft.assets());
    }
}
