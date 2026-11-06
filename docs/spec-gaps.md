# Crypto Trend Intelligence 规格补全

原规格在 `Crypto Trend Intelligence.md`。这里只记录原文没写死、但后面每个阶段都必须遵守的决定。原文已经写明的公式不改。

数值全部放在 `cti-server/cti-api/src/main/resources/strategy/cti-trend-v1.json`，启动时写入 `ct_strategy_config`。后面的引擎只读这张表，不在代码里再写一套权重。

## 1. 情绪分冲突

第 9.1 节写 `SentimentScore = 0`。第 22 节写 sentiment 必须保持缺失，并对可用权重重新归一化。

采用第 22 节：

- V1 不计算情绪分，库存 `NULL`，`reason_json` 标记 `sentiment=ABSENT`。
- 不把情绪分当成 0 分加进总分。
- 其余权重按原比例放大。只有情绪缺失时：market 0.20/0.90，trend 0.35/0.90，flow 0.25/0.90，derivatives 0.10/0.90。
- 衍生品也缺失时，再从剩下的 market、trend、flow 里归一化。
- market、trend、flow 任一为 NULL，总分就是 NULL，当天不允许发 BUY。

## 2. 评分等级边界

原文区间在 75、65、50、-30、-50、-65 上重叠。按下表取第一命中项：

| 条件 | 等级 |
| --- | --- |
| score >= 75 | STRONG_BULLISH |
| score >= 65 | BULLISH |
| score >= 50 | WATCH_BULLISH |
| score >= -30 | NEUTRAL |
| score >= -50 | WATCH_BEARISH |
| score > -65 | BEARISH |
| score <= -65 | STRONG_BEARISH |

BEARISH 只表示不宜新开多仓，不做空。

## 3. 市场分里原文没有给数字的部分

BTCTrend，输入有空值则为 NULL：

- close > EMA200 且 EMA50 > EMA200：+100
- close > EMA200 且 EMA50 <= EMA200：+40
- close <= EMA200 且 EMA50 >= EMA200：-40
- close < EMA200 且 EMA50 < EMA200：-100
- close 等于 EMA200 且 EMA50 低于 EMA200：0。输入有空值仍是 NULL。

MarketBreadthScore = 200 * 广度 - 100。没有有效资产则为 NULL。数据质量为 INVALID 的资产不进广度。

BTCVolatilityScore 只扣分，不表示方向。Z 用过去 252 根已收盘波动率，不含当天：

- 样本不足或标准差为 0：NULL
- |Z| <= 1：0
- |Z| <= 2：-40
- 否则：-80

某个子分是 NULL 时，MarketScore 用剩下的子权重重新归一化。

## 4. 趋势结构

摆动高点：high[i] 严格高于左边 2 根和右边 2 根。低点同理。右边 2 根必须已经收盘，所以最新可确认的摆动点停在 t-2。向前最多看 120 根日线。

- 日线不足 60 根：StructureScore = NULL
- 足够长但找不到两对高低点：0
- HH 且 HL：+100
- 只满足 HH 或 HL：+50
- 只满足 LH 或 LL：-50
- LH 且 LL：-100
- 高点和低点方向相反，或其中一边持平：0

EMA20 等于 EMA50 时 EMAScore = 0。EMA200 还没有时只使用 ±50，不把缺失的 EMA200 当成 0，也不给 ±100。

ADX 按原文只给非负强度，方向交给 EMA 和结构。

动量每个收益率先做时间序列 Z（窗口 252，均值和标准差只用 t 之前的数据），再 `100 * clip(Z/2, -1, 1)`。不足窗口或标准差为 0 则该分量为 NULL，动量内部重新归一化。

## 5. 资金流

VolumeScore 必须带上当天涨跌。`return1d = close/prevClose - 1`。

- 比率 >= 1.5 且上涨：+80；下跌：-80
- 比率 >= 1.0 且上涨：+40；下跌：-40
- 比率 < 0.7：-20
- 其余：0
- 成交额为 0 或比率算不出：NULL

SpotDeltaScore、SpotDelta7DScore 用同样的时间序列标准化。7 日分数用 7 日 SpotDelta 除以 7 日成交额之后再标准化。

## 6. 衍生品

拥挤风险分 0 到 100，DerivativesScore = 负的拥挤风险分。

- Funding Z > 3：60 分；否则 Funding Z > 2：40 分
- OI 7 日变化的历史 Z > 2：再加 30
- 7 日收益为正、Funding Z > 2、且 OI 7 日变化为正：再加 30
- 上限 100

OI 下降只写进 `reason_json`，不加安全分。资金费率或 OI 缺失时 DerivativesScore = NULL，不许填 0。

## 7. 市场状态

原始状态按第 8.2 节。确认状态要连续 2 根已收盘日线才进入，也要连续 2 根才退出。`confirm_count` 是截至当天原始状态连续相同的天数，满 `confirmDays` 才切换确认状态，否则保持前一天。序列开始前视为 NEUTRAL，所以历史起点不够 2 根时确认状态是 NEUTRAL。

