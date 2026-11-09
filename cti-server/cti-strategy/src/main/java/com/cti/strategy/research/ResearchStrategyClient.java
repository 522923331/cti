package com.cti.strategy.research;

import com.cti.feature.config.QuantProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class ResearchStrategyClient {

    private final QuantProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public ResearchStrategyClient(QuantProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .build();
    }

    public JsonNode askAssistant(JsonNode payload) {
        return post("/api/v1/research/assistant/query", payload);
    }

    public JsonNode runExperiment(JsonNode payload) {
        return post("/api/v1/strategies/experiments", payload);
    }

    public JsonNode assessHealth(JsonNode payload) {
        return post("/api/v1/strategies/health", payload);
    }

    public JsonNode evaluateEnsemble(JsonNode payload) {
        return post("/api/v1/strategies/ensemble", payload);
    }

    public JsonNode evaluate(JsonNode payload) {
        return post("/api/v1/strategies/research/evaluate", payload);
    }

    private JsonNode post(String path, JsonNode payload) {
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
            throw new IllegalStateException("Python 策略计算不可用: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Python 策略计算被中断", ex);
        }
        try {
            JsonNode body = mapper.readTree(response.body() == null || response.body().isBlank() ? "{}" : response.body());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Python 策略计算失败: " + body.path("error").asText(response.body()));
            }
            return body;
        } catch (IOException ex) {
            throw new IllegalStateException("Python 返回的不是 JSON", ex);
        }
    }
}
