package com.cti.data.intelligence;

import java.math.BigDecimal;
import java.util.List;

public record NewsEventRow(
        String eventId,
        String title,
        String sourceUrl,
        String sourceName,
        String category,
        String sentiment,
        String impactLevel,
        BigDecimal confidence,
        String summary,
        Long publishedAtMs,
        List<String> assets
) {
}
