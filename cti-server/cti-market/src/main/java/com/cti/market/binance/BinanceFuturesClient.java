package com.cti.market.binance;

import com.cti.market.FuturesMarketClient;
import com.cti.market.model.FuturesPoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class BinanceFuturesClient implements FuturesMarketClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceFuturesClient.class);
    private static final int LIMIT = 500;

    private final String baseUrl;
    private final int maxRetries;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final Sleeper sleeper;
    private final HttpClient httpClient;
    private volatile long bannedUntilEpochMs;

    public BinanceFuturesClient(BinanceMarketProperties properties, ObjectMapper mapper, Sleeper sleeper) {
        this(properties.getFuturesBaseUrl(), properties.getMaxRetries(),
                Duration.ofSeconds(properties.getRequestTimeoutSeconds()), mapper, sleeper);
    }

    public BinanceFuturesClient(String baseUrl, int maxRetries, Duration timeout, ObjectMapper mapper, Sleeper sleeper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.maxRetries = maxRetries;
        this.timeout = timeout;
        this.mapper = mapper;
        this.sleeper = sleeper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public long serverTime() {
        return BinanceParsers.serverTime(get("/fapi/v1/time"), mapper);
    }

    @Override
    public List<FuturesPoint> collect(String symbol, long startTime, long serverTime) {
        List<FuturesPoint> points = new ArrayList<>();
        points.addAll(BinanceFuturesParsers.premiumIndex(get("/fapi/v1/premiumIndex?symbol=" + symbol), mapper));
        points.addAll(BinanceFuturesParsers.currentOpenInterest(get("/fapi/v1/openInterest?symbol=" + symbol), mapper));
        points.addAll(pages("/fapi/v1/fundingRate", symbol, "", "fundingTime", startTime,
                json -> BinanceFuturesParsers.fundingRates(json, mapper, serverTime)));
        points.addAll(pages("/futures/data/openInterestHist", symbol, "&period=4h", "timestamp", startTime,
                json -> BinanceFuturesParsers.openInterestHistory(json, mapper, serverTime)));
        points.addAll(pages("/futures/data/takerlongshortRatio", symbol, "&period=4h", "timestamp", startTime,
                json -> BinanceFuturesParsers.takerVolume(symbol, json, mapper, serverTime)));
        return points;
    }

    private List<FuturesPoint> pages(String endpoint, String symbol, String extra, String timeField, long startTime, PageParser parser) {
        List<FuturesPoint> points = new ArrayList<>();
        long cursor = startTime;
        for (int page = 0; page < 5; page++) {
            String path = endpoint + "?symbol=" + symbol + extra + "&startTime=" + cursor + "&limit=" + LIMIT;
            String json = get(path);
            points.addAll(parser.parse(json));
            JsonNode rows = readArray(json);
            if (!rows.isArray() || rows.isEmpty() || rows.size() < LIMIT) {
                return points;
            }
            long last = rows.get(rows.size() - 1).path(timeField).asLong();
            if (last < cursor) {
                return points;
            }
            cursor = last + 1;
        }
        return points;
    }

    private JsonNode readArray(String json) {
        try {
            return mapper.readTree(json);
        } catch (IOException ex) {
            throw new IllegalArgumentException("无法解析衍生品分页", ex);
        }
    }

    private String get(String pathAndQuery) {
        refuseIfBanned();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            refuseIfBanned();
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
                        .timeout(timeout)
                        .header("Accept", "application/json")
                        .header("User-Agent", "cti-market/1.0")
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 200) {
                    return response.body();
                }
                if (status == 418) {
                    long pause = retryAfterMillis(response, 60_000);
                    bannedUntilEpochMs = System.currentTimeMillis() + pause;
                    throw new BinanceBannedException("HTTP 418，暂停请求 " + pause + "ms");
                }
                if (status == 429 || status >= 500) {
                    last = new IllegalStateException("HTTP " + status);
                    sleeper.sleep(Duration.ofMillis(Math.max(backoff(attempt), retryAfterMillis(response, 0))));
                    continue;
                }
                throw new BinanceRejectedException("HTTP " + status);
            } catch (BinanceBannedException | BinanceRejectedException ex) {
                throw ex;
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                last = new IllegalStateException(ex.getMessage(), ex);
                sleeper.sleep(Duration.ofMillis(backoff(attempt)));
            }
        }
        throw last == null ? new IllegalStateException("Binance 合约请求失败") : last;
    }

    private void refuseIfBanned() {
        long remaining = bannedUntilEpochMs - System.currentTimeMillis();
        if (remaining > 0) {
            throw new BinanceBannedException("Binance 仍在封禁窗口内，剩余 " + remaining + "ms");
        }
    }

    private static long retryAfterMillis(HttpResponse<?> response, long defaultMillis) {
        String value = response.headers().firstValue("retry-after").orElse("").trim();
        if (!value.isEmpty() && value.chars().allMatch(Character::isDigit)) {
            return Long.parseLong(value) * 1000L;
        }
        return defaultMillis;
    }

    private static long backoff(int attempt) {
        return Math.min(500L << Math.min(attempt - 1, 4), 8_000L);
    }

    @FunctionalInterface
    private interface PageParser {
        List<FuturesPoint> parse(String json);
    }
}
