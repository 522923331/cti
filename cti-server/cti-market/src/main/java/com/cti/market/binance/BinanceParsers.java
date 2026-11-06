package com.cti.market.binance;

import com.cti.common.Versions;
import com.cti.market.model.ExchangeRules;
import com.cti.market.model.KlineBar;
import com.cti.market.model.SymbolRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BinanceParsers {

    private BinanceParsers() {
    }

    public static long serverTime(String json, ObjectMapper mapper) {
        try {
            return mapper.readTree(json).path("serverTime").asLong();
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法解析交易所时间", ex);
        }
    }

    public static ExchangeRules exchangeRules(String json, ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(json);
            Long weight = null;
            for (JsonNode limit : root.path("rateLimits")) {
                if ("REQUEST_WEIGHT".equals(limit.path("rateLimitType").asText())
                        && "MINUTE".equals(limit.path("interval").asText())) {
                    weight = limit.path("limit").asLong();
                }
            }
            Map<String, SymbolRule> symbols = new LinkedHashMap<>();
            for (JsonNode symbol : root.path("symbols")) {
                SymbolRule rule = symbolRule(symbol);
                symbols.put(rule.symbol(), rule);
            }
            return new ExchangeRules(weight, symbols);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法解析 exchangeInfo", ex);
        }
    }

    public static List<KlineBar> klines(String symbol, String interval, String json, ObjectMapper mapper) {
        try {
            JsonNode rows = mapper.readTree(json);
            List<KlineBar> bars = new ArrayList<>();
            if (!rows.isArray()) {
                throw new IllegalArgumentException("K 线响应不是数组");
            }
            for (JsonNode row : rows) {
                bars.add(new KlineBar(
                        Versions.EXCHANGE,
                        symbol,
                        interval,
                        row.get(0).asLong(),
                        row.get(6).asLong(),
                        decimal(row.get(1)),
                        decimal(row.get(2)),
                        decimal(row.get(3)),
                        decimal(row.get(4)),
                        decimal(row.get(5)),
                        decimal(row.get(7)),
                        row.get(8).isNull() ? null : row.get(8).asLong(),
                        decimal(row.get(9)),
                        decimal(row.get(10)),
                        false));
            }
            return bars;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法解析 K 线", ex);
        }
    }

    private static SymbolRule symbolRule(JsonNode symbol) {
        BigDecimal minQty = null;
        BigDecimal stepSize = null;
        BigDecimal minNotional = null;
        for (JsonNode filter : symbol.path("filters")) {
            String type = filter.path("filterType").asText();
            if ("LOT_SIZE".equals(type)) {
                minQty = decimal(filter.get("minQty"));
                stepSize = decimal(filter.get("stepSize"));
            } else if ("NOTIONAL".equals(type) || "MIN_NOTIONAL".equals(type)) {
                JsonNode value = filter.hasNonNull("minNotional") ? filter.get("minNotional") : filter.get("notional");
                minNotional = decimal(value);
            }
        }
        return new SymbolRule(
                symbol.path("symbol").asText(),
                symbol.path("baseAsset").asText(),
                symbol.path("quoteAsset").asText(),
                symbol.path("status").asText(),
                minQty,
                stepSize,
                minNotional);
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        return new BigDecimal(node.asText());
    }
}
