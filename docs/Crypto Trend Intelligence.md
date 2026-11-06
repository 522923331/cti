# Crypto Trend Intelligence

## V1.0 产品需求与技术开发 Spec

**项目名称：** Crypto Trend Intelligence
**项目代号：** CTI
**版本：** V1.0
**文档类型：** 产品需求 + 技术架构 + 量化模型 + 数据库设计 + 回测规范
**技术架构：** Java 17 + Spring Boot + Python
**数据源：** Binance
**数据存储：** MySQL 8.0 + Parquet + Redis
**任务调度：** XXL-Job 2.4.0
**首批资产：** BTC、ETH、SOL、BNB、XRP、LINK、AVAX
**交易方式：** 现货、无杠杆、只做多
**研究周期：** 1～14 天
**项目目标：** 建立一套可解释、可量化、可回测、可持续迭代的加密货币趋势识别与现货波段交易决策系统。

# 一、项目背景与建设目标

## 1.1 项目背景

加密货币市场缺乏传统股票市场意义上的统一估值锚点，价格受到宏观流动性、市场情绪、现货资金流、衍生品仓位、市场结构和突发消息等因素共同影响。

单纯使用技术指标、新闻消息或社交媒体情绪判断市场，容易出现以下问题：

1. 指标之间缺乏统一的判断标准。
2. 消息和价格走势经常出现背离。
3. 价格上涨不一定代表真实现货需求增强。
4. 衍生品杠杆可能放大短期行情。
5. 不同币种的波动率和流动性差异较大。
6. 人工判断难以长期保持一致。
7. 缺乏历史回测，无法验证交易判断是否有效。

因此，需要构建一套以数据驱动为核心的市场研究系统。

## 1.2 建设目标

建立一个 Crypto Market Intelligence 系统，实现：

- 自动采集 Binance 市场数据。
- 自动计算市场趋势、动量、波动率和资金流指标。
- 判断整个加密市场的牛市、熊市和震荡状态。
- 对 BTC、ETH 和主流山寨币进行横向比较。
- 计算币种趋势评分。
- 自动识别买入、持有、观察和卖出信号。
- 提供可解释的交易信号。
- 根据风险预算计算建议仓位。
- 建立历史回测和策略评估体系。
- 生成每日市场研究报告。
- 支持策略参数版本管理和历史信号追溯。

## 1.3 第一版不建设的功能

V1.0 明确不包含：

1. 自动实盘交易。
2. 合约下单和杠杆交易。
3. AI 自动预测价格。
4. 新闻 NLP 和社交媒体情绪模型。
5. 收费链上数据接入。
6. ETF 和宏观数据接入。
7. 复杂机器学习模型。
8. 多交易所价格聚合。

以上功能作为后续版本规划。

## 1.4 核心设计原则

**原则一：价格趋势优先，资金流验证，衍生品识别风险。**

**原则二：市场状态优先于单币信号。**

**原则三：只使用决策时点已经可获得的数据。**

**原则四：所有模型指标必须能够通过历史数据重算。**

**原则五：策略参数必须经过样本外测试，不能根据单段历史行情调参。**

**原则六：允许空仓，不强制每天产生买卖信号。**

# 二、业务范围和交易规则

## 2.1 首批资产池

| 资产 | Binance 现货交易对 | 类型       |
| ---- | ------------------ | ---------- |
| BTC  | BTCUSDT            | 核心资产   |
| ETH  | ETHUSDT            | 核心资产   |
| SOL  | SOLUSDT            | 主流山寨币 |
| BNB  | BNBUSDT            | 主流山寨币 |
| XRP  | XRPUSDT            | 主流山寨币 |
| LINK | LINKUSDT           | 主流山寨币 |
| AVAX | AVAXUSDT           | 主流山寨币 |

说明：

- 交易对必须通过 Binance exchangeInfo 实时确认。
- 如果交易对不处于 TRADING 状态，不允许产生新买入信号。
- 资产池需要支持数据库动态配置。
- 不允许在历史回测中仅使用当前仍然上市的币种来代表全部历史资产池。
- V1.0 使用上述七个币种建立基础验证集，动态资产池作为后续扩展。

## 2.2 交易规则

| 项目         | 规则      |
| ------------ | --------- |
| 交易类型     | 现货      |
| 交易方向     | 只做多    |
| 杠杆         | 不使用    |
| 主要周期     | 1～14 天  |
| 决策周期     | 日线      |
| 辅助周期     | 4 小时    |
| 大周期过滤   | 周线      |
| 最大持仓币种 | 初始 5 个 |
| 是否允许现金 | 是        |
| 是否自动下单 | 否        |
| 是否允许空仓 | 是        |

## 2.3 时间标准

系统统一使用 UTC 作为内部时间标准。

北京时间仅用于报告展示和任务调度。

时间规则：

- 日线：UTC 00:00～23:59:59。
- 4 小时：UTC 00:00、04:00、08:00、12:00、16:00、20:00。
- 周线：UTC 周一 00:00 开始。
- 所有计算只允许使用已经收盘的 K 线。

信号生成时间和行情数据时间必须分离。

例如：

2026-11-05 的日线收盘时间是 2026-11-05 23:59:59 UTC。

在该 K 线正式收盘并且数据完整之前，不允许生成当天最终日线信号。

# 三、系统总体架构

## 3.1 系统架构

```
                    Binance
                       |
           +-----------+-----------+
           |                       |
      Spot REST API           Spot WebSocket
           |                       |
           +-----------+-----------+
                       |
                Java Data Service
                       |
              数据标准化与校验
                       |
           +-----------+-----------+
           |                       |
         MySQL                  Parquet
           |                       |
           +-----------+-----------+
                       |
                 Python Engine
                       |
        +--------------+--------------+
        |              |              |
    Market Engine  Trend Engine   Flow Engine
        |              |              |
        +--------------+--------------+
                       |
                 Risk Engine
                       |
                Decision Engine
                       |
           +-----------+-----------+
           |                       |
       MySQL结果表              Redis缓存
           |                       |
           +-----------+-----------+
                       |
                 Java API Service
                       |
                 Dashboard
                       |
                 Daily Report
```

## 3.2 模块职责

### Java 服务负责

- Binance 数据采集。
- REST API 调用。
- WebSocket 连接。
- K 线数据落库。
- Parquet 文件管理。
- 数据质量检查。
- XXL-Job 任务调度。
- Python 计算任务编排。
- 策略配置管理。
- 信号结果持久化。
- REST API。
- Dashboard 数据接口。
- 日志和异常管理。

### Python 服务负责

- 历史数据读取。
- 技术指标计算。
- 特征工程。
- 市场状态识别。
- 趋势评分。
- 资金流评分。
- 风险评分。
- 买卖信号生成。
- 组合仓位计算。
- 历史回测。
- 绩效统计。
- 策略参数实验。

### MySQL 负责

保存业务数据、配置数据、任务执行记录和计算结果。

### Parquet 负责

保存大量历史行情、特征数据和回测中间数据。

### Redis 负责

保存最新市场状态、最新评分、排行榜、Dashboard 缓存和分布式任务锁。

# 四、数据采集模块

## 4.1 数据源定义

### A. Binance Spot

基础地址：

```
https://api.binance.com
```

公开市场数据备用地址：

```
https://data-api.binance.vision
```

官方文档：

https://developers.binance.com/docs/binance-spot-api-docs

主要接口：

| 接口                     | 用途             | 采集频率             |
| ------------------------ | ---------------- | -------------------- |
| GET /api/v3/exchangeInfo | 交易对信息       | 每日                 |
| GET /api/v3/klines       | K 线             | 每 4 小时 / 历史补全 |
| GET /api/v3/ticker/24hr  | 24 小时行情      | 每 5 分钟            |
| GET /api/v3/depth        | 订单簿           | 按需                 |
| GET /api/v3/aggTrades    | 聚合成交         | 按需                 |
| GET /api/v3/time         | 交易所服务器时间 | 每日及异常检查       |

