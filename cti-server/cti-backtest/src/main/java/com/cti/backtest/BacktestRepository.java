package com.cti.backtest;

import java.util.List;
import java.util.Optional;

public interface BacktestRepository {

    void save(SavedRun run);

    List<RunSummary> recent(int limit);

    Optional<RunSummary> find(String runId);

    List<TradeRow> trades(String runId);

    List<EquityRow> equity(String runId, int limit);
}
