# Phase 2 验证记录

时间：2026-11-06。

## 做了什么

- 从币安公开公告接口采集每个栏目最近 20 条。只保存有文章编号、标题和发布时间的记录。原文地址是 `https://www.binance.com/en/support/announcement/{code}`。
- 同一地址再次采集会更新原记录。标题规范化后的哈希相同、地址不同时不新增第二条，因此不会重复计分。
- 分类只用标题规则，没有调用模型。能对上类别或方向时置信度 0.400000，类别和方向都不明时 0.200000。方向不明记 `UNCERTAIN`。没有关联到现有 7 个资产时记 `MARKET_WIDE`。
- 分类结果不包含买卖动作。带有 `BUY` 的分类不会入库。
- 链上 Provider 已落地，但没有配置供应商。任务结果是未接入，表里没有净流入。流入或流出缺失、单位不同、供应商不同时，Python 的净流入结果保持 NULL。
- 事件影响分的半衰期和等级分值规格没有给出具体数字，这一阶段没有编造，也没有计算。

## 核对

- 第一次公告任务 `inserted=140`。第二次 `inserted=0 updated=140`。事件、地址哈希、标题哈希都是 140，没有重复。
- 情绪分布：POSITIVE 15、NEGATIVE 19、NEUTRAL 4、UNCERTAIN 102。没有任何 `BUY` 标签。
- 抽查原文地址返回 HTTP 202。`COIN-M` 不再被当成 BNB。标题里明确写出 BNB、ETH、SOL、XRP 的公告仍关联到对应资产。
- 链上任务两次都是 `onchain=UNAVAILABLE provider=NONE rows=0`。`ct_onchain_snapshot` 是 0 行。
- `ct_trend_score` 和 `ct_trade_signal` 仍各是 5600 行。`/api/v1/market/overview` 仍是 2026-11-05 NEUTRAL。
- Python 45 项测试通过。Java 模块测试通过。

## 查询

- `GET /api/v1/intelligence/news`
- `GET /api/v1/intelligence/news?asset=ETH`
- `GET /api/v1/intelligence/onchain`
- `POST /api/v1/admin/jobs/cti_sync_news_job/trigger`
- `POST /api/v1/admin/jobs/cti_sync_onchain_job/trigger`
