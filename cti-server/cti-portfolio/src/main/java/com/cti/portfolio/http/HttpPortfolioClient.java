package com.cti.portfolio.http;

import com.cti.feature.config.QuantProperties;
import com.cti.portfolio.PortfolioClient;
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

public class HttpPortfolioClient implements PortfolioClient {

    private final QuantProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpPortfolioClient(QuantProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String risk(List<String> symbols, LocalDate tradeDate) {
        JsonNode body = post(symbols, tradeDate);
        JsonNode risk = body.get("risk");
        if (risk == null || risk.isNull()) {
            throw new IllegalStateException("Python 没有返回组合风险");
        }
        return risk.toString();
    }

    private JsonNode post(List<String> symbols, LocalDate tradeDate) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putPOJO("symbols", symbols);
        if (tradeDate != null) {
            payload.put("tradeDate", tradeDate.toString());
        }
        String base = properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1)
                : properties.getBaseUrl();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + "/api/v1/portfolio/risk/calculate"))
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new IllegalStateException("Python 组合风险不可用: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Python 组合风险被中断", ex);
        }
        JsonNode body = read(response.body());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Python 组合风险失败: " + body.path("error").asText(response.body()));
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