K 线请求示例：

```
GET /api/v3/klines
    ?symbol=BTCUSDT
    &interval=1d
    &startTime=...
    &endTime=...
    &limit=1000
```

支持周期：

```
4h
1d
1w
```

历史回测需要时可以额外采集 1h 数据。

K 线返回字段：

| 序号 | 字段                | 含义                 |
| ---- | ------------------- | -------------------- |
| 0    | openTime            | 开盘时间             |
| 1    | open                | 开盘价               |
| 2    | high                | 最高价               |
| 3    | low                 | 最低价               |
| 4    | close               | 收盘价               |
| 5    | volume              | 成交量               |
| 6    | closeTime           | 收盘时间             |
| 7    | quoteVolume         | 成交额               |
| 8    | tradeCount          | 成交笔数             |
| 9    | takerBuyBaseVolume  | 主动买入基础资产数量 |
| 10   | takerBuyQuoteVolume | 主动买入计价资产金额 |
| 11   | ignore              | 忽略字段             |

特别注意：

Binance K 线中的 takerBuyQuoteVolume 可以用于计算现货主动买卖成交额差。

计算：

```
TakerBuyQuote = takerBuyQuoteVolume

TakerSellQuote = quoteVolume - takerBuyQuoteVolume

SpotDelta = TakerBuyQuote - TakerSellQuote

SpotDelta = 2 * takerBuyQuoteVolume - quoteVolume
```

这里的 SpotDelta 是根据 K 线成交数据计算的主动买卖差额代理指标，不等于交易所账户的净资金流入。

### B. Binance USDⓈ-M Futures 公共数据

虽然策略只做现货，但需要利用合约市场数据识别杠杆拥挤风险。

基础地址：

```
https://fapi.binance.com
```

官方文档：

https://developers.binance.com/docs/derivatives/usds-margined-futures/market-data/rest-api

采集接口：

| 接口                                  | 用途                   |
| ------------------------------------- | ---------------------- |
| GET /fapi/v1/openInterest             | 当前 OI                |
| GET /futures/data/openInterestHist    | 历史 OI                |
| GET /fapi/v1/fundingRate              | 历史资金费率           |
| GET /fapi/v1/premiumIndex             | 当前资金费率和标记价格 |
| GET /futures/data/takerlongshortRatio | 多空账户比例，可选     |

注意：

1. OI 是合约市场数据，不代表现货资金流入。
2. 资金费率必须使用实际结算时间和当时可获取的数据。
3. 历史 OI 接口有时间窗口限制，不能假设可以直接获得无限历史数据。
4. 应从项目开始持续保存历史 OI 和 Funding。
5. 合约数据不可用时，必须标记为缺失，不能填充成 0。

第一版优先支持 BTCUSDT、ETHUSDT、SOLUSDT、BNBUSDT、XRPUSDT、LINKUSDT、AVAXUSDT 永续合约数据。

对于接口不支持的历史区间，不得伪造数据。

### C. 暂不接入的数据

以下数据暂时不进入 V1.0：

- ETF 净流入。
- MVRV。
- SOPR。
- NUPL。
- 稳定币供应。
- 宏观利率。
- 美元指数。
- 社交媒体情绪。

保留数据接口扩展能力，V1.1 再研究。

## 4.2 数据采集策略

### 初始历史数据

首次启动时：

1. 获取 exchangeInfo。
2. 获取七个币种的 1d K 线。
3. 获取七个币种的 4h K 线。
4. 获取必要的历史合约数据。
5. 补全至少 2 年日线数据。
6. 尽可能补全 4h 历史数据。
7. 检查数据连续性。
8. 生成 Parquet 文件。
9. 触发指标初始化计算。

200 日均线需要至少 200 根有效日线。

为了稳定计算，建议首次采集不少于 500 根日线。

### 增量采集

每天：

- 补充最新日线。
- 补充最新周线。
- 校验上一交易日 K 线。
- 检查是否有历史数据修订或缺失。

每四小时：

- 获取最新 4h K 线。
- 更新实时价格状态。
- 检查 WebSocket 与 REST 数据是否一致。

### 数据完整性

K 线唯一标识：

```
exchange_code + symbol + interval + open_time
```

使用唯一索引保证幂等写入。

禁止重复插入同一根 K 线。

缺失 K 线处理：

- 自动识别缺失时间段。
- 调用 REST API 补齐。
- 补齐失败记录异常。
- 数据缺失时禁止生成依赖该数据的有效交易信号。

## 4.3 Binance 限流与异常处理

必须实现：

- 请求权重监控。
- HTTP 429 退避。
- HTTP 418 禁止继续请求直至恢复。
- Retry-After 处理。
- 指数退避。
- 最大重试次数。
- WebSocket 断线重连。
- REST 补偿。
- API 超时。
- 请求失败告警。

不要将 Binance 接口的固定限流数值写死在代码里，应通过 exchangeInfo 和官方文档动态检查。

# 五、数据存储设计

数据库使用 MySQL 8.0。

所有价格字段使用 DECIMAL，避免使用 DOUBLE 存储原始交易价格。

历史 K 线主要归档到 Parquet。

以下为第一版核心 DDL。

## 5.1 币种配置表

