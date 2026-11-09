package com.cti.admin.job;

import com.cti.admin.report.DailyReportService;
import com.cti.admin.report.StoredReport;
import com.cti.backtest.BacktestService;
import com.cti.backtest.RunSummary;
import com.cti.common.job.JobTriggerResult;
import com.cti.common.job.ManualJob;
import com.cti.data.intelligence.IntelligenceSyncService;
import com.cti.data.intelligence.NewsSyncService;
import com.cti.data.intelligence.OnchainSyncService;
import com.cti.strategy.intelligence.IntelligenceWorkflowService;
import com.cti.data.sync.MarketSyncService;
import com.cti.feature.DailyFeatureService;
import com.cti.portfolio.PortfolioService;
import com.cti.signal.SignalService;
import com.cti.strategy.ScoreService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

abstract class TrackedManualJob implements ManualJob {

    private static final Logger log = LoggerFactory.getLogger(TrackedManualJob.class);

    private final JobRunRepository jobRunRepository;
    private final StringRedisTemplate redis;

    protected TrackedManualJob(JobRunRepository jobRunRepository, StringRedisTemplate redis) {
        this.jobRunRepository = jobRunRepository;
        this.redis = redis;
    }

    @Override
    public final JobTriggerResult run() {
        String key = "cti:lock:" + name();
        Boolean locked = redis.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(20));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (!Boolean.TRUE.equals(locked)) {
            return new JobTriggerResult(name(), "FAILED", today, 0, "任务正在执行");
        }
        Instant started = Instant.now();
        try {
            String message = clip(doRun());
            int attempt = jobRunRepository.insert(name(), today, "SUCCESS", message, started, Instant.now());
            return new JobTriggerResult(name(), "SUCCESS", today, attempt, message);
        } catch (RuntimeException ex) {
            log.error("job {} failed", name(), ex);
            String message = clip(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            int attempt = 0;
            try {
                attempt = jobRunRepository.insert(name(), today, "FAILED", message, started, Instant.now());
            } catch (RuntimeException recordEx) {
                log.error("failed to record job {}", name(), recordEx);
            }
            return new JobTriggerResult(name(), "FAILED", today, attempt, message);
        } finally {
            redis.delete(key);
        }
    }

    protected abstract String doRun();

    protected final void finish(JobTriggerResult result) {
        XxlJobHelper.log(result.message());
        if ("FAILED".equals(result.status())) {
            XxlJobHelper.handleFail(result.message());
        }
    }

    private static String clip(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}

@Component
class SyncExchangeInfoJob extends TrackedManualJob {

    private final MarketSyncService marketSyncService;

    SyncExchangeInfoJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, MarketSyncService marketSyncService) {
        super(jobRunRepository, redis);
        this.marketSyncService = marketSyncService;
    }

    @Override
    public String name() {
        return JobCatalog.SYNC_EXCHANGE_INFO;
    }

    @Override
    protected String doRun() {
        return marketSyncService.syncExchangeInfo();
    }

    @XxlJob(JobCatalog.SYNC_EXCHANGE_INFO)
    public void job() {
        finish(run());
    }
}

@Component
class SyncDailyKlineJob extends TrackedManualJob {

    private final MarketSyncService marketSyncService;

    SyncDailyKlineJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, MarketSyncService marketSyncService) {
        super(jobRunRepository, redis);
        this.marketSyncService = marketSyncService;
    }

    @Override
    public String name() {
        return JobCatalog.SYNC_KLINE_DAILY;
    }

    @Override
    protected String doRun() {
        return marketSyncService.syncKlines("1d");
    }

    @XxlJob(JobCatalog.SYNC_KLINE_DAILY)
    public void job() {
        finish(run());
    }
}

@Component
class SyncFourHourKlineJob extends TrackedManualJob {

    private final MarketSyncService marketSyncService;

    SyncFourHourKlineJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, MarketSyncService marketSyncService) {
        super(jobRunRepository, redis);
        this.marketSyncService = marketSyncService;
    }

    @Override
    public String name() {
        return JobCatalog.SYNC_KLINE_4H;
    }

    @Override
    protected String doRun() {
        return marketSyncService.syncKlines("4h");
    }

    @XxlJob(JobCatalog.SYNC_KLINE_4H)
    public void job() {
        finish(run());
    }
}

@Component
class DataQualityJob extends TrackedManualJob {

