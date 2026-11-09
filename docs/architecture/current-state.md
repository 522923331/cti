# CTI 当前工程现状

记录时间：2026-11-06。对照的规格是 `docs/V1.1_V1.3.md`，约束是 `docs/CTI V1.1–V1.3 统一产品设计原则与 AI 开发约束.md`。本文只描述已经存在的 V1.0 工程，不改业务代码。

## 1. 模块

Java 在 `cti/cti-server`，父工程 Spring Boot 3.4.5，JDK 17。模块：

```text
cti-server
├── cti-common
├── cti-market        币安现货行情客户端
├── cti-data          K 线、质量、Parquet
├── cti-feature       日线特征编排
├── cti-strategy      市场状态和评分编排
├── cti-signal        信号编排
├── cti-portfolio     研究账户编排
├── cti-backtest      回测编排和落库
├── cti-admin         健康检查、任务、日报
└── cti-api           HTTP、页面、Flyway
```

这些模块跑在同一个 Spring Boot 进程里，端口 8081。Python 在 `cti/cti-quant`，FastAPI 端口 8090。计算在 `engines/`、`features/`、`indicators/`。规格里的 `cti-python/` 目录不存在，也不把现有目录改名。

MySQL 8.0 库名 `cti`。Redis 在 6379。XXL-Job 执行器在 9999，Admin `127.0.0.1:8088` 当前没有进程。Docker 未安装，`docker-compose.yml` 只是文件。迁移用 Flyway，脚本在 `cti-api/src/main/resources/db/migration`，另有 Java 迁移 `V2__seed_strategy`、`V6__refresh_strategy`。

## 2. 数据流

```text
币安现货 REST
  → ct_kline、Parquet
  → Python 特征
  → ct_feature_daily
  → Python 市场状态和评分
  → ct_market_regime、ct_trend_score
  → Python 信号和研究账户
  → ct_trade_signal、ct_portfolio_*
  → Python 回测
  → ct_backtest_*
  → 日报 ct_daily_report
  → 8081 页面和 /api/v1
```

日线交易日是币安 1 日 K 线的 UTC 日期。K 线收盘时间是开盘时间加 86,400,000 毫秒减 1。币安服务器时间晚于收盘时间，这根 K 线才算收盘。特征、评分、信号和回测只用已收盘 K 线。

## 3. 数据表

已有表：

- `ct_asset`、`ct_exchange_symbol`
- `ct_kline`、`ct_kline_revision`、`ct_data_quality_report`
- `ct_derivatives_snapshot`（表在，行数为 0，没有采集任务）
- `ct_feature_daily`
- `ct_market_regime`、`ct_trend_score`
- `ct_trade_signal`
- `ct_portfolio_account`、`ct_portfolio_position`、`ct_portfolio_equity`
- `ct_strategy_config`
- `ct_backtest_run`、`ct_backtest_result`、`ct_backtest_trade`、`ct_backtest_equity`
- `ct_daily_report`
- `ct_job_run`

当前研究范围是 7 个现货交易对的 1 日和 4 小时 K 线。情绪分和衍生品分在评分里保持 NULL，总分只在市场、趋势、资金流之间重新归一化。

## 4. 接口

JSON 前缀是 `/api/v1`，不是规格中的 `/api/cti/v1`。

- 行情：`/assets`、`/assets/{symbol}`、`/assets/{symbol}/klines`、`/admin/data-quality`
- 特征：`/assets/{symbol}/features`
- 评分：`/market/overview`、`/market/regime`、`/market/breadth`、`/trend/ranking`、`/trend/{symbol}`
- 信号和组合：`/signals`、`/signals/latest`、`/portfolio`、`/portfolio/positions`、`/portfolio/risk`、`/portfolio/equity`
- 回测：`/backtest/run`、`/compare`、`/walk-forward`、`/sensitivity` 及查询
- 管理：`/admin/system-health`、`/admin/jobs/{jobName}/trigger`

页面由 `ResearchController` 渲染：`/`、`/ranking`、`/assets/{symbol}`、`/signals`、`/portfolio`、`/backtests`、`/strategies`、`/reports`、`/monitor`。

## 5. 任务

任务名在 `JobCatalog`，手动触发走 `/api/v1/admin/jobs/{jobName}/trigger`，并用 Redis 锁。

- `cti_health_probe_job`
- `cti_sync_exchange_info_job`
- `cti_sync_kline_daily_job`
- `cti_sync_kline_4h_job`
- `cti_data_quality_job`
- `cti_build_daily_feature_job`
- `cti_build_market_regime_job`
- `cti_build_trend_score_job`
- `cti_generate_signal_job`
- `cti_portfolio_risk_job`
- `cti_run_backtest_job`
- `cti_daily_report_job`

## 6. 已实现和未实现

已实现的是 V1.0：现货采集、日线和 4 小时线、指标、市场状态、趋势评分、只做多信号、研究账户、回测、基准、走步验证、参数敏感性、日报和页面。没有真实下单。

规格中的这些能力还没有：

- BTC Dominance、TOTAL、TOTAL3、ETHBTC 市场快照
- 币安 U 本位持仓量、资金费率、主动买卖量、溢价指数的采集
- 新闻、链上
- 独立的风险分、情绪分和数据置信度
- 预警
- 多账户模拟订单、流水和加权平均成本
- 策略注册中心、策略融合、V1.3 实验记录、模型健康度
- AI 研究助手

## 7. 与规格的差异

差异和采用的兼容方案写在 `docs/spec-gaps.md` 第 14 节，任务顺序写在 `docs/implementation/roadmap.md`。原则是保留 V1.0 的表、接口、评分版本和走步规则，新能力加在旁边。
