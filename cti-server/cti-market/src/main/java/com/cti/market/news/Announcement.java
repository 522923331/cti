package com.cti.market.news;

public record Announcement(
        String code,
        String title,
        long publishedAtMs,
        String catalogName,
        String sourceUrl
) {
}
