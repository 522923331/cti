# Crypto Trend Intelligence

现货、只做多的加密货币趋势研究系统。规格见原文档，缺口和取舍见 `docs/spec-gaps.md`。

当前版本是 V1.3 研究版。发布说明见 `docs/release/v1.3.md`。部署说明见 `docs/release/linux-deploy.md`。Alibaba Cloud Linux 4 容器优化版运行 `sudo bash deploy/up.sh`。浏览器打开 http://127.0.0.1:8081/ 可以看总览、排行、单币、信号、组合、回测、策略、日报和监控，后面的页面还有情报、账户、研究策略、融合、实验和助手。样本内回测仍然不能标成通过。

## 本阶段可以调用的接口

Java，端口 8081。本机 8080 上已经有别的服务，所以第一版改到 8081：

- `GET /api/v1/admin/system-health`：MySQL、Redis、XXL-Job 是否可用
- `GET /api/v1/admin/jobs`：已注册任务和最近 20 条执行记录
- `POST /api/v1/admin/jobs/cti_health_probe_job/trigger`：手动跑探测任务。它会写 `ct_job_run`，并把时间戳写到 Redis 键 `cti:health:probe`
- `POST /api/v1/admin/jobs/cti_sync_kline_daily_job/trigger`：同步交易对并补齐最近 800 天日线
- `POST /api/v1/admin/jobs/cti_sync_kline_4h_job/trigger`：补齐最近 800 天 4 小时 K 线
- `POST /api/v1/admin/jobs/cti_data_quality_job/trigger`：检查缺口和价格异常
- `GET /api/v1/assets`、`GET /api/v1/assets/BTC`、`GET /api/v1/assets/BTCUSDT/klines?interval=1d&limit=5`
- `GET /api/v1/admin/data-quality`
- `POST /api/v1/admin/jobs/cti_build_daily_feature_job/trigger`：重算全部已收盘日线特征。同一版本再次运行会更新原行，不会多出一行
- `GET /api/v1/assets/BTC/features?limit=5`：最近的日线特征，价格类数字是字符串，缺失是 null
- `POST /api/v1/admin/jobs/cti_build_market_regime_job/trigger`：重算市场状态
- `POST /api/v1/admin/jobs/cti_build_trend_score_job/trigger`：重算七个币的评分
- `GET /api/v1/market/overview`：最新市场状态和当天排行榜
- `GET /api/v1/market/regime?limit=5`、`GET /api/v1/market/breadth?limit=5`
- `GET /api/v1/trend/ranking`、`GET /api/v1/trend/BTC`、`GET /api/v1/trend/BTC/history?limit=5`
- `POST /api/v1/admin/jobs/cti_generate_signal_job/trigger`：用已收盘数据生成信号，并重写研究账户
- `POST /api/v1/admin/jobs/cti_portfolio_risk_job/trigger`：按当前持仓重算相关性和回撤
- `GET /api/v1/signals/latest`、`GET /api/v1/signals?symbol=BTC&limit=5`、`GET /api/v1/signals/{signalId}`
- `GET /api/v1/portfolio`、`GET /api/v1/portfolio/positions`、`GET /api/v1/portfolio/risk`、`GET /api/v1/portfolio/equity?limit=5`
- `POST /api/v1/admin/jobs/cti_run_backtest_job/trigger`：用全部已收盘历史回测 `CTI_TREND_V1` 的 BASE 成本
- `POST /api/v1/backtest/run`：指定策略、日期和成本情景。策略名包括 `CTI_TREND_V1`、`TREND_PULLBACK_V1`、`TREND_BREAKOUT_V1`、`MARKET_FILTER_TREND`、`BASELINE_BTC_HOLD`、`BASELINE_BTC_EMA50_200`、`BASELINE_BTC_EMA20_50`、`BASELINE_TSMOM_14`
- `POST /api/v1/backtest/compare`、`POST /api/v1/backtest/walk-forward`、`POST /api/v1/backtest/sensitivity`
- `GET /api/v1/backtest`、`GET /api/v1/backtest/{runId}`、`GET /api/v1/backtest/{runId}/trades`、`GET /api/v1/backtest/{runId}/equity?limit=5`
- 页面：`/`、`/ranking`、`/assets/BTC`、`/signals`、`/portfolio`、`/backtests`、`/strategies`、`/reports`、`/monitor`
- `POST /api/v1/admin/jobs/cti_daily_report_job/trigger`：为最近一个已收盘评分日生成或更新日报

重复触发日线任务不会增加行数。正在形成的那根 K 线 `closed` 为 false，收盘后更新同一行。

Python，端口 8090：

- `GET /health`
- `POST /api/v1/features/daily/calculate`：Java 调用它计算特征。请求体是 `symbols`，可以带 `tradeDate`

没有 XXL-Job Admin 时，健康检查里的 `xxlJob` 会是 `DEGRADED`，整个状态也是 `DEGRADED`。MySQL 或 Redis 失败时状态是 `DOWN`，HTTP 503。

## 本地启动

本机如果没有 Docker，用下面的方式。有 Docker 时在仓库根目录执行 `docker compose up -d`，MySQL 用户 `cti` / `cti`，XXL-Job Admin 是 `http://127.0.0.1:8088/xxl-job-admin`，账号 `admin` / `123456`。

```bash
redis-server --daemonize yes --port 6379

# MySQL 8 建库后：
# CREATE DATABASE cti CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
# CREATE USER 'cti'@'localhost' IDENTIFIED BY 'cti';
# GRANT ALL ON cti.* TO 'cti'@'localhost';

cd cti-server
export JAVA_HOME="/Users/k02/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home"
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/maven/bin:$PATH"
mvn -pl cti-api -am package -DskipTests
java -jar cti-api/target/cti-api-1.0.0-SNAPSHOT.jar

cd cti-quant
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8090
```

## 验收

```bash
curl -sS http://127.0.0.1:8081/api/v1/admin/system-health
curl -sS -X POST http://127.0.0.1:8081/api/v1/admin/jobs/cti_health_probe_job/trigger
curl -sS http://127.0.0.1:8081/api/v1/admin/jobs
redis-cli GET cti:health:probe
curl -sS http://127.0.0.1:8090/health
```

探测任务再调用一次，`attempt` 会变成 2，不会改掉上一条记录。
