package com.cti.market.binance;

import com.cti.market.news.Announcement;
import com.cti.market.news.AnnouncementClient;
import com.cti.market.news.BinanceAnnouncementParsers;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class BinanceAnnouncementClient implements AnnouncementClient {

    private static final String PATH =
            "/bapi/composite/v1/public/cms/article/list/query?type=1&pageNo=1&pageSize=20";

    private final String baseUrl;
    private final int maxRetries;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final Sleeper sleeper;
    private final HttpClient httpClient;
    private volatile long bannedUntilEpochMs;

    public BinanceAnnouncementClient(BinanceMarketProperties properties, ObjectMapper mapper, Sleeper sleeper) {
        this(properties.getAnnouncementBaseUrl(), properties.getMaxRetries(),
                Duration.ofSeconds(properties.getRequestTimeoutSeconds()), mapper, sleeper);
    }

    public BinanceAnnouncementClient(String baseUrl, int maxRetries, Duration timeout, ObjectMapper mapper, Sleeper sleeper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.maxRetries = maxRetries;
        this.timeout = timeout;
        this.mapper = mapper;
        this.sleeper = sleeper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public List<Announcement> latest() {
        return BinanceAnnouncementParsers.parse(get(PATH), mapper);
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
                    bannedUntilEpochMs = System.currentTimeMillis() + 60_000L;
                    throw new BinanceBannedException("HTTP 418，暂停请求公告");
                }
                if (status == 429 || status >= 500) {
                    last = new IllegalStateException("HTTP " + status);
                    sleeper.sleep(Duration.ofMillis(Math.min(500L << Math.min(attempt - 1, 4), 8_000L)));
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
                sleeper.sleep(Duration.ofMillis(Math.min(500L << Math.min(attempt - 1, 4), 8_000L)));
            }
        }
        throw last == null ? new IllegalStateException("币安公告请求失败") : last;
    }

    private void refuseIfBanned() {
        long remaining = bannedUntilEpochMs - System.currentTimeMillis();
        if (remaining > 0) {
            throw new BinanceBannedException("Binance 仍在封禁窗口内，剩余 " + remaining + "ms");
        }
    }
}
