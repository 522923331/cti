package com.cti.strategy.research;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ResearchStrategyServiceTest {

    @Test
    void researchSignalsDoNotWriteTheV1BookOrTheV1Backtests() {
        String sql = ResearchStrategyService.INSERT_SIGNAL;
        assertFalse(sql.contains("ct_backtest_run"));
        assertFalse(sql.contains("ct_portfolio_account"));
        assertFalse(sql.contains("ct_paper_order"));
        assertFalse(sql.contains("ct_trade_signal"));
        assertFalse(EnsembleService.INSERT_RESULT.contains("ct_market_regime"));
        assertFalse(EnsembleService.INSERT_RESULT.contains("ct_backtest_run"));
        assertFalse(EnsembleService.INSERT_RESULT.contains("ct_portfolio_account"));
        assertFalse(ExperimentService.INSERT_HEALTH.contains("UPDATE"));
        assertFalse(ExperimentService.INSERT_HEALTH.contains("ct_strategy_parameter"));
        assertFalse(ExperimentService.INSERT_RESULT.contains("ct_backtest_run"));
        assertFalse(ResearchAssistantService.INSERT_SESSION.contains("UPDATE"));
        assertFalse(ResearchAssistantService.INSERT_EVIDENCE.contains("UPDATE"));
        assertFalse(ResearchAssistantService.INSERT_EVIDENCE.contains("ct_strategy_parameter"));
        assertFalse(ResearchAssistantService.INSERT_EVIDENCE.contains("ct_portfolio_account"));
        assertFalse(ResearchAssistantService.INSERT_EVIDENCE.contains("ct_trade_signal"));
        assertFalse(ResearchAssistantService.SELECT_PARAMETER.contains("UPDATE"));
        assertFalse(ResearchAssistantService.SELECT_ENSEMBLE.contains("INSERT"));
    }
}
