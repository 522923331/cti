# Phase 1 验证记录

时间：2026-11-06。

## 做了什么

- 从币安 U 本位公开接口采集 7 个交易对最近 30 天的资金费率、4 小时持仓量和主动买卖量。未收盘的 4 小时桶不入库。
- 实时标记价格、指数价格、当期资金费率和当期持仓量单独记为 `SNAPSHOT`。
- 采集 ETHBTC 日线。市场快照只用最近一根已收盘日线。BTC Dominance、TOTAL、TOTAL3 记为 `ABSENT`，值为 NULL。
- Python 增加 `oi_change_4h`、`funding_z20`、`taker_buy_ratio`。这三个函数本阶段只做测试，还没有接进 V1.0 评分。

## 核对

- 第一次衍生品任务写入 5684 行。第二次仍处理 5684 个点。已收盘序列没有新增行。实时快照因为币安返回了新的时间，7 个币各多 4 行。表内唯一键没有重复，合计 5712 行。
- ETHBTC 第二次 `inserted=0`。快照时间仍是 2026-11-05 的开盘时间，收盘价 0.02940，状态 `CLOSED`。
- `ct_asset` 没有 ETHBTC。`ct_trend_score` 和 `ct_trade_signal` 仍各是 5600 行。`/api/v1/market/overview` 仍是 2026-11-05 NEUTRAL。
- 最新 4 小时事件时间是 2026-11-06 04:00 UTC。当时服务器时间已过 08:00 UTC，08:00 开始的那根还没收盘，没有入库。
- Python 43 项测试通过。Java 模块测试通过，其中包括解析、合约接口重试，以及单个币失败时其他币仍落库。

## 查询

- `GET /api/v1/intelligence/market-overview`
- `GET /api/v1/intelligence/derivatives?symbol=BTCUSDT`
- `POST /api/v1/admin/jobs/cti_sync_derivatives_job/trigger`
- `POST /api/v1/admin/jobs/cti_sync_market_overview_job/trigger`

## 未做

新闻、链上、新评分和预警留在后面的阶段。实时快照每次采集都会按新的交易所时间追加，这不是同一根已收盘 K 线的重复入库。
