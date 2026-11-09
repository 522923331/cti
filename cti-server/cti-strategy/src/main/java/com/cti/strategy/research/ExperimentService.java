package com.cti.strategy.research;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@Service
public class ExperimentService {

    static final String INSERT_RESULT = """
            INSERT INTO ct_optimization_result (
                result_id, run_id, parameter_json, eligible, selected, validation_json, test_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    static final String INSERT_HEALTH = """
            INSERT INTO ct_model_health_snapshot (
                snapshot_id, strategy_code, strategy_version, status, action, parameter_change, metrics_json, rules_json
            ) VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ResearchStrategyClient client;

    public ExperimentService(JdbcTemplate jdbc, PlatformTransactionManager manager, ResearchStrategyClient client) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.client = client;
    }

    public JsonNode experiment(JsonNode payload) {
        JsonNode result = client.runExperiment(payload);
        String runId = sha(payload.toString());
        transactions.executeWithoutResult(status -> {
            Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM ct_optimization_run WHERE run_id = ?", Integer.class, runId);
            if (existing != null && existing > 0) {
                return;
            }
            jdbc.update("""
                    INSERT INTO ct_optimization_run (
                        run_id, grade, passed, selected_json, fold_json, constraint_json, start_date, end_date
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, runId, result.path("grade").asText(), result.path("passed").asBoolean(),
                    result.path("selected").isNull() ? null : result.path("selected").toString(),
                    result.path("folds").toString(), payload.path("constraints").toString(),
                    payload.path("startDate").asText(), payload.path("endDate").asText());
            int index = 0;
            for (JsonNode record : result.path("records")) {
                jdbc.update(INSERT_RESULT, sha(runId + "|" + index), runId, record.path("parameters").toString(),
                        record.path("eligible").asBoolean(), record.path("selected").asBoolean(),
                        record.path("validation").toString(),
                        record.path("test").isNull() ? null : record.path("test").toString());
                index++;
            }
        });
        return result;
    }

    public JsonNode health(JsonNode payload) {
        JsonNode result = client.assessHealth(payload);
        String code = payload.path("strategyCode").asText();
        String version = payload.path("strategyVersion").asText();
        jdbc.update(INSERT_HEALTH, sha(code + "|" + version + "|" + payload.path("snapshot").toString()),
                code, version, result.path("status").asText(), result.path("action").asText(),
                payload.path("snapshot").toString(), payload.path("rules").toString());
        return result;
    }

    public List<Map<String, Object>> latest() {
        return jdbc.queryForList("""
                SELECT r.grade, r.passed, r.selected_json, o.parameter_json, o.eligible, o.selected
                FROM ct_optimization_run r
                JOIN ct_optimization_result o ON o.run_id = r.run_id
                ORDER BY r.id DESC, o.id
                LIMIT 20
                """);
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
