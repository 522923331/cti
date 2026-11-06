package com.cti.api.web;

import com.cti.backtest.BacktestService;
import com.cti.backtest.RunSummary;
import com.cti.backtest.query.BacktestQueryService;
import com.cti.backtest.query.EquityView;
import com.cti.backtest.query.RunView;
import com.cti.backtest.query.TradeView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v1/backtest")
public class BacktestController {

    private final BacktestService backtests;
    private final BacktestQueryService queries;

    public BacktestController(BacktestService backtests, BacktestQueryService queries) {
        this.backtests = backtests;
        this.queries = queries;
    }

    @PostMapping("/run")
    public RunView run(@RequestBody RunBody body) {
        return view(call(() -> backtests.run(body.strategy(), body.startDate(), body.endDate(), body.costScenario())));
    }

    @PostMapping("/compare")
    public List<RunView> compare(@RequestBody CompareBody body) {
        return call(() -> backtests.compare(body.strategies(), body.startDate(), body.endDate(), body.costScenario())).stream()
                .map(BacktestController::view)
                .toList();
    }

    @PostMapping("/walk-forward")
    public RunView walkForward(@RequestBody WindowBody body) {
        return view(call(() -> backtests.walkForward(body.startDate(), body.endDate(), body.costScenario())));
    }

    @PostMapping("/sensitivity")
    public RunView sensitivity(@RequestBody WindowBody body) {
        return view(call(() -> backtests.sensitivity(body.startDate(), body.endDate(), body.costScenario())));
    }

    @GetMapping
    public List<RunView> recent(@RequestParam(defaultValue = "20") int limit) {
        return queries.recent(bounded(limit));
    }

    @GetMapping("/{runId}")
    public RunView one(@PathVariable String runId) {
        return queries.find(runId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这次回测"));
    }

    @GetMapping("/{runId}/trades")
    public List<TradeView> trades(@PathVariable String runId) {
        require(runId);
        return queries.trades(runId);
    }

    @GetMapping("/{runId}/equity")
    public List<EquityView> equity(@PathVariable String runId, @RequestParam(defaultValue = "800") int limit) {
        require(runId);
        return queries.equity(runId, bounded(limit));
    }

    private void require(String runId) {
        if (queries.find(runId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这次回测");
        }
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

    private static String plain(java.math.BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        }
    }

    private static int bounded(int limit) {
        if (limit < 1 || limit > 5000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit 要在 1 到 5000 之间");
        }
        return limit;
    }

    public record RunBody(String strategy, LocalDate startDate, LocalDate endDate, String costScenario) {
    }

    public record CompareBody(List<String> strategies, LocalDate startDate, LocalDate endDate, String costScenario) {
    }

    public record WindowBody(LocalDate startDate, LocalDate endDate, String costScenario) {
    }
}
