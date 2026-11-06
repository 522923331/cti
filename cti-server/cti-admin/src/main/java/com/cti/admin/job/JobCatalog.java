package com.cti.admin.job;

import java.util.List;

public final class JobCatalog {

    public static final String HEALTH_PROBE = "cti_health_probe_job";
    public static final String SYNC_EXCHANGE_INFO = "cti_sync_exchange_info_job";
    public static final String SYNC_KLINE_DAILY = "cti_sync_kline_daily_job";
    public static final String SYNC_KLINE_4H = "cti_sync_kline_4h_job";
    public static final String DATA_QUALITY = "cti_data_quality_job";
    public static final String BUILD_DAILY_FEATURE = "cti_build_daily_feature_job";
    public static final String BUILD_MARKET_REGIME = "cti_build_market_regime_job";
    public static final String BUILD_TREND_SCORE = "cti_build_trend_score_job";
    public static final String GENERATE_SIGNAL = "cti_generate_signal_job";
    public static final String PORTFOLIO_RISK = "cti_portfolio_risk_job";
    public static final String RUN_BACKTEST = "cti_run_backtest_job";
    public static final String DAILY_REPORT = "cti_daily_report_job";

    private JobCatalog() {
    }

    public static List<JobCatalogEntry> registered() {
        return List.of(
                new JobCatalogEntry(HEALTH_PROBE, "探测 MySQL 与 Redis", "0"),
                new JobCatalogEntry(SYNC_EXCHANGE_INFO, "同步 Binance exchangeInfo，确认交易对状态", "1"),
                new JobCatalogEntry(SYNC_KLINE_DAILY, "补齐并增量更新日线", "1"),
                new JobCatalogEntry(SYNC_KLINE_4H, "补齐并增量更新 4 小时 K 线", "1"),
                new JobCatalogEntry(DATA_QUALITY, "检查 K 线连续性和价格异常", "1"),
                new JobCatalogEntry(BUILD_DAILY_FEATURE, "用已收盘日线计算特征", "2"),
                new JobCatalogEntry(BUILD_MARKET_REGIME, "计算市场广度和确认后的市场状态", "3"),
                new JobCatalogEntry(BUILD_TREND_SCORE, "计算七个币的趋势、资金流和总分", "3"),
                new JobCatalogEntry(GENERATE_SIGNAL, "按已收盘数据生成信号并更新研究账户", "4"),
                new JobCatalogEntry(PORTFOLIO_RISK, "用当前持仓重算相关性和回撤", "4"),
                new JobCatalogEntry(RUN_BACKTEST, "用全部已收盘历史回测趋势策略的 BASE 成本", "5"),
                new JobCatalogEntry(DAILY_REPORT, "用最近一个已收盘交易日生成研究报告", "6"));
    }
}
