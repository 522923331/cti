package com.cti.market.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BinanceFuturesClientTest {

    @Test
    void retriesAfterATimeoutStatusThenReadsServerTime() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            if (calls.incrementAndGet() == 1) {
                exchange.sendResponseHeaders(503, -1);
                return;
            }
            byte[] body = "{\"serverTime\":42}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            BinanceFuturesClient client = new BinanceFuturesClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    2, Duration.ofSeconds(2), new ObjectMapper(), duration -> {
            });
            assertEquals(42L, client.serverTime());
            assertEquals(2, calls.get());
        } finally {
            server.stop(0);
        }
    }
}
