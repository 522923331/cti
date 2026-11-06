package com.cti.strategy.http;

import com.cti.feature.config.QuantProperties;
import com.cti.strategy.RegimeBatch;
import com.cti.strategy.RegimeRow;
import com.cti.strategy.ScoreBatch;
import com.cti.strategy.ScoreClient;
import com.cti.strategy.ScoreRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class HttpScoreClient implements ScoreClient {

    private final QuantProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpScoreClient(QuantProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public RegimeBatch regimes(List<String> symbols, LocalDate tradeDate) {
        JsonNode body = post("/api/v1/market/regime/calculate", symbols, tradeDate);
        List<RegimeRow> rows = new ArrayList<>();
        for (JsonNode node : body.path("rows")) {
            rows.add(new RegimeRow(
                    LocalDate.parse(text(node, "tradeDate")),
                    text(node, "regime"),
                    text(node, "rawRegime"),
                    node.path("confirmCount").asInt(),
                    decimal(node, "marketScore"),
                    decimal(node, "btcTrendScore"),
                    decimal(node, "marketBreadth"),
                    decimal(node, "btcVolatility"),
                    decimal(node, "totalExposureLimit"),
                    text(node, "riskOverride"),
                    json(node, "reason"),
                    text(node, "strategyVersion")));
        }
        return new RegimeBatch(text(body, "strategyVersion"), text(body, "featureVersion"), List.copyOf(rows));
    }

    @Override
    public ScoreBatch scores(List<String> symbols, LocalDate tradeDate) {
        JsonNode body = post("/api/v1/trend/score/calculate", symbols, tradeDate);
        List<ScoreRow> rows = new ArrayList<>();
        for (JsonNode node : body.path("rows")) {
            rows.add(new ScoreRow(
                    text(node, "symbol"),
                    LocalDate.parse(text(node, "tradeDate")),
                    decimal(node, "marketScore"),
                    decimal(node, "trendScore"),
                    decimal(node, "flowScore"),
                    decimal(node, "derivativesScore"),
                    decimal(node, "sentimentScore"),
                    decimal(node, "totalScore"),
                    text(node, "signalLevel"),
                    json(node, "reason"),
                    json(node, "risk"),
                    text(node, "featureVersion"),
                    text(node, "strategyVersion")));
        }
        return new ScoreBatch(text(body, "strategyVersion"), text(body, "featureVersion"), List.copyOf(rows));
    }

    private JsonNode post(String path, List<String> symbols, LocalDate tradeDate) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putPOJO("symbols", symbols);
        if (tradeDate != null) {
            payload.put("tradeDate", tradeDate.toString());
        }
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
            throw new IllegalStateException("Python 评分计算不可用: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Python 评分计算被中断", ex);
        }
        JsonNode body = read(response.body());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Python 评分计算失败: " + body.path("error").asText(response.body()));
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

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private static String json(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "{}";
        }
        return value.toString();
    }
}