紧急保护不改确认标签。满足下面任一条时，当天总仓位上限改用熊市上限 10%，并在原因里写 `riskOverride=EMERGENCY`：

- BTC 日收益率 <= -8%
- 市场广度较前一日下降达到 0.40

Phase 3 给 `ct_market_regime` 增加 `raw_regime`、`confirm_count`、`risk_override`。

## 8. 买卖信号里原文没写完的条件

日线回踩：`EMA20 - 1.0 * ATR14 <= 日线最低价 <= EMA20 + 0.5 * ATR14`。

4 小时回踩维持原文：`Low_4H <= EMA20_4H + 0.5 * ATR14_4H` 且 `Close_4H > EMA20_4H`。用当天最后一根已收盘 4 小时线。

SpotDelta7D 未显著恶化：SpotDelta7DScore >= -40。这个分数是 NULL 时，回踩买入不成立。

BTC 风险过滤失败的条件：

- BTC 日线数据质量是 INVALID
- 确认后的市场状态是 BEARISH
- 衍生品数据齐全，且 Funding Z > 2、OI 7 日变化为正、BTC 7 日收益为正

只有衍生品缺失时不因此否决买入，信号记 WARNING，建议仓位乘 0.7。K 线或核心特征缺失是 INVALID，禁止发有效交易信号。

没有持仓时：

- 满足回踩或突破：BUY
- 否则市场不是 BEARISH，且 TrendScore >= 60、TotalScore >= 50、EMA20 > EMA50：WATCH
- 否则 NO_TRADE

已有持仓时先看退出：

- SELL：收盘跌破 EMA50 且 FlowScore < 0；或 EMA20 < EMA50 且市场为 BEARISH；或收盘触及止损；或持仓达到 14 天且 TotalScore < 50
- REDUCE：还没到 SELL，且收盘低于 EMA20、FlowScore < 0、收盘仍不低于 EMA50；或持仓达到 14 天且 50 <= TotalScore < 65
- 其余：HOLD

止损初始值是入场价减 2 倍 ATR14。跟踪止损是入场后最高收盘价减 2 倍 ATR14。止损只上移。

信号主键：`{strategyVersion}:{symbol}:{tradeDate}:{signalType}:CTI_TREND_V1`。同一币、同一版本、同一日、同一类型只有一条。

阶段 4 把原文没写死的执行口径补在这里：

- 突破的趋势分门槛按原文 10.3 是 65，写在 `entry.breakoutTrendScore`。回踩和观察用 `minimumTrendScore` 60。BTC 拥挤过滤只否决回踩；突破仍必须避开确认后的熊市。
- 回踩的 EMA50 斜率是 `EMA50[t] / EMA50[t-10] - 1`，中间必须是连续 10 个日历日。缺口使斜率为 NULL，回踩不成立。
- 衍生品缺失时信号状态是 WARNING，建议仓位乘 `warningPositionScale`。状态 INVALID 只用于数据质量 INVALID，或总分因核心分缺失而无法计算；这种日子不允许开仓，已有持仓也不按坏数据去止损。
- 规划价用信号日已收盘的收盘价，止损距离是 `atrStopMultiplier` 倍 ATR14。研究账户在下一根日线开盘成交，买入价是开盘价乘 `1 + 滑点`，卖出价是开盘价乘 `1 - 滑点`，手续费另计。开盘价小于等于止损时按开盘价全部退出，当天不再开回同一币。
- 止损用进入当天之前的止损价和收盘价比较，收盘后才用当日最高收盘价上移跟踪止损。
- 持仓天数从成交日算起，含成交当天。满 `timeReviewDays` 才做时间退出。减仓卖出 `reduceFraction`（0.5）的数量。
- 同一天后来的买入要把前面已经建议的仓位算进敞口、持仓数和相关簇。相关簇只在双方最近 30 个日历日的对数收益都齐全、且相关系数大于等于 0.80 时成立；算不出来就不当成同一簇，也不记成 0。压到权益 1% 以下的新仓放弃。
- 阶段 4 的账户、持仓和权益是研究簿记。正式绩效、基准和走步验证留在阶段 5。

## 12. 回测

- 同一根 K 线上的新开仓按总分从高到低分配。研究账户仍按币种代码排序，两条路径都不用未来数据。
- `costScenarios` 的单边总成本拆成相等的手续费和滑点。BASE 是各 0.10%，和执行配置里的 0.001 + 0.001 相同。
- 基准只做多：BTC 买入持有在窗口第一根开盘买入；均线交叉和 14 日动量在收盘确定方向，下一根开盘成交。
- `MARKET_FILTER_TREND` 不要求回踩或突破，只要求市场不是熊市、趋势分达到 60、总分达到回踩门槛、并且 EMA20 高于 EMA50。
- 夏普和索提诺的无风险利率按 0。波动率为 0 时比率是 NULL。盈利因子在没有亏损交易时是 NULL。
- 样本外门槛不能用全样本结果顶上。训练 24 个月、验证 3 个月、测试 3 个月在当前历史里排不开时，走步结果是样本不足，不认定策略通过。
- EMA 周期和动量周期的敏感性只重算价格基准。已落库的 `features-1.0.0` 不在回测里改写。止损倍数、买入分数、放量倍数和突破回看直接作用在趋势策略上。

