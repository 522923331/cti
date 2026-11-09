package com.cti.strategy.intelligence;

import com.cti.feature.config.QuantProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

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

@Component
public class HttpIntelligenceClient {

    private final QuantProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public HttpIntelligenceClient(QuantProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .build();
    }

    public List<JdbcIntelligenceRepository.IntelligenceScore> calculate() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getBaseUrl() + "/api/v1/intelligence/score/calculate"))
                    .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("情报评分 HTTP " + response.statusCode());
            }
            JsonNode rows = mapper.readTree(response.body()).path("rows");
            List<JdbcIntelligenceRepository.IntelligenceScore> scores = new ArrayList<>();
            for (JsonNode node : rows) {
                scores.add(new JdbcIntelligenceRepository.IntelligenceScore(
                        node.path("symbol").asText(),
                        LocalDate.parse(node.path("tradeDate").asText()),
                        node.path("modelVersion").asText(),
                        decimal(node, "trendScore"),
                        decimal(node, "riskScore"),
                        decimal(node, "sentimentScore"),
                        decimal(node, "dataConfidence"),
                        node.path("riskLevel").asText(),
                        node.path("signalType").asText(),
                        node.path("dataStatus").asText(),
                        node.path("reason").isMissingNode() ? null : node.path("reason").toString()));
            }
            return scores;
        } catch (IOException ex) {
            throw new IllegalStateException("情报评分读取失败", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("情报评分被中断", ex);
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return new BigDecimal(value.asText());
    }
}
