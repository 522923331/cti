package com.cti.backtest.http;

import com.cti.backtest.BacktestClient;
import com.cti.feature.config.QuantProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

public class HttpBacktestClient implements BacktestClient {

    private final QuantProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpBacktestClient(QuantProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public JsonNode run(List<String> symbols, String strategy, LocalDate start, LocalDate end, String costScenario) {
        ObjectNode payload = base(symbols, start, end, costScenario);
        payload.put("strategy", strategy);
        return post("/api/v1/backtest/run", payload);
    }

    @Override
    public JsonNode compare(List<String> symbols, List<String> strategies, LocalDate start, LocalDate end, String costScenario) {
        ObjectNode payload = base(symbols, start, end, costScenario);
        payload.putPOJO("strategies", strategies);
        return post("/api/v1/backtest/compare", payload);
    }

    @Override
    public JsonNode walkForward(List<String> symbols, LocalDate start, LocalDate end, String costScenario) {
        ObjectNode payload = base(symbols, start, end, costScenario);
        payload.put("strategy", "CTI_TREND_V1");
        return post("/api/v1/backtest/walk-forward", payload);
    }

    @Override
    public JsonNode sensitivity(List<String> symbols, LocalDate start, LocalDate end, String costScenario) {
        ObjectNode payload = base(symbols, start, end, costScenario);
        payload.put("strategy", "CTI_TREND_V1");
        return post("/api/v1/backtest/sensitivity", payload);
    }

    private ObjectNode base(List<String> symbols, LocalDate start, LocalDate end, String costScenario) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putPOJO("symbols", symbols);
        payload.put("costScenario", costScenario);
        if (start != null) {
            payload.put("startDate", start.toString());
        }
        if (end != null) {
            payload.put("endDate", end.toString());
        }
        return payload;
    }

    private JsonNode post(String path, ObjectNode payload) {
        String base = properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1)
                : properties.getBaseUrl();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + path))
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new IllegalStateException("Python 回测不可用: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Python 回测被中断", ex);
        }
        JsonNode body = read(response.body());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Python 回测失败: " + body.path("error").asText(body.path("detail").asText(response.body())));
        }
        return body;
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (IOException ex) {
            throw new IllegalStateException("Python 返回的不是 JSON", ex);
        }
    }
}
