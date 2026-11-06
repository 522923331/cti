package com.cti.signal.http;

import com.cti.feature.config.QuantProperties;
import com.cti.portfolio.AccountRow;
import com.cti.portfolio.EquityRow;
import com.cti.portfolio.PositionRow;
import com.cti.signal.SignalBatch;
import com.cti.signal.SignalClient;
import com.cti.signal.SignalRow;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class HttpSignalClient implements SignalClient {

    private final QuantProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpSignalClient(QuantProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public SignalBatch generate(List<String> symbols, LocalDate tradeDate) {
        JsonNode body = post(symbols, tradeDate);
        List<SignalRow> signals = new ArrayList<>();
        for (JsonNode node : body.path("signals")) {
            signals.add(new SignalRow(
                    text(node, "signalId"),
                    text(node, "symbol"),
                    LocalDateTime.parse(text(node, "signalTime")),
                    LocalDate.parse(text(node, "tradeDate")),
                    text(node, "signalType"),
                    text(node, "signalStatus"),
                    decimal(node, "score"),
                    decimal(node, "entryPrice"),
                    decimal(node, "stopPrice"),
                    decimal(node, "suggestedPosition"),
                    decimal(node, "riskAmount"),
                    json(node, "signalReason"),
                    jsonOrNull(node, "invalidationReason"),
                    text(node, "featureVersion"),
                    text(node, "strategyVersion")));
        }
        return new SignalBatch(
                text(body, "strategyVersion"),
                text(body, "featureVersion"),
                List.copyOf(signals),
                account(body.path("account")),
                positions(body.path("positions")),
                equity(body.path("equity")));
    }

    private AccountRow account(JsonNode node) {
        String asOf = text(node, "asOfDate");
        return new AccountRow(
                text(node, "accountCode"),
                text(node, "strategyName"),
                text(node, "strategyVersion"),
                text(node, "quoteAsset"),
                decimal(node, "initialCapital"),
                decimal(node, "cash"),
                decimal(node, "equity"),
                decimal(node, "peakEquity"),
                asOf == null ? null : LocalDate.parse(asOf),
                json(node, "risk"));
    }

    private List<PositionRow> positions(JsonNode rows) {
        List<PositionRow> positions = new ArrayList<>();
        if (rows.isMissingNode()) {
            return List.of();
        }
        for (JsonNode node : rows) {
            positions.add(new PositionRow(
                    text(node, "symbol"),
                    decimal(node, "quantity"),
                    decimal(node, "avgEntryPrice"),
                    decimal(node, "stopPrice"),
                    decimal(node, "highestClose"),
                    LocalDate.parse(text(node, "openedOn")),
                    LocalDate.parse(text(node, "updatedOn")),
                    decimal(node, "marketValue")));
        }
        return List.copyOf(positions);
    }

    private List<EquityRow> equity(JsonNode rows) {
        List<EquityRow> equity = new ArrayList<>();
        if (rows.isMissingNode()) {
            return List.of();
        }
        for (JsonNode node : rows) {
            equity.add(new EquityRow(
                    LocalDate.parse(text(node, "tradeDate")),
                    decimal(node, "cash"),
                    decimal(node, "equity"),
                    decimal(node, "peakEquity"),
                    decimal(node, "drawdown"),
                    decimal(node, "exposure")));
        }
        return List.copyOf(equity);
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
                .uri(URI.create(base + "/api/v1/signals/generate"))
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new IllegalStateException("Python 信号计算不可用: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Python 信号计算被中断", ex);
        }
        JsonNode body = read(response.body());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Python 信号计算失败: " + body.path("error").asText(response.body()));
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

    private static String jsonOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.toString();
    }
}
