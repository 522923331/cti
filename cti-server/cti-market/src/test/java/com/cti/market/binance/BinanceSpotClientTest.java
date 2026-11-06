package com.cti.market.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BinanceSpotClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void retries429ThenReadsTheBody() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            if (calls.incrementAndGet() == 1) {
                exchange.getResponseHeaders().add("Retry-After", "0");
                exchange.sendResponseHeaders(429, -1);
                return;
            }
            byte[] body = "{\"serverTime\":1700000000000}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        });
        try {
            BinanceSpotClient client = client(server, "", 3);
            assertEquals(1700000000000L, client.serverTime());
            assertEquals(2, calls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void stopsOn418WithoutCallingFallback() throws Exception {
        AtomicInteger primary = new AtomicInteger();
        AtomicInteger fallback = new AtomicInteger();
        HttpServer banned = server(exchange -> {
            primary.incrementAndGet();
            exchange.getResponseHeaders().add("Retry-After", "30");
            exchange.sendResponseHeaders(418, -1);
        });
        HttpServer spare = server(exchange -> {
            fallback.incrementAndGet();
            byte[] body = "{\"serverTime\":1}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        });
        try {
            BinanceSpotClient client = client(banned, url(spare), 3);
            assertThrows(BinanceBannedException.class, client::serverTime);
            assertEquals(1, primary.get());
            assertEquals(0, fallback.get());
        } finally {
            banned.stop(0);
            spare.stop(0);
        }
    }

    @Test
    void usesFallbackAfterPrimaryFailures() throws Exception {
        HttpServer primary = server(exchange -> exchange.sendResponseHeaders(503, -1));
        HttpServer fallback = server(exchange -> {
            byte[] body = "{\"serverTime\":42}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        });
        try {
            BinanceSpotClient client = client(primary, url(fallback), 1);
            assertEquals(42L, client.serverTime());
        } finally {
            primary.stop(0);
            fallback.stop(0);
        }
    }

    private BinanceSpotClient client(HttpServer primary, String fallback, int retries) {
        return new BinanceSpotClient(url(primary), fallback, retries, Duration.ofSeconds(2), mapper, duration -> {
        });
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return server;
    }
}
