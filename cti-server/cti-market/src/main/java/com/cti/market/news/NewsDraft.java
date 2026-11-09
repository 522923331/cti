package com.cti.market.news;

import java.math.BigDecimal;
import java.util.List;

public record NewsDraft(
        String category,
        String sentiment,
        String impactLevel,
        BigDecimal confidence,
        List<String> assets,
        String summary
) {
}