    private final MarketSyncService marketSyncService;

    DataQualityJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, MarketSyncService marketSyncService) {
        super(jobRunRepository, redis);
        this.marketSyncService = marketSyncService;
    }

    @Override
    public String name() {
        return JobCatalog.DATA_QUALITY;
    }

    @Override
    protected String doRun() {
        return marketSyncService.checkQuality();
    }

    @XxlJob(JobCatalog.DATA_QUALITY)
    public void job() {
        finish(run());
    }
}

@Component
class BuildDailyFeatureJob extends TrackedManualJob {

    private final DailyFeatureService dailyFeatureService;

    BuildDailyFeatureJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, DailyFeatureService dailyFeatureService) {
        super(jobRunRepository, redis);
        this.dailyFeatureService = dailyFeatureService;
    }

    @Override
    public String name() {
        return JobCatalog.BUILD_DAILY_FEATURE;
    }

    @Override
    protected String doRun() {
        return dailyFeatureService.build(null);
    }

    @XxlJob(JobCatalog.BUILD_DAILY_FEATURE)
    public void job() {
        finish(run());
    }
}

@Component
class BuildMarketRegimeJob extends TrackedManualJob {

    private final ScoreService scoreService;

    BuildMarketRegimeJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, ScoreService scoreService) {
        super(jobRunRepository, redis);
        this.scoreService = scoreService;
    }

    @Override
    public String name() {
        return JobCatalog.BUILD_MARKET_REGIME;
    }

    @Override
    protected String doRun() {
        return scoreService.buildRegimes();
    }

    @XxlJob(JobCatalog.BUILD_MARKET_REGIME)
    public void job() {
        finish(run());
    }
}

@Component
class BuildTrendScoreJob extends TrackedManualJob {

    private final ScoreService scoreService;

    BuildTrendScoreJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, ScoreService scoreService) {
        super(jobRunRepository, redis);
        this.scoreService = scoreService;
    }

    @Override
    public String name() {
        return JobCatalog.BUILD_TREND_SCORE;
    }

    @Override
    protected String doRun() {
        return scoreService.buildScores();
    }

    @XxlJob(JobCatalog.BUILD_TREND_SCORE)
    public void job() {
        finish(run());
    }
}

@Component
class GenerateSignalJob extends TrackedManualJob {

    private final SignalService signalService;

    GenerateSignalJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, SignalService signalService) {
        super(jobRunRepository, redis);
        this.signalService = signalService;
    }

    @Override
    public String name() {
        return JobCatalog.GENERATE_SIGNAL;
    }

    @Override
    protected String doRun() {
        return signalService.generate();
    }

    @XxlJob(JobCatalog.GENERATE_SIGNAL)
    public void job() {
        finish(run());
    }
}

@Component
class PortfolioRiskJob extends TrackedManualJob {

    private final PortfolioService portfolioService;

    PortfolioRiskJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, PortfolioService portfolioService) {
        super(jobRunRepository, redis);
        this.portfolioService = portfolioService;
    }

    @Override
    public String name() {
        return JobCatalog.PORTFOLIO_RISK;
    }

    @Override
    protected String doRun() {
        return portfolioService.refreshRisk();
    }

    @XxlJob(JobCatalog.PORTFOLIO_RISK)
    public void job() {
        finish(run());
    }
}

@Component
class RunBacktestJob extends TrackedManualJob {

    private final BacktestService backtestService;

    RunBacktestJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, BacktestService backtestService) {
        super(jobRunRepository, redis);
        this.backtestService = backtestService;
    }

    @Override
    public String name() {
        return JobCatalog.RUN_BACKTEST;
    }

    @Override
    protected String doRun() {
        RunSummary summary = backtestService.run("CTI_TREND_V1", null, null, "BASE");
        return "runId=" + summary.runId() + " trades=" + summary.totalTrades()
                + " totalReturn=" + summary.totalReturn() + " version=" + summary.strategyVersion();
    }

    @XxlJob(JobCatalog.RUN_BACKTEST)
    public void job() {
        finish(run());
    }
}

@Component
class DailyReportJob extends TrackedManualJob {

    private final DailyReportService reports;

    DailyReportJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, DailyReportService reports) {
        super(jobRunRepository, redis);
        this.reports = reports;
    }

    @Override
    public String name() {
        return JobCatalog.DAILY_REPORT;
    }

    @Override
    protected String doRun() {
        StoredReport report = reports.generate();
        return "tradeDate=" + report.tradeDate() + " sections=" + report.sections().size()
                + " version=" + report.strategyVersion();
    }

    @XxlJob(JobCatalog.DAILY_REPORT)
    public void job() {
        finish(run());
    }
}

@Component
class SyncDerivativesJob extends TrackedManualJob {

    private final IntelligenceSyncService intelligence;

    SyncDerivativesJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, IntelligenceSyncService intelligence) {
        super(jobRunRepository, redis);
        this.intelligence = intelligence;
    }

    @Override
    public String name() {
        return JobCatalog.SYNC_DERIVATIVES;
    }

    @Override
    protected String doRun() {
        return intelligence.syncDerivatives();
    }

    @XxlJob(JobCatalog.SYNC_DERIVATIVES)
    public void job() {
        finish(run());
    }
}

@Component
class SyncMarketOverviewJob extends TrackedManualJob {

    private final IntelligenceSyncService intelligence;

    SyncMarketOverviewJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, IntelligenceSyncService intelligence) {
        super(jobRunRepository, redis);
        this.intelligence = intelligence;
    }

    @Override
    public String name() {
        return JobCatalog.SYNC_MARKET_OVERVIEW;
    }

    @Override
    protected String doRun() {
        return intelligence.syncMarketOverview();
    }

    @XxlJob(JobCatalog.SYNC_MARKET_OVERVIEW)
    public void job() {
        finish(run());
    }
}

@Component
class SyncNewsJob extends TrackedManualJob {

    private final NewsSyncService news;

    SyncNewsJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, NewsSyncService news) {
        super(jobRunRepository, redis);
        this.news = news;
    }

    @Override
    public String name() {
        return JobCatalog.SYNC_NEWS;
    }

    @Override
    protected String doRun() {
        return news.sync();
    }

    @XxlJob(JobCatalog.SYNC_NEWS)
    public void job() {
        finish(run());
    }
}

@Component
class SyncOnchainJob extends TrackedManualJob {

    private final OnchainSyncService onchain;

    SyncOnchainJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, OnchainSyncService onchain) {
        super(jobRunRepository, redis);
        this.onchain = onchain;
    }

    @Override
    public String name() {
        return JobCatalog.SYNC_ONCHAIN;
    }

    @Override
    protected String doRun() {
        return onchain.sync();
    }

    @XxlJob(JobCatalog.SYNC_ONCHAIN)
    public void job() {
        finish(run());
    }
}

@Component
class BuildIntelligenceScoreJob extends TrackedManualJob {

    private final IntelligenceWorkflowService intelligence;

    BuildIntelligenceScoreJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, IntelligenceWorkflowService intelligence) {
        super(jobRunRepository, redis);
        this.intelligence = intelligence;
    }

    @Override
    public String name() {
        return JobCatalog.BUILD_INTELLIGENCE_SCORE;
    }

    @Override
    protected String doRun() {
        return intelligence.score();
    }

    @XxlJob(JobCatalog.BUILD_INTELLIGENCE_SCORE)
    public void job() {
        finish(run());
    }
}

@Component
class BuildIntelligenceAlertJob extends TrackedManualJob {

    private final IntelligenceWorkflowService intelligence;

    BuildIntelligenceAlertJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, IntelligenceWorkflowService intelligence) {
        super(jobRunRepository, redis);
        this.intelligence = intelligence;
    }

    @Override
    public String name() {
        return JobCatalog.BUILD_INTELLIGENCE_ALERT;
    }

    @Override
    protected String doRun() {
        return intelligence.alerts();
    }

    @XxlJob(JobCatalog.BUILD_INTELLIGENCE_ALERT)
    public void job() {
        finish(run());
    }
}

@Component
class IntelligenceReportJob extends TrackedManualJob {

    private final IntelligenceWorkflowService intelligence;

    IntelligenceReportJob(JobRunRepository jobRunRepository, StringRedisTemplate redis, IntelligenceWorkflowService intelligence) {
        super(jobRunRepository, redis);
        this.intelligence = intelligence;
    }

    @Override
    public String name() {
        return JobCatalog.INTELLIGENCE_REPORT;
    }

    @Override
    protected String doRun() {
        return intelligence.report();
    }

    @XxlJob(JobCatalog.INTELLIGENCE_REPORT)
    public void job() {
        finish(run());
    }
}
