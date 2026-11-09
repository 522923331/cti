package com.cti.strategy.intelligence;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntelligencePolicyTest {

    private final AlertPolicy policy = new AlertPolicy();

    @Test
    void cooldownKeepsOneOpenAlertAndAllowsAResolvedOneToFireAgain() {
        long start = 1_793_836_800_000L;
        AlertPolicy.PriorAlert open = new AlertPolicy.PriorAlert("same-day", "CREATED", start);
        assertTrue(policy.allow(true, start, 1440, List.of(), "new-day"));
        assertFalse(policy.allow(true, start + 3_600_000L, 1440, List.of(open), "later"));
        assertTrue(policy.allow(true, start + 1440L * 60_000L, 1440, List.of(open), "next-day"));
        AlertPolicy.PriorAlert resolved = new AlertPolicy.PriorAlert("old", "RESOLVED", start);
        assertTrue(policy.allow(true, start + 60_000L, 1440, List.of(resolved), "again"));
        assertFalse(policy.allow(false, start, 1440, List.of(), "disabled"));
        assertFalse(policy.allow(true, start, 1440, List.of(open), "same-day"));
    }

    @Test
    void everyConclusionPointsAtAStoredField() {
        var report = new IntelligenceReportComposer().compose(
                LocalDate.parse("2026-11-05"),
                "intelligence-1.1.0",
                "NEUTRAL",
                List.of(new IntelligenceReportComposer.ScoreLine(
                        "BTCUSDT", new BigDecimal("91.1765"), new BigDecimal("0.850000"), "STRONG_BUY", "PARTIAL")));
        assertEquals(3, report.path("conclusions").size());
        report.path("conclusions").forEach(node -> {
            assertFalse(node.path("table").asText().isBlank());
            assertFalse(node.path("field").asText().isBlank());
            assertEquals("intelligence-1.1.0", node.path("modelVersion").asText());
            assertEquals("2026-11-05", node.path("dataTime").asText());
        });
        assertEquals("ct_intelligence_score", report.path("conclusions").get(1).path("table").asText());
        assertEquals("trend_score", report.path("conclusions").get(1).path("field").asText());
    }

    @Test
    void scoreWritesDoNotNameTheV1Tables() {
        assertTrue(JdbcIntelligenceRepository.INSERT_SCORE.contains("ct_intelligence_score"));
        assertTrue(JdbcIntelligenceRepository.INSERT_SIGNAL.contains("ct_intelligence_signal"));
        assertFalse(JdbcIntelligenceRepository.INSERT_SCORE.contains("ct_trend_score"));
        assertFalse(JdbcIntelligenceRepository.INSERT_SIGNAL.contains("ct_trade_signal"));
    }
}
