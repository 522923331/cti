package com.cti.api.web;

import com.cti.admin.health.SystemHealthService;
import com.cti.admin.job.JobCatalog;
import com.cti.admin.job.JobRunRepository;
import com.cti.admin.report.DailyReportService;
import com.cti.admin.report.StoredReport;
import com.cti.backtest.BacktestService;
import com.cti.backtest.RunSummary;
import com.cti.backtest.query.BacktestQueryService;
import com.cti.backtest.query.EquityView;
import com.cti.backtest.query.RunView;
import com.cti.data.query.KlinePoint;
import com.cti.data.query.MarketQueryService;
import com.cti.feature.query.FeatureQueryService;
import com.cti.feature.query.FeatureView;
import com.cti.portfolio.query.PortfolioQueryService;
import com.cti.signal.query.SignalQueryService;
import com.cti.signal.query.SignalView;
import com.cti.strategy.query.ScoreQueryService;
import com.cti.strategy.query.ScoreView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ResearchController {

    static final List<String> STRATEGIES = List.of(
            "CTI_TREND_V1", "TREND_PULLBACK_V1", "TREND_BREAKOUT_V1", "MARKET_FILTER_TREND",
            "BASELINE_BTC_HOLD", "BASELINE_BTC_EMA50_200", "BASELINE_BTC_EMA20_50", "BASELINE_TSMOM_14");

    private final ScoreQueryService scores;
    private final FeatureQueryService features;
    private final SignalQueryService signals;
    private final PortfolioQueryService portfolio;
    private final BacktestQueryService backtests;
    private final BacktestService backtestService;
    private final MarketQueryService market;
    private final SystemHealthService health;
    private final JobRunRepository jobs;
    private final DailyReportService reports;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ResearchController(ScoreQueryService scores, FeatureQueryService features, SignalQueryService signals,
                              PortfolioQueryService portfolio, BacktestQueryService backtests, BacktestService backtestService,
                              MarketQueryService market, SystemHealthService health, JobRunRepository jobs,
                              DailyReportService reports, JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.scores = scores;
        this.features = features;
        this.signals = signals;
        this.portfolio = portfolio;
        this.backtests = backtests;
        this.backtestService = backtestService;
        this.market = market;
        this.health = health;
        this.jobs = jobs;
        this.reports = reports;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/")
    public String overview(Model model) {
        var overview = scores.overview();
        Map<String, SignalView> latest = latestBySymbol();
        List<RankLine> rows = rows(overview.ranking(), latest);
        model.addAttribute("regime", overview.regime());
        model.addAttribute("rows", rows);
        model.addAttribute("account", portfolio.account().orElse(null));
        model.addAttribute("btc", find(rows, "BTCUSDT"));
        model.addAttribute("eth", find(rows, "ETHUSDT"));
        return "overview";
    }

    @GetMapping("/ranking")
    public String ranking(@RequestParam(defaultValue = "total") String sort,
                          @RequestParam(required = false) String level,
                          Model model) {
        List<RankLine> rows = rows(scores.ranking(null), latestBySymbol());
        model.addAttribute("levels", rows.stream().map(RankLine::signalLevel).filter(value -> value != null).distinct().toList());
        if (level != null && !level.isBlank()) {
            rows = rows.stream().filter(row -> level.equals(row.signalLevel())).toList();
        }
        model.addAttribute("rows", sort(rows, sort));
        model.addAttribute("sort", sort);
        model.addAttribute("level", level == null ? "" : level);
        return "ranking";
    }

    @GetMapping("/assets/{symbol}")
    public String asset(@PathVariable String symbol, Model model) {
        var asset = market.asset(symbol).orElse(null);
        if (asset == null) {
            model.addAttribute("message", "没有这个资产");
            return "missing";
        }
        ScoreView score = scores.latest(asset.symbol()).orElse(null);
        List<FeatureView> history = features.latest(asset.symbol(), 180).orElse(List.of());
        FeatureView feature = history.isEmpty() ? null : history.get(history.size() - 1);
        List<KlinePoint> daily = closed(market.klines(asset.symbol(), "1d", 180));
        List<KlinePoint> fourHour = closed(market.klines(asset.symbol(), "4h", 180));
        model.addAttribute("asset", asset);
        model.addAttribute("score", score);
        model.addAttribute("feature", feature);
        model.addAttribute("change1d", change(daily));
        model.addAttribute("dailyChart", chart(daily, history));
        model.addAttribute("fourHourChart", chart(fourHour, List.of()));
        model.addAttribute("dailyBars", tail(daily, 12));
        model.addAttribute("fourHourBars", tail(fourHour, 8));
        model.addAttribute("scoreHistory", scores.history(asset.symbol(), 30).orElse(List.of()));
        model.addAttribute("signals", signals.actionable(asset.symbol(), 80).stream()
                .filter(row -> !"WATCH".equals(row.signalType()))
                .toList());
        return "asset";
    }

    @GetMapping("/signals")
    public String signalList(@RequestParam(required = false) String type, Model model) {
        List<SignalView> latest = signals.latest();
        if (type != null && !type.isBlank()) {
            latest = latest.stream().filter(row -> type.equals(row.signalType())).toList();
        }
        List<SignalView> history = signals.actionable(null, 80).stream()
                .filter(row -> row.signalType().equals("BUY") || row.signalType().equals("SELL") || row.signalType().equals("REDUCE"))
                .toList();
        model.addAttribute("latest", latest);
        model.addAttribute("history", history);
        model.addAttribute("type", type == null ? "" : type);
        return "signals";
    }

    @GetMapping("/signals/{signalId}")
    public String signal(@PathVariable String signalId, Model model) {
        SignalView signal = signals.find(signalId).orElse(null);
        if (signal == null) {
            model.addAttribute("message", "没有这条信号");
            return "missing";
        }
        model.addAttribute("signal", signal);
        return "signal";
    }

    @GetMapping("/portfolio")
    public String portfolio(Model model) {
        model.addAttribute("account", portfolio.account().orElse(null));
        model.addAttribute("positions", portfolio.positions().orElse(List.of()));
        model.addAttribute("risk", portfolio.risk().orElse(null));
        model.addAttribute("equity", portfolio.equity(15).orElse(List.of()));
        return "portfolio";
    }

    @GetMapping("/backtests")
    public String backtestList(@RequestParam(required = false) String error, Model model) {
        model.addAttribute("runs", backtests.recent(30));
        model.addAttribute("strategies", STRATEGIES);
        model.addAttribute("assets", market.assets());
        model.addAttribute("error", error);
        return "backtests";
    }

    @PostMapping("/backtests")
    public String createBacktest(@RequestParam String strategy,
                                 @RequestParam(required = false) String startDate,
                                 @RequestParam(required = false) String endDate,
                                 @RequestParam(defaultValue = "BASE") String costScenario,
                                 RedirectAttributes redirect) {
        try {
            RunSummary run = backtestService.run(strategy, date(startDate), date(endDate), costScenario);
            return "redirect:/backtests/" + run.runId();
        } catch (RuntimeException ex) {
            redirect.addAttribute("error", ex.getMessage());
            return "redirect:/backtests";
        }
    }

    @PostMapping("/backtests/compare")
    public String compare(@RequestParam List<String> strategies,
                          @RequestParam(required = false) String startDate,
                          @RequestParam(required = false) String endDate,
                          @RequestParam(defaultValue = "BASE") String costScenario,
                          RedirectAttributes redirect) {
        try {
            List<RunSummary> runs = backtestService.compare(strategies, date(startDate), date(endDate), costScenario);
            redirect.addAttribute("created", String.join(",", runs.stream().map(RunSummary::runId).toList()));
            return "redirect:/backtests";
        } catch (RuntimeException ex) {
            redirect.addAttribute("error", ex.getMessage());
            return "redirect:/backtests";
        }
    }

    @PostMapping("/backtests/walk-forward")
    public String walk(@RequestParam(defaultValue = "BASE") String costScenario, RedirectAttributes redirect) {
        try {
            RunSummary run = backtestService.walkForward(null, null, costScenario);
            return "redirect:/backtests/" + run.runId();
        } catch (RuntimeException ex) {
            redirect.addAttribute("error", ex.getMessage());
            return "redirect:/backtests";
        }
    }

    @GetMapping("/backtests/{runId}")
    public String backtest(@PathVariable String runId, Model model) {
        RunView run = backtests.find(runId).orElse(null);
        if (run == null) {
            model.addAttribute("message", "没有这次回测");
            return "missing";
        }
        List<EquityView> equity = new ArrayList<>(backtests.equity(runId, 5000));
        java.util.Collections.reverse(equity);
        List<BigDecimal> curve = equity.stream().map(row -> new BigDecimal(row.equity())).toList();
        model.addAttribute("run", run);
        model.addAttribute("report", read(run.reportJson()));
        model.addAttribute("chart", PriceChart.svg(curve, List.of(), List.of(), List.of()));
        model.addAttribute("equity", tail(equity, 12));
        model.addAttribute("trades", backtests.trades(runId));
        return "backtest";
    }

    @GetMapping("/strategies")
    public String strategies(Model model) {
        model.addAttribute("configs", jdbc.queryForList("""
                SELECT strategy_name, strategy_version, enabled, description, config_json
                FROM ct_strategy_config
                ORDER BY strategy_name, strategy_version
                """));
        return "strategies";
    }

    @GetMapping("/reports")
    public String reportList(Model model) {
        model.addAttribute("reports", reports.recent());
        return "reports";
    }

    @PostMapping("/reports")
    public String generateReport(RedirectAttributes redirect) {
        try {
            StoredReport report = reports.generate();
            return "redirect:/reports/" + report.tradeDate();
        } catch (RuntimeException ex) {
            redirect.addAttribute("error", ex.getMessage());
            return "redirect:/reports";
        }
    }

    @GetMapping("/reports/{tradeDate}")
    public String report(@PathVariable String tradeDate, Model model) {
        StoredReport report = reports.find(LocalDate.parse(tradeDate));
        if (report == null) {
            model.addAttribute("message", "还没有这一天的日报");
            return "missing";
        }
        model.addAttribute("report", report);
        return "report";
    }

    @GetMapping("/monitor")
    public String monitor(Model model) {
        model.addAttribute("health", health.check());
        model.addAttribute("quality", market.quality());
        model.addAttribute("jobs", jobs.recent(20));
        model.addAttribute("catalog", JobCatalog.registered());
        return "monitor";
    }

    private Map<String, SignalView> latestBySymbol() {
        Map<String, SignalView> indexed = new LinkedHashMap<>();
        for (SignalView signal : signals.latest()) {
            indexed.put(signal.symbol(), signal);
        }
        return indexed;
    }

    private List<RankLine> rows(List<ScoreView> scoreRows, Map<String, SignalView> latest) {
        List<RankLine> rows = new ArrayList<>();
        for (ScoreView score : scoreRows) {
            var asset = market.asset(score.symbol()).orElse(null);
            List<FeatureView> history = features.latest(score.symbol(), 1).orElse(List.of());
            FeatureView feature = history.isEmpty() ? null : history.get(history.size() - 1);
            List<KlinePoint> daily = closed(market.klines(score.symbol(), "1d", 3));
            KlinePoint last = daily.isEmpty() ? null : daily.get(daily.size() - 1);
            SignalView signal = latest.get(score.symbol());
            rows.add(new RankLine(
                    score.symbol(),
                    asset == null ? score.symbol() : asset.assetName(),
                    last == null ? null : last.close(),
                    change(daily),
                    feature == null ? null : feature.return7d(),
                    feature == null ? null : feature.return14d(),
                    feature == null ? null : feature.return30d(),
                    feature == null ? null : feature.ema20(),
                    feature == null ? null : feature.ema50(),
                    feature == null ? null : feature.ema200(),
                    score.trendScore(),
                    score.flowScore(),
                    score.derivativesScore(),
                    score.totalScore(),
                    score.signalLevel(),
                    signal == null ? null : signal.signalType(),
                    feature == null ? null : feature.dataQualityStatus()));
        }
        return rows;
    }

    private static List<RankLine> sort(List<RankLine> rows, String sort) {
        Comparator<RankLine> comparator = switch (sort == null ? "total" : sort) {
            case "price" -> Comparator.comparing(row -> decimal(row.price()), nullsLast());
            case "change" -> Comparator.comparing(row -> decimal(row.change1d()), nullsLast());
            case "return7" -> Comparator.comparing(row -> decimal(row.return7d()), nullsLast());
            case "return14" -> Comparator.comparing(row -> decimal(row.return14d()), nullsLast());
            case "return30" -> Comparator.comparing(row -> decimal(row.return30d()), nullsLast());
            case "trend" -> Comparator.comparing(row -> decimal(row.trendScore()), nullsLast());
            case "flow" -> Comparator.comparing(row -> decimal(row.flowScore()), nullsLast());
            case "symbol" -> Comparator.comparing(RankLine::symbol);
            default -> Comparator.comparing(row -> decimal(row.totalScore()), nullsLast());
        };
        List<RankLine> sorted = new ArrayList<>(rows);
        if (!"symbol".equals(sort)) {
            comparator = comparator.reversed();
        }
        sorted.sort(comparator);
        return sorted;
    }

    private static <T extends Comparable<T>> Comparator<T> nullsLast() {
        return Comparator.nullsLast(Comparator.naturalOrder());
    }

    private String chart(List<KlinePoint> bars, List<FeatureView> history) {
        Map<String, FeatureView> byDate = new LinkedHashMap<>();
        for (FeatureView row : history) {
            byDate.put(row.tradeDate(), row);
        }
        List<BigDecimal> close = new ArrayList<>();
        List<BigDecimal> ema20 = new ArrayList<>();
        List<BigDecimal> ema50 = new ArrayList<>();
        List<BigDecimal> ema200 = new ArrayList<>();
        for (KlinePoint bar : bars) {
            String day = Instant.ofEpochMilli(bar.openTime()).atZone(ZoneOffset.UTC).toLocalDate().toString();
            close.add(new BigDecimal(bar.close()));
            FeatureView feature = byDate.get(day);
            ema20.add(feature == null ? null : decimal(feature.ema20()));
            ema50.add(feature == null ? null : decimal(feature.ema50()));
            ema200.add(feature == null ? null : decimal(feature.ema200()));
        }
        return PriceChart.svg(close, ema20, ema50, ema200);
    }

    private static List<KlinePoint> closed(List<KlinePoint> bars) {
        return bars.stream().filter(KlinePoint::closed).toList();
    }

    private static String change(List<KlinePoint> closed) {
        if (closed.size() < 2) {
            return null;
        }
        BigDecimal previous = new BigDecimal(closed.get(closed.size() - 2).close());
        BigDecimal last = new BigDecimal(closed.get(closed.size() - 1).close());
        if (previous.signum() == 0) {
            return null;
        }
        return last.divide(previous, 8, RoundingMode.HALF_UP).subtract(BigDecimal.ONE).toPlainString();
    }

    private static <T> List<T> tail(List<T> rows, int count) {
        if (rows.size() <= count) {
            return rows;
        }
        return rows.subList(rows.size() - count, rows.size());
    }

    private static RankLine find(List<RankLine> rows, String symbol) {
        return rows.stream().filter(row -> symbol.equals(row.symbol())).findFirst().orElse(null);
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "回测报告无法读取");
        }
    }
}
