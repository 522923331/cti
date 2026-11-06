package com.cti.feature.http;

import com.cti.feature.FeatureBatch;
import com.cti.feature.FeatureRow;
import com.cti.feature.QuantFeatureClient;
import com.cti.feature.config.QuantProperties;
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

public class HttpQuantFeatureClient implements QuantFeatureClient {

    private final QuantProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpQuantFeatureClient(QuantProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public FeatureBatch calculate(List<String> symbols, LocalDate tradeDate) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putPOJO("symbols", symbols);
        if (tradeDate != null) {
            payload.put("tradeDate", tradeDate.toString());
        }
        String base = properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1)
                : properties.getBaseUrl();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + "/api/v1/features/daily/calculate"))
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new IllegalStateException("Python 特征计算不可用: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Python 特征计算被中断", ex);
        }
        JsonNode body = read(response.body());
        if (response.statusCode() != 200) {
            String error = body.path("error").asText(response.body());
            throw new IllegalStateException("Python 特征计算失败: " + error);
        }
        List<FeatureRow> rows = new ArrayList<>();
        for (JsonNode node : body.path("rows")) {
            rows.add(new FeatureRow(
                    text(node, "symbol"),
                    LocalDate.parse(text(node, "tradeDate")),
                    decimal(node, "ema20"),
                    decimal(node, "ema50"),
                    decimal(node, "ema200"),
                    decimal(node, "return7d"),
                    decimal(node, "return14d"),
                    decimal(node, "return30d"),
                    decimal(node, "return90d"),
                    decimal(node, "atr14"),
                    decimal(node, "atrPct"),
                    decimal(node, "adx14"),
                    decimal(node, "rsi14"),
                    decimal(node, "realizedVolatility20d"),
                    decimal(node, "volumeRatio20d"),
                    decimal(node, "spotDelta"),
                    decimal(node, "spotDeltaRatio"),
                    decimal(node, "oiChange1d"),
                    decimal(node, "oiChange7d"),
                    decimal(node, "fundingRate"),
                    decimal(node, "fundingZscore"),
                    text(node, "featureVersion"),
                    text(node, "dataQualityStatus")));
        }
        return new FeatureBatch(text(body, "featureVersion"), List.copyOf(rows));
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
}
