package com.cti.strategy.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class IntelligenceReportComposer {

    private final ObjectMapper mapper = new ObjectMapper();

    public ObjectNode compose(LocalDate tradeDate, String modelVersion, String regime, List<ScoreLine> scores) {
        ObjectNode report = mapper.createObjectNode();
        report.put("reportDate", tradeDate.toString());
        report.put("modelVersion", modelVersion);
        report.put("marketRegime", regime);
        ArrayNode conclusions = report.putArray("conclusions");
        conclusions.add(conclusion(tradeDate, modelVersion, "market_regime", regime,
                "ct_market_regime", "regime", tradeDate + " 确认状态来自已保存的市场状态"));
        for (ScoreLine score : scores) {
            conclusions.add(conclusion(tradeDate, modelVersion, "trend_score", text(score.trendScore()),
                    "ct_intelligence_score", "trend_score",
                    score.symbol() + " 趋势分 " + text(score.trendScore()) + "，信号 " + score.signalType()));
            conclusions.add(conclusion(tradeDate, modelVersion, "data_confidence", text(score.confidence()),
                    "ct_intelligence_score", "data_confidence",
                    score.symbol() + " 数据置信度 " + text(score.confidence()) + "，状态 " + score.dataStatus()));
        }
        report.set("scores", mapper.valueToTree(scores.stream().map(ScoreLine::symbol).toList()));
        return report;
    }

    private ObjectNode conclusion(LocalDate tradeDate, String modelVersion, String metric, String value,
                                  String table, String field, String text) {
        ObjectNode node = mapper.createObjectNode();
        node.put("metric", metric);
        node.put("value", value);
        node.put("dataTime", tradeDate.toString());
        node.put("modelVersion", modelVersion);
        node.put("table", table);
        node.put("field", field);
        node.put("text", text);
        return node;
    }

    private static String text(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    public record ScoreLine(String symbol, BigDecimal trendScore, BigDecimal confidence, String signalType, String dataStatus) {
    }
}
