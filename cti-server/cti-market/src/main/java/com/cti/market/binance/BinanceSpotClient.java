package com.cti.market.binance;

import com.cti.market.SpotMarketClient;
import com.cti.market.model.ExchangeRules;
import com.cti.market.model.KlineBar;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class BinanceSpotClient implements SpotMarketClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceSpotClient.class);

    private final String baseUrl;
    private final String fallbackBaseUrl;
    private final int maxRetries;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final Sleeper sleeper;
    private final HttpClient httpClient;
    private volatile Long weightLimitPerMinute;
    private volatile long bannedUntilEpochMs;

    public BinanceSpotClient(BinanceMarketProperties properties, ObjectMapper mapper, Sleeper sleeper) {
        this(properties.getBaseUrl(), properties.getFallbackBaseUrl(), properties.getMaxRetries(),
                Duration.ofSeconds(properties.getRequestTimeoutSeconds()), mapper, sleeper);
    }

    public BinanceSpotClient(String baseUrl, String fallbackBaseUrl, int maxRetries, Duration timeout,
                              ObjectMapper mapper, Sleeper sleeper) {
        this.baseUrl = strip(baseUrl);
        this.fallbackBaseUrl = strip(fallbackBaseUrl);
        this.maxRetries = maxRetries;
        this.timeout = timeout;
        this.mapper = mapper;
        this.sleeper = sleeper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public long serverTime() {
        return BinanceParsers.serverTime(get("/api/v3/time"), mapper);
    }

    @Override
    public ExchangeRules exchangeRules() {
        ExchangeRules rules = BinanceParsers.exchangeRules(get("/api/v3/exchangeInfo"), mapper);
        if (rules.requestWeightPerMinute() != null) {
            weightLimitPerMinute = rules.requestWeightPerMinute();
        }
        return rules;
    }

    @Override
    public List<KlineBar> klines(String symbol, String interval, long startTime, Long endTime, int limit) {
        String path = "/api/v3/klines?symbol=" + symbol + "&interval=" + interval
                + "&startTime=" + startTime + "&limit=" + limit
                + (endTime == null ? "" : "&endTime=" + endTime);
        return BinanceParsers.klines(symbol, interval, get(path), mapper);
    }

    private String get(String pathAndQuery) {
        refuseIfBanned();
        RuntimeException last = null;
        for (String host : List.of(baseUrl, fallbackBaseUrl)) {
            if (host.isBlank()) {
                continue;
            }
            try {
                return getFrom(host, pathAndQuery);
            } catch (BinanceBannedException | BinanceRejectedException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                last = ex;
                log.warn("binance host {} failed for {}: {}", host, pathAndQuery, ex.toString());
            }
        }
        if (last == null) {
            throw new IllegalStateException("没有可用的 Binance 地址");
        }
        throw last;
    }

    private String getFrom(String host, String pathAndQuery) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            refuseIfBanned();
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(host + pathAndQuery))
                        .timeout(timeout)
                        .header("Accept", "application/json")
                        .header("User-Agent", "cti-market/1.0")
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 200) {
                    noteWeight(response);
                    return response.body();
                }
                if (status == 418) {
                    long pause = retryAfterMillis(response, 60_000);
                    bannedUntilEpochMs = System.currentTimeMillis() + pause;
                    throw new BinanceBannedException("HTTP 418，暂停请求 " + pause + "ms");
                }
                if (status == 429 || status >= 500) {
                    last = new IllegalStateException("HTTP " + status + " " + clip(response.body()));
                    sleeper.sleep(Duration.ofMillis(Math.max(backoff(attempt), retryAfterMillis(response, 0))));
                    continue;
                }
                throw new BinanceRejectedException("HTTP " + status + " " + clip(response.body()));
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
        throw last == null ? new IllegalStateException("Binance 请求失败") : last;
    }

    private void refuseIfBanned() {
        long remaining = bannedUntilEpochMs - System.currentTimeMillis();
        if (remaining > 0) {
            throw new BinanceBannedException("Binance 仍在封禁窗口内，剩余 " + remaining + "ms");
        }
    }

    private void noteWeight(HttpResponse<?> response) {
        Long limit = weightLimitPerMinute;
        if (limit == null || limit <= 0) {
            return;
        }
        String usedText = null;
        for (String name : response.headers().map().keySet()) {
            if (name.toLowerCase().startsWith("x-mbx-used-weight-")) {
                usedText = response.headers().firstValue(name).orElse(null);
            }
        }
        if (usedText == null) {
            return;
        }
        long used = Long.parseLong(usedText.trim());
        if (used >= limit * 95 / 100) {
            long wait = 60_000 - Math.floorMod(System.currentTimeMillis(), 60_000) + 200;
            log.info("binance weight {}/{}，等待 {}ms", used, limit, wait);
            sleeper.sleep(Duration.ofMillis(wait));
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
        long delay = 500L << Math.min(attempt - 1, 4);
        return Math.min(delay, 8_000L);
    }

    private static String clip(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 300 ? body : body.substring(0, 300);
    }

    private static String strip(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
