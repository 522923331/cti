package com.cti.backtest.query;

import com.cti.backtest.BacktestRepository;
import com.cti.backtest.EquityRow;
import com.cti.backtest.RunSummary;
import com.cti.backtest.TradeRow;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class BacktestQueryService {

    private final BacktestRepository repository;

    public BacktestQueryService(BacktestRepository repository) {
        this.repository = repository;
    }

    public List<RunView> recent(int limit) {
        return repository.recent(limit).stream().map(BacktestQueryService::view).toList();
    }

    public Optional<RunView> find(String runId) {
        return repository.find(runId).map(BacktestQueryService::view);
    }

    public List<TradeView> trades(String runId) {
        return repository.trades(runId).stream().map(BacktestQueryService::trade).toList();
    }

    public List<EquityView> equity(String runId, int limit) {
        return repository.equity(runId, limit).stream().map(BacktestQueryService::equity).toList();
    }

    private static RunView view(RunSummary row) {
        return new RunView(
                row.runId(), row.strategyName(), row.strategyVersion(),
                row.startDate().toString(), row.endDate().toString(),
                plain(row.initialCapital()), plain(row.commissionRate()), plain(row.slippageRate()),
                row.status(), row.reportJson(),
                plain(row.totalReturn()), plain(row.annualizedReturn()), plain(row.annualizedVolatility()),
                plain(row.sharpeRatio()), plain(row.sortinoRatio()), plain(row.maxDrawdown()),
                plain(row.winRate()), plain(row.profitFactor()), row.totalTrades(),
                plain(row.averageHoldingDays()), plain(row.totalFee()), plain(row.totalSlippage()),
                plain(row.benchmarkReturn()), plain(row.excessReturn()));
    }

    private static TradeView trade(TradeRow row) {
        return new TradeView(
                row.tradeDate().toString(), row.symbol(), row.side(),
                plain(row.quantity()), plain(row.openPrice()), plain(row.fillPrice()),
                plain(row.fee()), plain(row.slippage()), plain(row.pnl()),
                row.openedOn() == null ? null : row.openedOn().toString(), row.holdingDays());
    }

    private static EquityView equity(EquityRow row) {
        return new EquityView(
                row.tradeDate().toString(), plain(row.cash()), plain(row.equity()),
                plain(row.peakEquity()), plain(row.drawdown()), plain(row.exposure()));
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