## 13. 页面和日报

- 页面由 `cti-api` 用服务端模板渲染。计算仍只在已经落库的结果上读取，页面不重算指标。
- 日涨跌用最近两根已收盘日线。图上的 K 线丢掉尚未收盘的那一根。
- 回测页的手续费和滑点用 LOW、BASE、HIGH 三个成本情景。初始资金保持策略里的 100000 USDT。资产池是全部已启用币种。
- 策略页只展示已落库配置。改公式要换版本。
- 日报按最近一个已有评分的交易日生成。同一天再跑会更新这一行。结论都带回表名和字段值。衍生品没有采集时写成空值。

## 9. 相关性和回撤

30 日对数收益相关系数 >= 0.80 的持仓算同一风险簇。新仓位加上簇内已有权重后不得超过 `maxSingleAssetWeight`。压到权益的 1% 以下就放弃。

回撤档位按配置：5% 时单笔风险预算乘 0.75，8% 乘 0.50，12% 停止新开仓。

成交：信号用 T 日收盘后的数据，最早在下一根 K 线开盘成交。买入价 = 开盘价 * (1 + 滑点)，卖出价 = 开盘价 * (1 - 滑点)，手续费按成交额另计。开盘跳空穿过止损时按开盘价退出，不按止损价成交。

## 10. 表和工程

Phase 0 修正了 `ct_kline` 的重复主键，并增加 `ct_job_run`。下面这些表在用到的阶段再建：

- Phase 1：`ct_data_quality_report`、`ct_kline_revision`
- Phase 4：`ct_portfolio_account`、`ct_portfolio_position`、`ct_portfolio_equity`
- Phase 6：`ct_daily_report`

K 线 `is_closed = 1` 的条件是 `close_time` 早于交易所时间。未收盘 K 线可以入库，但不能进特征、评分和最终信号。

同一根 K 线只保留一行。未收盘的那根在收盘后要更新这一行，不能插成第二行，也不能把“正在形成的这一根”算成缺失。已经收盘的 K 线如果交易所修订了价格或成交额，原值写入 `ct_kline_revision`，Parquet 只追加新文件，不覆盖旧文件。

阶段 1 的任务是 REST 采集。WebSocket 留到后面，收盘判断用 K 线 `closeTime` 和 `/api/v3/time`。限流上限读 `exchangeInfo` 的 `rateLimits`，不把币安公布的权重配额写死。

行情采集放在 Java。指标、评分、信号和回测的数学只放在 Python。Java 负责调度、落库和查询。

Dashboard 用服务端页面，做在 `cti-api` 里，Phase 6 再做。V1 不自动下单，管理接口默认只给本机研究使用。

内部时间用 UTC。XXL-Job 的 Cron 按 UTC。

## 11. 指标计算

原文只写了指标名称，没有写种子和窗口是否包含当天。`features-1.0.0` 采用下面的算法。改周期或种子要换特征版本，不覆盖旧行。

- EMA 用标准指数平均，系数 `2/(N+1)`。前 N 根的种子是 SMA，不够 N 根是 NULL。这不是 Wilder 平滑。
- ATR、RSI、ADX 用 Wilder。第一根真实波幅是最高价减最低价。ADX 的方向运动从第二根开始，第一根真实波幅不进入 ADX 的初始求和。RSI 在平均涨跌都为 0 时是 NULL；只有上涨、没有下跌时是 100。
- 实现波动率用样本标准差（除以 N-1）乘 `sqrt(365)`。窗口内完全没波动时结果是 0。样本不足才是 NULL。
- 滚动 Z 分数的均值和标准差只用当前值之前的样本，不含当天。标准差为 0 或样本不足是 NULL，不记成 0。资金费率 Z 用此前 90 次已经结算的费率，缺一天不补 0。
- 持仓量变化按日历对齐，并且只用 USDT 名义价值。缺前一日或前 7 日就是 NULL，不拿更早的快照冒充。
- 成交量比率和 Spot Delta 用成交额。成交额为 0 时比率是 NULL。Spot Delta 本身仍按公式计算，两边都是 0 时结果是 0。
- 日线缺一根，后面的 EMA、ATR、RSI、ADX、收益率和波动率重新预热，不把缺口两边当成相邻 K 线。
- 未收盘 K 线不进入特征。合约历史还没采集，所以持仓量和资金费率列先保持 NULL，公式用固定数据单测。
- 4 小时指标用同一套函数。本阶段只把日线写入 `ct_feature_daily`。EMA 排列、斜率和价格偏离可以由存下来的 EMA 重算，不另开列。7 日 Spot Delta 同样先留在计算函数里，表里只存当日 Spot Delta。
- 横截面排名留给阶段 3。阶段 2 只做时间序列标准化。
