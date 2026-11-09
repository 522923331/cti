package com.cti.market.news;

import java.math.BigDecimal;
import java.util.List;

public record StoredNews(
        String eventId,
        String providerCode,
        String title,
        String sourceUrl,
        String sourceName,
        String urlHash,
        String contentHash,
        String category,
        String sentiment,
        String impactLevel,
        BigDecimal confidence,
        String summary,
        long publishedAtMs,
        List<String> assets
) {
}
