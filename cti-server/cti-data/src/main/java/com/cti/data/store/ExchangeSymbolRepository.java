package com.cti.data.store;

import com.cti.market.model.SymbolRule;

import java.time.Instant;
import java.util.Map;

public interface ExchangeSymbolRepository {

    void upsert(SymbolRule rule, Instant syncedAt);

    void markMissing(String symbol, String baseAsset, String quoteAsset, Instant syncedAt);

    String statusOf(String symbol);

    Map<String, String> statuses();
}
