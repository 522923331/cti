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
public class ResearchStrategyService {

    static final String INSERT_SIGNAL = """
            INSERT INTO ct_strategy_signal (
                signal_id, strategy_code, strategy_version, symbol, signal_time, signal_name,
                score, target_weight, stop_price, explanation_json, parameter_hash
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                signal_name = VALUES(signal_name),
                score = VALUES(score),
                explanation_json = VALUES(explanation_json)
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ResearchStrategyClient client;

    public ResearchStrategyService(JdbcTemplate jdbc, PlatformTransactionManager manager, ResearchStrategyClient client) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.client = client;
    }

    public JsonNode evaluate(JsonNode payload) {
        JsonNode result = client.evaluate(payload);
        String code = payload.path("strategyCode").asText();
        String version = result.path("signals").path(0).path("strategyVersion").asText(payload.path("strategyVersion").asText("1.0.0"));
        String parameters = result.path("parameters").toString();
        String hash = sha(parameters);
        transactions.executeWithoutResult(status -> {
            jdbc.update("""
                    INSERT IGNORE INTO ct_strategy_registry (strategy_code, strategy_name, status)
                    VALUES (?, ?, 'ACTIVE')
                    """, code, code);
            jdbc.update("""
                    INSERT IGNORE INTO ct_strategy_version (strategy_code, strategy_version) VALUES (?, ?)
                    """, code, version);
            jdbc.update("""
                    INSERT IGNORE INTO ct_strategy_parameter (strategy_code, strategy_version, parameter_hash, parameter_json)
                    VALUES (?, ?, ?, ?)
                    """, code, version, hash, parameters);
            for (JsonNode row : result.path("signals")) {
                String symbol = row.path("symbol").asText();
                String time = row.path("signalTime").asText("NONE");
                jdbc.update(INSERT_SIGNAL, sha(code + "|" + version + "|" + symbol + "|" + time + "|" + hash),
                        code, version, symbol, time, row.path("signal").asText(),
                        row.path("score").isNull() ? null : row.path("score").asText(),
                        row.path("targetWeight").isNull() ? null : row.path("targetWeight").asText(),
                        row.path("stopPrice").isNull() ? null : row.path("stopPrice").asText(),
                        row.path("explanation").toString(), hash);
            }
            if (!result.path("book").isMissingNode() && !result.path("book").isNull()) {
                jdbc.update("""
                        INSERT IGNORE INTO ct_strategy_execution (execution_id, strategy_code, strategy_version, parameter_hash, result_json)
                        VALUES (?, ?, ?, ?, ?)
                        """, sha(code + "|" + version + "|" + hash + "|" + result.path("book").toString()),
                        code, version, hash, result.path("book").toString());
            }
        });
        return result;
    }

    public List<Map<String, Object>> registry() {
        return jdbc.queryForList("""
                SELECT r.strategy_code, r.strategy_name, v.strategy_version, p.parameter_json
                FROM ct_strategy_registry r
                JOIN ct_strategy_version v ON v.strategy_code = r.strategy_code
                JOIN ct_strategy_parameter p ON p.strategy_code = v.strategy_code AND p.strategy_version = v.strategy_version
                ORDER BY r.strategy_code, v.strategy_version, p.id
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
