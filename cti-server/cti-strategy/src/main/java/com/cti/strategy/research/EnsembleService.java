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
public class EnsembleService {

    static final String INSERT_RESULT = """
            INSERT INTO ct_ensemble_result (
                result_id, config_id, symbol, trade_date, ensemble_score, confidence, signal_name,
                direction_regime, volatility_regime, contribution_json, model_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                ensemble_score = VALUES(ensemble_score),
                confidence = VALUES(confidence),
                signal_name = VALUES(signal_name),
                direction_regime = VALUES(direction_regime),
                volatility_regime = VALUES(volatility_regime),
                contribution_json = VALUES(contribution_json)
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ResearchStrategyClient client;

    public EnsembleService(JdbcTemplate jdbc, PlatformTransactionManager manager, ResearchStrategyClient client) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.client = client;
    }

    public JsonNode evaluate(JsonNode payload) {
        JsonNode result = client.evaluateEnsemble(payload);
        String symbol = payload.path("symbol").asText("BTCUSDT");
        String tradeDate = payload.path("tradeDate").asText(null);
        String configId = sha(payload.path("weights").toString() + "|" + payload.path("config").toString());
        String resultId = sha(configId + "|" + symbol + "|" + String.valueOf(tradeDate) + "|" + payload.path("scores").toString());
        transactions.executeWithoutResult(status -> {
            jdbc.update("""
                    INSERT IGNORE INTO ct_ensemble_config (config_id, weight_json, config_json) VALUES (?, ?, ?)
                    """, configId, payload.path("weights").toString(), payload.path("config").toString());
            if (payload.has("observation")) {
                jdbc.update("""
                        INSERT INTO ct_regime_history (
                            history_id, symbol, trade_date, direction_regime, volatility_regime, model_version, inputs_json
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            direction_regime = VALUES(direction_regime),
                            volatility_regime = VALUES(volatility_regime),
                            inputs_json = VALUES(inputs_json)
                        """, sha(symbol + "|" + String.valueOf(tradeDate) + "|ensemble-1.3.0"), symbol, tradeDate,
                        text(result.path("direction")), text(result.path("volatility")),
                        result.path("modelVersion").asText("ensemble-1.3.0"), payload.path("observation").toString());
            }
            jdbc.update(INSERT_RESULT, resultId, configId, symbol, tradeDate,
                    text(result.path("score")),
                    result.path("confidence").asText(), result.path("signal").asText(),
                    text(result.path("direction")), text(result.path("volatility")),
                    result.path("contributions").toString(), result.path("modelVersion").asText());
        });
        return result;
    }

    public List<Map<String, Object>> latest() {
        return jdbc.queryForList("""
                SELECT symbol, trade_date, direction_regime, volatility_regime, ensemble_score, confidence, signal_name, contribution_json
                FROM ct_ensemble_result ORDER BY id DESC LIMIT 20
                """);
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.asText();
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