```
CREATE TABLE ct_asset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    asset_code VARCHAR(32) NOT NULL,
    asset_name VARCHAR(64),
    quote_asset VARCHAR(16) DEFAULT 'USDT',
    symbol VARCHAR(32) NOT NULL,
    asset_type VARCHAR(32) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    start_date DATE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_asset_code (asset_code),
    UNIQUE KEY uk_symbol (symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

初始化：

```
INSERT INTO ct_asset
(asset_code, asset_name, quote_asset, symbol, asset_type)
VALUES
('BTC','Bitcoin','USDT','BTCUSDT','CORE'),
('ETH','Ethereum','USDT','ETHUSDT','CORE'),
('SOL','Solana','USDT','SOLUSDT','ALT'),
('BNB','BNB','USDT','BNBUSDT','ALT'),
('XRP','XRP','USDT','XRPUSDT','ALT'),
('LINK','Chainlink','USDT','LINKUSDT','ALT'),
('AVAX','Avalanche','USDT','AVAXUSDT','ALT');
```

## 5.2 交易所交易对表

```
CREATE TABLE ct_exchange_symbol (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exchange_code VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    base_asset VARCHAR(32) NOT NULL,
    quote_asset VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    is_spot TINYINT NOT NULL DEFAULT 1,
    min_qty DECIMAL(30,12),
    step_size DECIMAL(30,12),
    min_notional DECIMAL(30,12),
    last_sync_time DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_exchange_symbol (exchange_code,symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 5.3 K 线数据表

```
CREATE TABLE ct_kline (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exchange_code VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    interval_code VARCHAR(8) NOT NULL,
    open_time BIGINT NOT NULL,
    close_time BIGINT NOT NULL,
    open_price DECIMAL(30,12) NOT NULL,
    high_price DECIMAL(30,12) NOT NULL,
    low_price DECIMAL(30,12) NOT NULL,
    close_price DECIMAL(30,12) NOT NULL,
    volume DECIMAL(30,12) NOT NULL,
    quote_volume DECIMAL(30,12),
    trade_count BIGINT,
    taker_buy_base_volume DECIMAL(30,12),
    taker_buy_quote_volume DECIMAL(30,12),
    is_closed TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_kline (
        exchange_code,
        symbol,
        interval_code,
        open_time
    ),
    KEY idx_symbol_interval_time (
        symbol,
        interval_code,
        open_time
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

说明：

生产环境数据量增加后，应根据 symbol 和时间进行归档或分区评估。不要为了减少表数量而无限期将所有历史 K 线保留在在线 MySQL。

## 5.4 衍生品数据表

```
CREATE TABLE ct_derivatives_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    symbol VARCHAR(32) NOT NULL,
    snapshot_time BIGINT NOT NULL,
    open_interest DECIMAL(30,12),
    open_interest_value DECIMAL(30,12),
    funding_rate DECIMAL(20,12),
    mark_price DECIMAL(30,12),
    index_price DECIMAL(30,12),
    source VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_symbol_time_source (
        symbol,
        snapshot_time,
        source
    ),
    KEY idx_symbol_time (symbol,snapshot_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 5.5 每日特征表

```
CREATE TABLE ct_feature_daily (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    symbol VARCHAR(32) NOT NULL,
    trade_date DATE NOT NULL,

    ema20 DECIMAL(30,12),
    ema50 DECIMAL(30,12),
    ema200 DECIMAL(30,12),

    return_7d DECIMAL(20,12),
    return_14d DECIMAL(20,12),
    return_30d DECIMAL(20,12),
    return_90d DECIMAL(20,12),

    atr14 DECIMAL(30,12),
    atr_pct DECIMAL(20,12),
    adx14 DECIMAL(20,12),
    rsi14 DECIMAL(20,12),

    realized_volatility_20d DECIMAL(20,12),
    volume_ratio_20d DECIMAL(20,12),

    spot_delta DECIMAL(30,12),
    spot_delta_ratio DECIMAL(20,12),

    oi_change_1d DECIMAL(20,12),
    oi_change_7d DECIMAL(20,12),
    funding_rate DECIMAL(20,12),
    funding_zscore DECIMAL(20,12),

    feature_version VARCHAR(32) NOT NULL,
    data_quality_status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_symbol_date_version (
        symbol,
        trade_date,
        feature_version
    ),
    KEY idx_trade_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 5.6 市场状态表

```
CREATE TABLE ct_market_regime (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trade_date DATE NOT NULL,
    regime VARCHAR(32) NOT NULL,
    market_score DECIMAL(10,4),
    btc_trend_score DECIMAL(10,4),
    market_breadth DECIMAL(10,4),
    btc_volatility DECIMAL(20,12),
    total_exposure_limit DECIMAL(10,4),
    reason_json JSON,
    strategy_version VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_version (
        trade_date,
        strategy_version
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 5.7 趋势评分表

```
CREATE TABLE ct_trend_score (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    symbol VARCHAR(32) NOT NULL,
    trade_date DATE NOT NULL,

    market_score DECIMAL(10,4),
    trend_score DECIMAL(10,4),
    flow_score DECIMAL(10,4),
    derivatives_score DECIMAL(10,4),
    sentiment_score DECIMAL(10,4),

    total_score DECIMAL(10,4),
    signal_level VARCHAR(32),

    reason_json JSON,
    risk_json JSON,

    feature_version VARCHAR(32) NOT NULL,
    strategy_version VARCHAR(32) NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_symbol_date_version (
        symbol,
        trade_date,
        strategy_version
    ),
    KEY idx_total_score (trade_date,total_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 5.8 交易信号表

```
CREATE TABLE ct_trade_signal (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    signal_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    signal_time DATETIME NOT NULL,
    trade_date DATE NOT NULL,

    signal_type VARCHAR(32) NOT NULL,
    signal_status VARCHAR(32) NOT NULL,

    score DECIMAL(10,4),
    entry_price DECIMAL(30,12),
    stop_price DECIMAL(30,12),

    suggested_position DECIMAL(20,12),
    risk_amount DECIMAL(30,12),

    signal_reason JSON,
    invalidation_reason JSON,

    feature_version VARCHAR(32),
    strategy_version VARCHAR(32),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_signal_id (signal_id),
    KEY idx_symbol_time (symbol,signal_time),
    KEY idx_status_time (signal_status,signal_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 5.9 回测任务表

```
CREATE TABLE ct_backtest_run (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id VARCHAR(64) NOT NULL,
    strategy_name VARCHAR(64) NOT NULL,
    strategy_version VARCHAR(32) NOT NULL,

    start_date DATE NOT NULL,
    end_date DATE NOT NULL,

    initial_capital DECIMAL(30,12),
    commission_rate DECIMAL(20,12),
    slippage_rate DECIMAL(20,12),

    status VARCHAR(32) NOT NULL,
    config_json JSON,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME,

    UNIQUE KEY uk_run_id (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 5.10 回测结果表

```
CREATE TABLE ct_backtest_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id VARCHAR(64) NOT NULL,

    total_return DECIMAL(20,12),
    annualized_return DECIMAL(20,12),
    annualized_volatility DECIMAL(20,12),

    sharpe_ratio DECIMAL(20,12),
    sortino_ratio DECIMAL(20,12),
    max_drawdown DECIMAL(20,12),

    win_rate DECIMAL(20,12),
    profit_factor DECIMAL(20,12),
    total_trades INT,

    average_holding_days DECIMAL(20,8),
    total_fee DECIMAL(30,12),
    total_slippage DECIMAL(30,12),

    benchmark_return DECIMAL(20,12),
    excess_return DECIMAL(20,12),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_run_id (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 5.11 策略配置表

```
CREATE TABLE ct_strategy_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    strategy_name VARCHAR(64) NOT NULL,
    strategy_version VARCHAR(32) NOT NULL,
    config_json JSON NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 0,
    description VARCHAR(512),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_strategy_version (
        strategy_name,
        strategy_version
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

# 六、Parquet 文件规范

历史数据使用 Parquet 存储。

建议目录：

```
data/
  raw/
    binance/
      spot/
        klines/
          interval=1d/
            symbol=BTCUSDT/
          interval=4h/
            symbol=BTCUSDT/
      futures/
        open_interest/
        funding_rate/

  features/
    daily/
      trade_date=2026-11-05/
        part-000.parquet

    four_hour/
      trade_date=2026-11-05/
        part-000.parquet

  backtest/
    run_id=xxx/
      trades.parquet
      equity_curve.parquet
      daily_returns.parquet
```

字段要求：

- 所有时间字段统一 UTC。
- 价格和数量采用高精度数值类型。
- 保留原始数据与计算数据的区别。
- 记录 source、data_version、feature_version。
- 不允许覆盖历史原始数据而不保留修订记录。

# 七、量化指标计算规范

本章节定义 V1.0 所有指标的计算规则。

所有指标必须由 Python 统一实现。

禁止 Java 和 Python 各自实现一套不同的技术指标计算逻辑。

## 7.1 基础价格指标

### A. EMA

```
EMA(N) = ExponentialMovingAverage(Close, N)
```

周期：

```
EMA20
EMA50
EMA200
```

### B. 收益率

```
Return_N = Close[t] / Close[t-N] - 1
```

周期：

```
Return_7D
Return_14D
Return_30D
Return_90D
```

### C. ATR

采用 Wilder ATR。

```
TR = max(
    High - Low,
    abs(High - PreviousClose),
    abs(Low - PreviousClose)
)

ATR14 = WilderMovingAverage(TR, 14)

ATR_PCT = ATR14 / Close
```

### D. ADX

采用 Wilder 标准 ADX14 算法。

用于衡量趋势强度，不用于直接判断方向。

### E. RSI

采用 Wilder RSI14。

主要用于辅助判断短期超买和超卖，不直接产生买入信号。

### F. 实现波动率

```
DailyReturn = ln(Close[t] / Close[t-1])

RealizedVolatility20D =
    std(DailyReturn, 20) * sqrt(365)
```

加密市场全年 365 天交易，因此年化采用 365。

## 7.2 成交量与现货资金流

### A. Volume Ratio

```
VolumeRatio20D =
    QuoteVolume[t] / SMA(QuoteVolume, 20)
```

### B. Spot Delta

```
SpotDelta =
    2 * TakerBuyQuoteVolume - QuoteVolume
```

### C. Spot Delta Ratio

```
SpotDeltaRatio =
    SpotDelta / QuoteVolume
```

当 QuoteVolume 为 0 时，结果为 NULL。

### D. Spot Delta 7D

```
SpotDelta7D =
    SUM(SpotDelta, 7)
```

用于衡量一周主动买卖力量。

不能将 Spot Delta 解释成真实净资金流入。

## 7.3 衍生品指标

### A. OI Change

```
OIChange1D =
    OI[t] / OI[t-1] - 1

OIChange7D =
    OI[t] / OI[t-7] - 1
```

优先使用统一的 USDT 名义价值口径。

不同合约类型不可直接混加。

### B. Funding Z-Score

```
FundingZ =
    (FundingRate - Mean(FundingRate, 90))
    / Std(FundingRate, 90)
```

历史样本必须使用当时已经结算的数据。

### C. 衍生品风险识别

风险情形：

```
OI 快速上升 + Funding 极端正值
    => 多头拥挤风险

OI 下降 + 价格稳定
    => 可能发生去杠杆

OI 下降 + 价格快速下跌
    => 可能发生多头清算

OI 上升 + 价格下跌
    => 空头参与增加的可能性上升
```

以上只是风险解释，不能直接认定每个仓位的开仓方向。

## 7.4 趋势结构指标

### A. EMA 排列

```
BullishEMA =
    EMA20 > EMA50 AND EMA50 > EMA200
```

### B. EMA 斜率

```
EMA50Slope =
    EMA50[t] / EMA50[t-10] - 1
```

### C. 价格偏离

```
EMA50Distance =
    Close / EMA50 - 1
```

### D. 市场广度

```
MarketBreadth =
    Count(Close > EMA50)
    / Count(ValidAssets)
```

只统计有效数据资产。

# 八、市场状态识别 Market Regime Engine

## 8.1 市场状态

支持：

```
BULLISH
NEUTRAL
BEARISH
```

## 8.2 初始判断规则

BULLISH：

```
BTC Close > BTC EMA200
AND BTC EMA50 > BTC EMA200
AND MarketBreadth >= 0.60
```

BEARISH：

```
BTC Close < BTC EMA200
AND BTC EMA50 < BTC EMA200
AND MarketBreadth <= 0.30
```

其他情况：

```
NEUTRAL
```

## 8.3 状态持续性

为避免短期噪声：

- 状态进入需要连续 2 个日线收盘确认。
- 状态退出也需要连续 2 个日线收盘确认。
- 出现严重异常行情时允许触发紧急风险保护。
- 不能使用未收盘日线确认市场状态。

## 8.4 市场状态仓位上限

初始配置：

| 市场状态 | 总仓位上限 |
| -------- | ---------- |
| BULLISH  | 80%        |
| NEUTRAL  | 35%        |
| BEARISH  | 10%        |

该参数必须进入策略配置表，允许回测调整。

# 九、趋势评分模型 Trend Score Engine

## 9.1 评分结构

综合评分：

```
TotalScore =
    0.20 * MarketScore
  + 0.35 * TrendScore
  + 0.25 * FlowScore
  + 0.10 * DerivativesScore
  + 0.10 * SentimentScore
```

每个子评分范围：

```
-100 ~ +100
```

V1.0 暂无独立情绪和链上数据。

因此：

```
SentimentScore = 0
```

并将其标记为中性占位，不允许伪造情绪数据。

市场状态仍然单独作为硬过滤条件。

## 9.2 标准化方法

使用历史滚动 Z-Score：

```
ZScore =
    (X - RollingMean(X, 252))
    / RollingStd(X, 252)
```

标准化：

```
NormalizedScore =
    100 * clip(ZScore / 2, -1, 1)
```

对于收益率和不同币种比较，必须明确使用时间序列还是横截面标准化。

规则：

- 单币自身趋势：时间序列标准化。
- 同一时间比较不同币种：横截面排名。
- 任何标准化都只能使用当前时点之前的数据。
- 标准差为 0 或历史样本不足时，结果为 NULL，不可默认为 0。

## 9.3 Market Score

初始权重：

```
MarketScore =
    0.40 * BTCTrend
  + 0.30 * MarketBreadthScore
  + 0.30 * BTCVolatilityScore
```

其中：

BTCTrend：

- BTC 高于 EMA200：正向。
- BTC 低于 EMA200：负向。
- EMA50 方向用于调整趋势强度。

MarketBreadthScore：

```
BreadthScore =
    200 * MarketBreadth - 100
```

BTCVolatilityScore：

- 正常波动不扣分。
- 极端波动增加风险。
- 不把高波动直接解释成看涨或看跌。

## 9.4 Trend Score

```
TrendScore =
    0.30 * EMAScore
  + 0.25 * MomentumScore
  + 0.20 * ADXScore
  + 0.25 * StructureScore
```

EMAScore：

```
EMA20 > EMA50 > EMA200 => +100

EMA20 > EMA50 => +50

EMA20 < EMA50 => -50

EMA20 < EMA50 < EMA200 => -100
```

MomentumScore：

综合：

```
Return7D
Return14D
Return30D
Return90D
```

初始权重：

```
MomentumScore =
    0.20 * Return7DScore
  + 0.35 * Return14DScore
  + 0.30 * Return30DScore
  + 0.15 * Return90DScore
```

ADXScore：

```
ADX < 15       => -20
15 <= ADX < 20 => 0
20 <= ADX < 25 => 40
ADX >= 25      => 70
```

ADX 只代表趋势强度，方向必须由价格结构确定。

StructureScore：

```
HH + HL => +100
HH 或 HL 部分成立 => +50
结构不明显 => 0
LH 或 LL 部分成立 => -50
LH + LL => -100
```

结构识别必须通过过去已收盘 K 线的局部高低点算法实现。

## 9.5 Flow Score

```
FlowScore =
    0.35 * VolumeScore
  + 0.35 * SpotDeltaScore
  + 0.30 * SpotDelta7DScore
```

VolumeScore：

- 成交额高于 20 日均值：正向。
- 成交额明显低于 20 日均值：负向或中性。
- 必须结合价格方向解释。

SpotDeltaScore：

- 主动买入成交额占比提高：正向。
- 主动卖出成交额占比提高：负向。

SpotDelta7DScore：

- 过去 7 天主动买卖差持续改善：正向。
- 持续恶化：负向。

不能仅因为成交量大就给予正向评分。

## 9.6 Derivatives Score

```
DerivativesScore =
    - CongestionRiskScore
```

重点扣分：

- Funding Z-Score 极端正值。
- OI 变化显著超过历史波动。
- 价格上涨但杠杆拥挤明显。
- 价格和 OI 出现异常背离。

如果衍生品数据不可用：

```
DerivativesScore = NULL
```

综合评分时重新归一化可用指标权重，并标记数据缺失。

不能把缺失数据解释成零风险。

## 9.7 信号等级

| TotalScore | 等级           |
| ---------- | -------------- |
| >= 75      | STRONG_BULLISH |
| 65～75     | BULLISH        |
| 50～65     | WATCH_BULLISH  |
| 30～50     | NEUTRAL        |
| -30～30    | NEUTRAL        |
| -50～-30   | WATCH_BEARISH  |
| -65～-50   | BEARISH        |
| <= -65     | STRONG_BEARISH |

注意：本系统是现货只做多模型。BEARISH 表示不宜新开多仓，不表示系统应该做空。

# 十、交易决策引擎 Decision Engine

## 10.1 信号类型

```
WATCH
BUY
HOLD
REDUCE
SELL
NO_TRADE
```

## 10.2 回踩买入策略

名称：

```
TREND_PULLBACK_V1
```

触发条件：

1. Market Regime 不为 BEARISH。
2. BTC 风险过滤器通过。
3. EMA20 > EMA50。
4. EMA50Slope > 0。
5. TrendScore >= 60。
6. TotalScore >= 65。
7. 价格回踩 EMA20 附近。
8. 4H 收盘重新站上 EMA20。
9. SpotDelta7D 未显著恶化。
10. 数据完整性通过。

回踩条件必须量化：

```
Low_4H <= EMA20_4H + 0.5 * ATR14_4H

Close_4H > EMA20_4H
```

同时要求回踩价格距离日线 EMA20 不超过预设 ATR 区间。

所有条件均以已收盘 K 线计算。

## 10.3 放量突破策略

名称：

```
TREND_BREAKOUT_V1
```

条件：

```
Close_D1 > PreviousHighestHigh20

VolumeRatio20D >= 1.5

TrendScore >= 65

TotalScore >= 70

MarketRegime != BEARISH
```

其中：

```
PreviousHighestHigh20 =
    max(High[t-20], ..., High[t-1])
```

不能将当前 K 线包含在过去 20 根 K 线最高价中。

防止未来数据泄漏。

## 10.4 卖出策略

支持：

### A. 趋势退出

```
Close_D1 < EMA50
AND FlowScore < 0
```

或者：

```
EMA20 < EMA50
AND MarketRegime = BEARISH
```

### B. ATR 止损

```
InitialStop =
    EntryPrice - 2 * ATR14
```

追踪止损：

```
TrailingStop =
    HighestCloseSinceEntry - 2 * ATR14
```

止损只能上移，不能下移。

### C. 时间退出

持仓达到 14 天时重新评估。

时间退出不默认强制卖出，作为独立策略参数参与回测。

## 10.5 信号去重

同一币种、同一策略版本、同一日线日期、同一信号类型只能产生一条有效信号。

信号必须具备唯一 signalId。

重复执行任务不能重复产生 BUY。

# 十一、仓位管理 Risk Engine

## 11.1 初始风险配置

```
{
  "accountCapital": 100000,
  "riskPerTrade": 0.005,
  "maxSingleAssetWeight": 0.15,
  "maxBtcWeight": 0.50,
  "maxEthWeight": 0.35,
  "maxAltcoinExposure": 0.30,
  "maxTotalExposure": 0.80,
  "minCashReserve": 0.20,
  "maxHoldingAssets": 5
}
```

以上为研究初始参数，必须允许调整。

## 11.2 仓位计算

```
RiskBudget =
    AccountEquity * RiskPerTrade
StopDistance =
    EntryPrice - StopPrice
Quantity =
    RiskBudget / StopDistance
PositionValue =
    Quantity * EntryPrice
```

最终仓位：

```
FinalPositionValue =
    min(
        PositionValue,
        SingleAssetLimit,
        TotalExposureRemaining,
        AssetClassLimit
    )
```

实际还需扣除交易手续费和预估滑点。

## 11.3 组合风险

必须计算：

- 单币持仓比例。
- BTC 总敞口。
- ETH 总敞口。
- 山寨币总敞口。
- 组合 30 日波动率。
- 组合最大回撤。
- 30 日收益率相关性矩阵。
- 组合风险贡献。

高相关币种不能视为独立风险。

## 11.4 回撤保护

初始参数：

| 账户回撤 | 风险处理                 |
| -------- | ------------------------ |
| 5%       | 单笔风险预算降低 25%     |
| 8%       | 单笔风险预算降低 50%     |
| 12%      | 暂停新开仓并进入人工复核 |

回撤定义：

```
Drawdown =
    CurrentEquity / HistoricalPeakEquity - 1
```

# 十二、Python 量化计算模块

## 12.1 项目结构

```
cti-quant/
  app/
    main.py

  config/
    settings.py

  data/
    mysql_repository.py
    parquet_repository.py
    market_loader.py

  indicators/
    trend.py
    momentum.py
    volatility.py
    volume.py
    derivatives.py

  features/
    feature_builder.py
    normalizer.py

  engines/
    market_regime_engine.py
    trend_engine.py
    flow_engine.py
    risk_engine.py
    decision_engine.py
    portfolio_engine.py

  strategies/
    trend_pullback_v1.py
    trend_breakout_v1.py

  backtest/
    engine.py
    portfolio.py
    execution.py
    metrics.py
    walk_forward.py

  api/
    schemas.py
    routes.py

  tests/
    test_indicators.py
    test_features.py
    test_regime.py
    test_scoring.py
    test_strategy.py
    test_backtest.py
```

## 12.2 Python 技术选型

```
Python 3.11+

pandas
numpy
pyarrow
scipy
pydantic
sqlalchemy
fastapi
pytest
```

第一版不引入深度学习框架。

所有指标计算必须有单元测试。

## 12.3 Python 接口

### 健康检查

```
GET /health
```

### 计算日线特征

```
POST /api/v1/features/daily/calculate
```

请求：

```
{
  "tradeDate": "2026-11-05",
  "symbols": [
    "BTCUSDT",
    "ETHUSDT",
    "SOLUSDT"
  ]
}
```

### 计算市场状态

```
POST /api/v1/market/regime/calculate
```

### 计算趋势评分

```
POST /api/v1/trend/score/calculate
```

### 生成交易信号

```
POST /api/v1/signals/generate
```

### 执行回测

```
POST /api/v1/backtest/run
```

### 查询回测结果

```
GET /api/v1/backtest/{runId}
```

Python 接口必须支持：

- 参数校验。
- 幂等执行。
- 任务超时。
- 异常信息返回。
- 计算版本记录。
- 日志追踪。
- 不允许静默吞掉计算异常。

# 十三、Java Spring Boot 模块设计

## 13.1 Java 项目结构

```
cti-server/
  cti-common/
  cti-market/
  cti-data/
  cti-feature/
  cti-strategy/
  cti-signal/
  cti-portfolio/
  cti-backtest/
  cti-admin/
  cti-api/
```

### cti-market

负责：

- Binance REST Client。
- Binance WebSocket Client。
- 交易对同步。
- 行情采集。
- 限流和重试。

### cti-data

负责：

- K 线入库。
- 数据完整性。
- 历史补偿。
- Parquet 文件写入。
- 数据质量检查。

### cti-feature

负责：

- 调用 Python 特征接口。
- 保存特征版本。
- 检查计算结果。

### cti-strategy

负责：

- 策略参数配置。
- 策略版本。
- 启停策略。
- 参数发布。

### cti-signal

负责：

- 信号生成任务。
- 信号保存。
- 信号去重。
- 信号解释。

### cti-portfolio

负责：

- 模拟账户。
- 虚拟持仓。
- 仓位建议。
- 组合风险。

### cti-backtest

负责：

- 回测任务创建。
- 回测任务调度。
- 回测结果管理。
- 回测报告。

### cti-admin

负责：

- 资产管理。
- 数据源管理。
- 参数管理。
- 任务管理。
- 系统监控。

### cti-api

负责：

- 对外 REST API。
- Dashboard 数据接口。
- 报告接口。

## 13.2 Java 规范

- Java 17。
- Spring Boot。
- MyBatis-Plus。
- MySQL 8。
- Redis。
- XXL-Job 2.4.0。
- Jackson。
- WebClient 或 OkHttp。
- BigDecimal 处理价格和数量。
- LocalDate、Instant 处理时间。
- UTC 作为统一内部时间。

禁止：

- Double 直接参与交易金额计算。
- 多处重复实现技术指标。
- 任务重试导致重复入库。
- 未收盘 K 线参与最终日线评分。
- 将缺失数据默认为 0。

# 十四、XXL-Job 任务设计

任务命名统一使用：

```
cti_xxx_job
```

## 14.1 任务清单

| 任务                        | Cron           | 作用         |
| --------------------------- | -------------- | ------------ |
| cti_sync_exchange_info_job  | 0 0 1 * * ?    | 同步交易对   |
| cti_sync_kline_4h_job       | 0 5 0/4 * * ?  | 补充4H K线   |
| cti_sync_kline_daily_job    | 0 10 0 * * ?   | 补充日线     |
| cti_sync_derivatives_job    | 0 0/15 * * * ? | 采集合约数据 |
| cti_data_quality_job        | 0 20 0 * * ?   | 数据质量检查 |
| cti_build_daily_feature_job | 0 30 0 * * ?   | 计算日线特征 |
| cti_build_market_regime_job | 0 40 0 * * ?   | 市场状态     |
| cti_build_trend_score_job   | 0 45 0 * * ?   | 趋势评分     |
| cti_generate_signal_job     | 0 50 0 * * ?   | 交易信号     |
| cti_portfolio_risk_job      | 0 55 0 * * ?   | 组合风险     |
| cti_daily_report_job        | 0 0 1 * * ?    | 每日报告     |
| cti_parquet_archive_job     | 0 0 2 * * ?    | Parquet 归档 |

以上 Cron 按 UTC 执行。

北京时间展示时间为 UTC+8。

注意：生产任务必须在 K 线收盘后进行完整性检查，不能只依赖 Cron 时间。

## 14.2 任务依赖

```
K线采集
   |
   v
数据质量检查
   |
   v
日线特征计算
   |
   v
市场状态计算
   |
   v
趋势评分
   |
   v
交易信号
   |
   v
组合风险
   |
   v
每日报告
```

任何上游任务失败：

- 下游任务不允许继续生成有效结果。
- 记录失败原因。
- 允许安全重试。
- 同一日期重复执行必须幂等。

## 14.3 分布式任务控制

必须实现：

- XXL-Job 执行日志。
- Redis 分布式锁。
- 任务唯一业务日期。
- 幂等处理。
- 最大重试次数。
- 超时控制。
- 失败告警。

Redis 锁不能代替数据库唯一约束。

# 十五、回测系统 Backtest Engine

## 15.1 回测目标

验证：

1. 趋势模型是否具有预测能力。
2. 资金流是否改善交易结果。
3. 衍生品风险指标是否降低回撤。
4. 综合评分是否优于简单均线策略。
5. 山寨币组合是否优于 BTC 基准。
6. 不同市场状态下策略是否稳定。

## 15.2 回测时间范围

首批研究数据：

- 至少 2 年日线。
- 尽可能完整的 4 小时数据。
- 衍生品数据从实际可获得的历史时间开始。

不同数据源覆盖时间不同。

不得为了统一起始日期而虚构历史数据。

## 15.3 对照策略

必须实现以下基准：

```
BASELINE_1:
BTC Buy And Hold

BASELINE_2:
BTC EMA50 / EMA200

BASELINE_3:
BTC EMA20 / EMA50

BASELINE_4:
14D Time Series Momentum

STRATEGY_1:
Trend Pullback

STRATEGY_2:
Trend Breakout

STRATEGY_3:
Market Filter + Trend Score
```

## 15.4 回测执行规则

日线信号：

- 使用 T 日收盘后可获得的数据。
- 最早在 T+1 日开盘执行。
- 不允许使用 T 日收盘价作为提前知道信号后的成交价。

4H 信号：

- 使用完整收盘的 4H K 线。
- 在下一根 4H K 线开盘后执行。

如果开盘跳空超过止损价：

- 按可成交开盘价模拟退出。
- 不允许假设一定以止损价成交。

## 15.5 手续费和滑点

初始研究参数：

```
{
  "commissionRate": 0.001,
  "slippageRate": 0.001,
  "initialCapital": 100000,
  "quoteAsset": "USDT"
}
```

这代表单边各 0.1% 的手续费和滑点假设。

正式回测需要分别统计：

- 手续费。
- 买入滑点。
- 卖出滑点。
- 现金余额。
- 实际成交数量。

并进行不同成本情景测试：

```
低成本：单边总成本 0.05%
基准成本：单边总成本 0.20%
高成本：单边总成本 0.40%
```

这些是压力测试参数，不是 Binance 对所有用户统一适用的实际费率。

实际交易成本应根据账户等级、手续费折扣和流动性确定。

## 15.6 回测资金管理

初始资金：

```
100000 USDT
```

初始单笔风险：

```
0.5%
```

最大持仓：

```
5 个币种
```

最大总仓位：

```
80%
```

不能使用未来表现来决定历史资产权重。

同一根 K 线中多个币种同时产生信号时：

1. 按 TotalScore 排序。
2. 进行市场状态过滤。
3. 检查组合相关性。
4. 按风险预算计算仓位。
5. 按资金余额分配。
6. 不满足约束的信号放弃执行。

## 15.7 回测绩效指标

必须输出：

| 指标                    | 说明             |
| ----------------------- | ---------------- |
| Total Return            | 总收益           |
| CAGR                    | 年化收益         |
| Annualized Volatility   | 年化波动率       |
| Sharpe Ratio            | 夏普比率         |
| Sortino Ratio           | 下行风险调整收益 |
| Max Drawdown            | 最大回撤         |
| Win Rate                | 胜率             |
| Profit Factor           | 盈利因子         |
| Average Holding Days    | 平均持仓天数     |
| Trade Count             | 交易次数         |
| Turnover                | 换手率           |
| Total Fee               | 总手续费         |
| Total Slippage          | 总滑点           |
| Benchmark Excess Return | 相对基准超额收益 |

## 15.8 Walk-Forward 测试

建议：

```
训练窗口：过去 24～36 个月

验证窗口：随后 3 个月

测试窗口：随后 3 个月

向前滚动：3 个月
```

所有参数只能在训练集和验证集确定。

测试集不能参与参数优化。

## 15.9 参数敏感性测试

需要测试：

| 参数       | 备选值             |
| ---------- | ------------------ |
| EMA短周期  | 10、15、20、25     |
| EMA长周期  | 40、50、60、80     |
| 动量周期   | 7、10、14、21      |
| 突破周期   | 10、15、20、30     |
| ATR倍数    | 1.5、2、2.5、3     |
| 买入评分   | 60、65、70、75     |
| 成交量倍数 | 1.2、1.5、1.8、2.0 |

重点是寻找稳定参数区间，不是寻找历史收益最高的参数组合。

## 15.10 回测验收门槛

初始内部目标：

```
Out-of-Sample Sharpe > 1.0

Profit Factor > 1.3

Max Drawdown < 20%

交易数量尽可能 >= 100

交易成本完整计入

样本外收益为正

相邻参数表现稳定

多个市场状态下进行验证
```

如果样本不足，必须报告样本不足，不能强行认定策略通过。

如果模型无法达到标准：

- 不允许直接进入实盘。
- 允许保留为研究策略。
- 输出失败原因。
- 重新评估指标和参数。

# 十六、Dashboard 功能需求

V1.0 提供以下页面。

## 16.1 市场总览

显示：

- BTC 当前趋势。
- ETH 当前趋势。
- 市场状态。
- 市场广度。
- 七个币种综合评分。
- 市场风险等级。
- 总建议仓位。
- 现金比例。

## 16.2 币种排行榜

字段：

```
Symbol
Price
24H Change
7D Return
14D Return
30D Return
EMA20
EMA50
EMA200
TrendScore
FlowScore
DerivativesScore
TotalScore
Signal
```

支持排序和筛选。

## 16.3 单币详情

展示：

- 日线 K 线。
- 4H K 线。
- EMA20 / EMA50 / EMA200。
- ATR。
- ADX。
- RSI。
- 现货成交量。
- Spot Delta。
- OI。
- Funding。
- 趋势评分变化。
- 历史交易信号。

## 16.4 交易信号页面

展示：

- 买入信号。
- 持有信号。
- 减仓信号。
- 卖出信号。
- 信号产生时间。
- 产生原因。
- 当前风险。
- 建议入场价。
- 建议止损价。
- 建议仓位。
- 失效条件。

## 16.5 回测管理

支持：

- 创建回测。
- 选择策略版本。
- 选择资产池。
- 选择开始和结束日期。
- 配置手续费。
- 配置滑点。
- 配置初始资金。
- 查看回测结果。
- 查看交易明细。
- 查看资金曲线。
- 查看最大回撤。
- 对比多个策略。

## 16.6 每日研究报告

自动生成：

```
1. 当前市场状态

2. BTC / ETH 趋势

3. 主流山寨币排行榜

4. 现货资金流分析

5. 衍生品风险分析

6. 新产生的买入信号

7. 持仓风险

8. 建议仓位

9. 关键失效条件

10. 数据质量异常
```

报告中的每一项结论必须能够追溯到具体数据。

# 十七、REST API 设计

Java API 前缀：

```
/api/v1
```

市场：

```
GET /market/overview
GET /market/regime
GET /market/breadth
```

资产：

```
GET /assets
GET /assets/{symbol}
GET /assets/{symbol}/klines
GET /assets/{symbol}/features
```

趋势：

```
GET /trend/ranking
GET /trend/{symbol}
GET /trend/{symbol}/history
```

信号：

```
GET /signals
GET /signals/{signalId}
GET /signals/latest
```

组合：

```
GET /portfolio
GET /portfolio/positions
GET /portfolio/risk
GET /portfolio/equity
```

回测：

```
POST /backtests
GET /backtests
GET /backtests/{runId}
GET /backtests/{runId}/trades
GET /backtests/{runId}/equity-curve
```

策略：

```
GET /strategies
GET /strategies/{strategyName}
GET /strategies/{strategyName}/versions
```

管理：

```
GET /admin/jobs
GET /admin/data-quality
GET /admin/system-health
```

# 十八、数据质量与系统监控

必须监控：

1. Binance 接口连接状态。
2. K 线数据缺失。
3. K 线时间连续性。
4. K 线价格异常。
5. 数据采集延迟。
6. Parquet 写入失败。
7. Python 计算失败。
8. MySQL 连接异常。
9. Redis 缓存异常。
10. XXL-Job 任务失败。
11. 信号重复生成。
12. 指标计算结果异常。

每次评分必须有数据质量状态：

```
NORMAL
WARNING
INVALID
```

规则：

- NORMAL：允许生成正常信号。
- WARNING：允许计算，但需要降低置信度并标记。
- INVALID：禁止生成有效交易信号。

特别注意：

缺失数据不能被解释为中性信号。

# 十九、测试规范

## 19.1 单元测试

必须覆盖：

- EMA。
- ATR。
- ADX。
- RSI。
- 收益率。
- 波动率。
- Spot Delta。
- OI Change。
- Funding Z-Score。
- Z-Score。
- 市场广度。
- 趋势评分。
- 市场状态。
- 入场条件。
- 退出条件。
- 仓位计算。

指标需要使用人工构造的固定数据验证计算结果。

## 19.2 集成测试

必须验证：

- Binance API 数据解析。
- K 线重复写入。
- K 线缺失补偿。
- Parquet 文件读写。
- Python 与 Java 接口。
- MySQL 数据持久化。
- Redis 缓存。
- XXL-Job 任务依赖。
- 重试幂等性。

## 19.3 回测测试

必须验证：

- 不使用未来数据。
- 不使用未收盘 K 线。
- 下一周期成交。
- 手续费。
- 滑点。
- 资金余额。
- 同时触发多个信号。
- 止损跳空。
- 持仓上限。
- 资产退市和不可交易情况。

# 二十、AI 编程模型实施计划

这是本 Spec 最重要的交付部分。

禁止让 AI 编程模型一次性开发全部模块。

必须按 Phase 顺序执行，每个阶段通过验收后才能进入下一阶段。

## Phase 0：项目初始化

目标：

建立 Java 和 Python 工程基础。

任务：

1. 创建 Maven 多模块项目。
2. 创建 Python 工程。
3. 配置 MySQL。
4. 配置 Redis。
5. 配置 XXL-Job。
6. 创建 Docker Compose 开发环境。
7. 创建基础配置文件。
8. 创建日志规范。
9. 创建健康检查接口。
10. 初始化数据库迁移脚本。

验收：

- Java 项目能够启动。
- Python 服务能够启动。
- MySQL 正常连接。
- Redis 正常连接。
- XXL-Job 正常执行测试任务。
- Docker Compose 能够启动全部基础组件。

## Phase 1：行情数据采集

目标：

完成 Binance 行情数据采集。

任务：

1. 实现 Binance REST Client。
2. 实现 exchangeInfo。
3. 实现 1D K 线采集。
4. 实现 4H K 线采集。
5. 实现历史 K 线补全。
6. 实现 MySQL 幂等入库。
7. 实现 Parquet 归档。
8. 实现数据质量检查。
9. 实现限流和重试。
10. 实现 XXL-Job 任务。

验收：

- 七个币种均能正常采集。
- 历史数据完整性可检查。
- 重复执行不会产生重复数据。
- K 线缺失可以补齐。
- 数据能够保存到 MySQL 和 Parquet。
- 能够识别未收盘 K 线。

## Phase 2：指标计算

目标：

完成技术指标和特征工程。

任务：

1. 实现 EMA。
2. 实现 ATR。
3. 实现 ADX。
4. 实现 RSI。
5. 实现收益率。
6. 实现实现波动率。
7. 实现成交量指标。
8. 实现 Spot Delta。
9. 实现 OI 和 Funding 特征。
10. 实现特征标准化。
11. 保存特征版本。

验收：

- 所有指标通过单元测试。
- 历史指标可以重算。
- 同一数据和版本得到相同结果。
- 不存在未来数据引用。
- 特征数据能够持久化。

## Phase 3：市场状态和趋势评分

目标：

实现 Market Regime Engine 和 Trend Score Engine。

任务：

1. 实现市场广度。
2. 实现 BTC 市场状态识别。
3. 实现牛市、熊市、震荡市状态。
4. 实现 Trend Score。
5. 实现 Flow Score。
6. 实现 Derivatives Score。
7. 实现 Total Score。
8. 实现币种排行榜。
9. 实现评分原因解释。
10. 实现策略版本管理。

验收：

- 能够输出七个币种评分。
- 能够输出市场状态。
- 能够解释每个评分的来源。
- 相同输入和策略版本能够复现结果。
- 数据缺失不会被当成零风险。

## Phase 4：交易信号与风险管理

目标：

完成只做多的现货波段策略。

任务：

1. 实现趋势回踩策略。
2. 实现放量突破策略。
3. 实现 ATR 止损。
4. 实现追踪止损。
5. 实现趋势退出。
6. 实现时间退出。
7. 实现仓位计算。
8. 实现总仓位限制。
9. 实现相关性风险检查。
10. 实现信号去重。
11. 实现信号解释。

验收：

- 满足条件时能够产生 BUY。
- 不满足条件时不会强制买入。
- 产生信号后能够计算建议仓位。
- 止损价格正确。
- 重复运行不会重复产生信号。
- 强势熊市能够有效限制新开仓。

## Phase 5：回测系统

目标：

建立可重复的历史回测系统。

任务：

1. 实现事件驱动回测。
2. 实现模拟成交。
3. 实现手续费。
4. 实现滑点。
5. 实现现金账户。
6. 实现多币种组合。
7. 实现止损和退出。
8. 实现基准策略。
9. 实现绩效计算。
10. 实现 Walk-Forward。
11. 实现参数敏感性测试。
12. 实现回测报告。

验收：

- 可以回测指定日期范围。
- 可以比较不同策略。
- 能够输出完整资金曲线。
- 能够输出交易明细。
- 能够计算所有绩效指标。
- 能够证明没有使用未来数据。
- 能够重复执行同一回测并得到一致结果。

## Phase 6：Dashboard 和研究报告

目标：

形成完整的研究平台。

任务：

1. 市场总览。
2. 币种排行榜。
3. 单币趋势详情。
4. 交易信号。
5. 组合风险。
6. 回测管理。
7. 策略配置。
8. 每日研究报告。
9. 系统监控。
10. 历史信号追溯。

验收：

- 可以查看七个币种。
- 可以查看市场状态。
- 可以查看评分明细。
- 可以查看交易信号。
- 可以查看历史回测。
- 可以查看每日报告。
- 每个信号能够追溯到数据和策略版本。

# 二十一、全局项目验收标准

项目完成必须满足以下要求。

## 功能验收

- 七个币种正常采集。
- 1D 和 4H K 线正常。
- 历史数据可以补全。
- 数据质量检查正常。
- 技术指标计算正确。
- 市场状态识别正常。
- 趋势评分正常。
- 资金流评分正常。
- 衍生品风险评分正常。
- 买入信号正常。
- 卖出信号正常。
- 仓位计算正常。
- 回测正常。
- 每日报告正常。

## 工程验收

- Java 17。
- Spring Boot。
- MySQL 8。
- Redis。
- Python。
- Parquet。
- XXL-Job。
- Docker Compose。
- 日志和异常管理。
- 幂等性。
- 数据版本。
- 策略版本。

## 量化验收

- 无未来数据泄漏。
- 无未收盘 K 线参与最终信号。
- 有手续费和滑点。
- 有 BTC 买入持有基准。
- 有多策略比较。
- 有 Walk-Forward。
- 有参数敏感性测试。
- 有样本外测试。
- 有最大回撤统计。
- 有风险收益分析。

# 二十二、V1.0 策略配置初始值

以下配置统一进入 `ct_strategy_config`，禁止散落在代码中。

```
{
  "strategyName": "CTI_TREND_V1",
  "strategyVersion": "1.0.0",

  "assets": [
    "BTCUSDT",
    "ETHUSDT",
    "SOLUSDT",
    "BNBUSDT",
    "XRPUSDT",
    "LINKUSDT",
    "AVAXUSDT"
  ],

  "timeframes": {
    "primary": "1d",
    "secondary": "4h",
    "macro": "1w"
  },

  "indicators": {
    "emaShort": 20,
    "emaMedium": 50,
    "emaLong": 200,
    "atrPeriod": 14,
    "adxPeriod": 14,
    "rsiPeriod": 14,
    "momentumPeriods": [7, 14, 30, 90],
    "volumePeriod": 20,
    "volatilityPeriod": 20
  },

  "scoreWeights": {
    "market": 0.20,
    "trend": 0.35,
    "flow": 0.25,
    "derivatives": 0.10,
    "sentiment": 0.10
  },

  "entry": {
    "pullbackScore": 65,
    "breakoutScore": 70,
    "minimumTrendScore": 60,
    "breakoutVolumeRatio": 1.5
  },

  "risk": {
    "riskPerTrade": 0.005,
    "atrStopMultiplier": 2.0,
    "maxHoldingDays": 14,
    "maxHoldingAssets": 5,
    "maxTotalExposure": 0.80,
    "maxSingleAssetWeight": 0.15
  },

  "execution": {
    "commissionRate": 0.001,
    "slippageRate": 0.001,
    "executionTiming": "NEXT_BAR_OPEN"
  }
}
```

注意：

V1.0 初始评分权重只是研究参数。sentiment 当前没有实际数据源，必须保持缺失状态，综合评分对可用模块重新归一化，不能用虚构情绪分数参与评分。

# 二十三、后续版本规划

## V1.1：宏观和机构资金

增加：

- BTC ETF 资金流。
- ETH ETF 资金流。
- DXY。
- 美国国债收益率。
- 美联储利率预期。
- 稳定币供应。

## V1.2：链上数据

增加：

- MVRV。
- SOPR。
- NUPL。
- LTH/STH。
- Exchange Netflow。
- 实现价格。

## V1.3：AI 市场情报

增加：

- 新闻采集。
- 新闻事件分类。
- 新闻情绪分析。
- 项目风险事件。
- 监管事件。
- 事件对价格的历史影响分析。

AI 不直接生成 BUY/SELL，而是输出：

```
事件类型
事件时间
影响资产
影响方向
影响程度
信息来源
可信度
市场价格反应
```

最终由量化引擎决定是否调整信号。

## V2.0：动态资产池和高级量化策略

增加：

- 市值和流动性动态筛选。
- 横截面动量。
- 动态组合优化。
- 多因子模型。
- 波动率目标策略。
- 更完善的多交易所数据。

# 二十四、交付给 AI 编程模型的执行总指令

以下指令作为本 Spec 的实施入口。

## AI CODING AGENT MASTER PROMPT

你是一名资深量化交易系统架构师、Java 后端工程师和 Python 量化开发工程师。

请严格按照《Crypto Trend Intelligence V1.0 产品需求与技术开发 Spec》实施项目。

项目目标：

建立一个基于 Binance 行情数据的加密货币趋势识别和现货波段交易研究系统。

技术架构：

- Java 17
- Spring Boot
- MySQL 8.0
- Redis
- Python 3.11+
- Pandas
- NumPy
- Parquet
- FastAPI
- XXL-Job 2.4.0

核心要求：

1. 必须遵循 Spec 中的模块划分。
2. 必须按照 Phase 0～Phase 6 顺序开发。
3. 不允许跳过单元测试。
4. 不允许一次性生成整个项目而不进行阶段验收。
5. 不允许擅自修改策略公式和评分权重。
6. 不允许将缺失数据填充为 0。
7. 不允许使用未来数据。
8. 不允许使用未收盘 K 线产生最终日线信号。
9. 不允许自动实盘下单。
10. 所有策略参数必须可配置。
11. 所有交易信号必须可解释。
12. 所有数据采集必须幂等。
13. 所有任务必须可安全重试。
14. 所有计算必须保留版本信息。
15. 所有回测必须包含手续费和滑点。

每个 Phase 开发前：

1. 阅读对应 Spec。
2. 输出本阶段实施计划。
3. 列出需要创建或修改的文件。
4. 列出数据库变更。
5. 列出接口定义。
6. 明确验收标准。

每个 Phase 开发完成后：

1. 执行编译。
2. 执行单元测试。
3. 执行集成测试。
4. 检查是否存在未来函数。
5. 检查幂等性。
6. 输出实际完成情况。
7. 输出未完成事项。
8. 等待确认后再进入下一 Phase。

如果 Spec 中存在不明确的地方：

- 优先遵循已经定义的量化公式。
- 不允许自行创造未经确认的策略。
- 对无法确定的部分提出问题。
- 将策略假设和实现事实明确区分。

最终交付：

- 完整源代码。
- 数据库初始化 SQL。
- Docker Compose。
- Java 服务。
- Python 量化服务。
- XXL-Job 任务。
- API 文档。
- 单元测试。
- 回测报告。
- 项目部署文档。
- 策略参数说明。
- 每日市场研究报告。

