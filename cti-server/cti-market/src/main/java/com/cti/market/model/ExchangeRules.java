package com.cti.market.model;

import java.util.Map;

public record ExchangeRules(Long requestWeightPerMinute, Map<String, SymbolRule> symbols) {
}
