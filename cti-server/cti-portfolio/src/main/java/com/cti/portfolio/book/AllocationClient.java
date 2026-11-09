package com.cti.portfolio.book;

import com.cti.feature.config.QuantProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Component;

@Component
public class AllocationClient {

    private final QuantProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public AllocationClient(QuantProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .build();
    }

    public JsonNode allocate(JsonNode payload) {
        String base = properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1)
                : properties.getBaseUrl();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + "/api/v1/portfolio/allocate"))
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new IllegalStateException("Python 配置计算不可用: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Python 配置计算被中断", ex);
        }
        JsonNode body = read(response.body());
        if (response.statusCode() == 422) {
            throw new IllegalStateException(body.path("error").asText("配置被拒绝"));
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Python 配置计算失败: " + body.path("error").asText(response.body()));
        }
        return body;
    }

    private JsonNode read(String body) {
        try {
            return mapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (IOException ex) {
            throw new IllegalStateException("Python 返回的不是 JSON", ex);
        }
    }
}
