package com.cti.strategy.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ResearchAssistantService {

    static final String INSERT_SESSION = """
            INSERT INTO ct_ai_research_session (
                session_id, question, intent_name, explanation, answer_json
            ) VALUES (?, ?, ?, ?, ?)
            """;

    static final String INSERT_EVIDENCE = """
            INSERT INTO ct_ai_research_evidence (
                evidence_id, session_id, source_table, data_range, metric_version, strategy_version,
                fact_json, missing_json, queried_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    static final String SELECT_ENSEMBLE = """
            SELECT symbol, trade_date, direction_regime, volatility_regime, ensemble_score, confidence, signal_name, model_version
            FROM ct_ensemble_result WHERE symbol = ? ORDER BY id DESC LIMIT 1
            """;

    static final String SELECT_INTELLIGENCE = """
            SELECT symbol, trade_date, trend_score, risk_score, sentiment_score, data_confidence, signal_type, model_version
            FROM ct_intelligence_score WHERE symbol = ? ORDER BY trade_date DESC LIMIT 1
            """;

    static final String SELECT_PARAMETER = """
            SELECT strategy_code, strategy_version, parameter_json
            FROM ct_strategy_parameter WHERE strategy_code = ? AND strategy_version = ? ORDER BY id LIMIT 1
            """;

    static final String SELECT_EXPERIMENT = """
            SELECT r.grade, r.passed, r.selected_json
            FROM ct_optimization_run r
            ORDER BY r.id DESC LIMIT 1
            """;

    static final String SELECT_HEALTH = """
            SELECT status, action, parameter_change
            FROM ct_model_health_snapshot WHERE strategy_code = ? ORDER BY id DESC LIMIT 1
            """;

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_]{1,32}$");
    private static final Pattern VERSION = Pattern.compile("^[A-Za-z0-9._-]{1,32}$");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ResearchStrategyClient client;
    private final ObjectMapper mapper;

    public ResearchAssistantService(JdbcTemplate jdbc, PlatformTransactionManager manager,
                                    ResearchStrategyClient client, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.client = client;
        this.mapper = mapper;
    }

    public JsonNode ask(JsonNode payload) {
        String question = payload.path("question").asText("").trim();
        if (question.isEmpty() || question.length() > 500) {
            throw new IllegalArgumentException("问题不能为空");
        }
        ObjectNode planRequest = mapper.createObjectNode();
        planRequest.put("question", question);
        JsonNode plan = client.askAssistant(planRequest);
        String intent = plan.path("intent").asText();
        JsonNode answer = plan;
        ObjectNode facts = mapper.createObjectNode();
        if (!"REJECTED".equals(intent)) {
            facts = load(intent, payload);
            ObjectNode compose = mapper.createObjectNode();
            compose.put("question", question);
            compose.set("facts", facts);
            compose.set("fields", plan.path("fields"));
            answer = client.askAssistant(compose);
        }
        store(question, answer, facts, payload);
        return answer;
    }

    public List<Map<String, Object>> latest() {
        return jdbc.queryForList("""
                SELECT s.question, s.intent_name, s.explanation, e.source_table, e.missing_json
                FROM ct_ai_research_session s
                JOIN ct_ai_research_evidence e ON e.session_id = s.session_id
                ORDER BY s.id DESC
                LIMIT 20
                """);
    }

    private ObjectNode load(String intent, JsonNode payload) {
        String symbol = code(payload.path("symbol").asText("BTCUSDT"));
        String strategy = code(payload.path("strategyCode").asText("EMA_TREND"));
        String version = version(payload.path("strategyVersion").asText("1.0.0"));
        List<Map<String, Object>> rows = switch (intent) {
            case "ensemble" -> jdbc.queryForList(SELECT_ENSEMBLE, symbol);
            case "intelligence" -> jdbc.queryForList(SELECT_INTELLIGENCE, symbol);
            case "parameter" -> jdbc.queryForList(SELECT_PARAMETER, strategy, version);
            case "experiment" -> jdbc.queryForList(SELECT_EXPERIMENT);
            case "health" -> jdbc.queryForList(SELECT_HEALTH, strategy);
            default -> List.of();
        };
        if (rows.isEmpty()) {
            return mapper.createObjectNode();
        }
        return facts(intent, rows.get(0));
    }

    private ObjectNode facts(String intent, Map<String, Object> row) {
        ObjectNode node = mapper.createObjectNode();
        switch (intent) {
            case "ensemble" -> {
                put(node, "symbol", row.get("symbol"));
                put(node, "tradeDate", row.get("trade_date"));
                put(node, "direction", row.get("direction_regime"));
                put(node, "volatility", row.get("volatility_regime"));
                put(node, "score", row.get("ensemble_score"));
                put(node, "confidence", row.get("confidence"));
                put(node, "signal", row.get("signal_name"));
                put(node, "modelVersion", row.get("model_version"));
            }
            case "intelligence" -> {
                put(node, "symbol", row.get("symbol"));
                put(node, "tradeDate", row.get("trade_date"));
                put(node, "trendScore", row.get("trend_score"));
                put(node, "riskScore", row.get("risk_score"));
                put(node, "sentimentScore", row.get("sentiment_score"));
                put(node, "confidence", row.get("data_confidence"));
                put(node, "signal", row.get("signal_type"));
                put(node, "modelVersion", row.get("model_version"));
            }
            case "parameter" -> {
                put(node, "strategyCode", row.get("strategy_code"));
                put(node, "strategyVersion", row.get("strategy_version"));
                put(node, "parameter", row.get("parameter_json"));
            }
            case "experiment" -> {
                put(node, "grade", row.get("grade"));
                put(node, "passed", row.get("passed"));
                put(node, "selected", row.get("selected_json"));
            }
            case "health" -> {
                put(node, "status", row.get("status"));
                put(node, "action", row.get("action"));
                put(node, "parameterChange", row.get("parameter_change"));
            }
            default -> {
            }
        }
        return node;
    }

    private void store(String question, JsonNode answer, ObjectNode facts, JsonNode payload) {
        String intent = answer.path("intent").asText();
        String sessionId = sha(question + "|" + intent + "|" + facts);
        transactions.executeWithoutResult(status -> {
            Integer existing = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ct_ai_research_session WHERE session_id = ?", Integer.class, sessionId);
            if (existing != null && existing > 0) {
                return;
            }
            jdbc.update(INSERT_SESSION, sessionId, question, intent, answer.path("explanation").asText(), answer.toString());
            String source = answer.path("sourceTable").isNull() || answer.path("sourceTable").asText().isBlank()
                    ? "NONE" : answer.path("sourceTable").asText();
            jdbc.update(INSERT_EVIDENCE, sha(sessionId + "|" + source), sessionId, source,
                    range(facts, payload),
                    text(facts, "modelVersion"),
                    text(facts, "strategyVersion"),
                    facts.toString(), answer.path("missing").toString(), Timestamp.from(Instant.now()));
        });
    }

    private static void put(ObjectNode node, String key, Object value) {
        if (value == null) {
            node.putNull(key);
            return;
        }
        if (value instanceof BigDecimal decimal) {
            node.put(key, decimal.toPlainString());
            return;
        }
        node.put(key, String.valueOf(value));
    }

    private static String range(ObjectNode facts, JsonNode payload) {
        String symbol = text(facts, "symbol");
        if (symbol != null) {
            return symbol;
        }
        String strategy = text(facts, "strategyCode");
        if (strategy != null) {
            return strategy;
        }
        JsonNode requested = payload.get("symbol");
        if (requested == null || requested.isNull() || requested.asText().isBlank()) {
            return null;
        }
        return requested.asText();
    }

    private static String text(ObjectNode facts, String field) {
        JsonNode node = facts.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private static String code(String value) {
        if (!CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("代码格式不正确");
        }
        return value;
    }

    private static String version(String value) {
        if (!VERSION.matcher(value).matches()) {
            throw new IllegalArgumentException("版本格式不正确");
        }
        return value;
    }

    private static String sha(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
