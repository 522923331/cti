package com.cti.market.binance;

import com.cti.market.model.FuturesPoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class BinanceFuturesParsers {

    static final long FOUR_HOURS_MS = 14_400_000L;

    private BinanceFuturesParsers() {
    }

    public static List<FuturesPoint> fundingRates(String json, ObjectMapper mapper, long serverTime) {
        List<FuturesPoint> points = new ArrayList<>();
        for (JsonNode row : array(json, mapper)) {
            long eventTime = row.path("fundingTime").asLong();
            if (eventTime <= 0 || eventTime > serverTime) {
                continue;
            }
            add(points, row, row.path("symbol").asText(), "FUNDING_RATE", "8h", eventTime, decimal(row.get("fundingRate")), false);
        }
        return points;
    }

    public static List<FuturesPoint> openInterestHistory(String json, ObjectMapper mapper, long serverTime) {
        List<FuturesPoint> points = new ArrayList<>();
        for (JsonNode row : array(json, mapper)) {
            long eventTime = row.path("timestamp").asLong();
            if (eventTime <= 0 || eventTime + FOUR_HOURS_MS > serverTime) {
                continue;
            }
            String symbol = row.path("symbol").asText();
            add(points, row, symbol, "OPEN_INTEREST", "4h", eventTime, decimal(row.get("sumOpenInterest")), true);
            add(points, row, symbol, "OPEN_INTEREST_VALUE", "4h", eventTime, decimal(row.get("sumOpenInterestValue")), true);
        }
        return points;
    }

    public static List<FuturesPoint> takerVolume(String symbol, String json, ObjectMapper mapper, long serverTime) {
        List<FuturesPoint> points = new ArrayList<>();
        for (JsonNode row : array(json, mapper)) {
            long eventTime = row.path("timestamp").asLong();
            if (eventTime <= 0 || eventTime + FOUR_HOURS_MS > serverTime) {
                continue;
            }
            add(points, row, symbol, "TAKER_BUY_VOLUME", "4h", eventTime, decimal(row.get("buyVol")), true);
            add(points, row, symbol, "TAKER_SELL_VOLUME", "4h", eventTime, decimal(row.get("sellVol")), true);
        }
        return points;
    }

    public static List<FuturesPoint> premiumIndex(String json, ObjectMapper mapper) {
        JsonNode row = object(json, mapper);
        long eventTime = row.path("time").asLong();
        String symbol = row.path("symbol").asText();
        List<FuturesPoint> points = new ArrayList<>();
        addSnapshot(points, row, symbol, "MARK_PRICE", eventTime, decimal(row.get("markPrice")));
        addSnapshot(points, row, symbol, "INDEX_PRICE", eventTime, decimal(row.get("indexPrice")));
        addSnapshot(points, row, symbol, "LAST_FUNDING_RATE", eventTime, decimal(row.get("lastFundingRate")));
        return points;
    }

    public static List<FuturesPoint> currentOpenInterest(String json, ObjectMapper mapper) {
        JsonNode row = object(json, mapper);
        List<FuturesPoint> points = new ArrayList<>();
        addSnapshot(points, row, row.path("symbol").asText(), "OPEN_INTEREST", row.path("time").asLong(), decimal(row.get("openInterest")));
        return points;
    }

    private static void add(List<FuturesPoint> points, JsonNode row, String symbol, String metric, String interval,
                            long eventTime, BigDecimal value, boolean rejectNegative) {
        if (symbol == null || symbol.isBlank() || eventTime <= 0) {
            return;
        }
        String status = status(value, rejectNegative, "CLOSED");
        points.add(new FuturesPoint(symbol, metric, interval, eventTime, value, status, raw(row)));
    }

    private static void addSnapshot(List<FuturesPoint> points, JsonNode row, String symbol, String metric,
                                    long eventTime, BigDecimal value) {
        if (symbol == null || symbol.isBlank() || eventTime <= 0) {
            return;
        }
        boolean rejectNegative = "OPEN_INTEREST".equals(metric) || metric.endsWith("_PRICE");
        points.add(new FuturesPoint(symbol, metric, "snapshot", eventTime, value, status(value, rejectNegative, "SNAPSHOT"), raw(row)));
    }

    private static String status(BigDecimal value, boolean rejectNegative, String ok) {
        if (value == null) {
            return "ABSENT";
        }
        if (rejectNegative && value.signum() < 0) {
            return "INVALID";
        }
        return ok;
    }

    private static List<JsonNode> array(String json, ObjectMapper mapper) {
        try {
            JsonNode rows = mapper.readTree(json);
            if (!rows.isArray()) {
                throw new IllegalArgumentException("衍生品响应不是数组");
            }
            List<JsonNode> out = new ArrayList<>();
            rows.forEach(out::add);
            return out;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法解析衍生品响应", ex);
        }
    }

    private static JsonNode object(String json, ObjectMapper mapper) {
        try {
            JsonNode row = mapper.readTree(json);
            if (!row.isObject()) {
                throw new IllegalArgumentException("衍生品响应不是对象");
            }
            return row;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法解析衍生品响应", ex);
        }
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String raw(JsonNode row) {
        return row.toString();
    }
}
