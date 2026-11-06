-- Crypto Trend Intelligence V1 core schema.
-- Prices stay DECIMAL. Time columns written by the application are UTC.
-- ct_kline keeps a surrogate primary key. The spec text declared PRIMARY KEY twice;
-- idempotent writes use uk_kline (exchange_code, symbol, interval_code, open_time).

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
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_asset_code (asset_code),
    UNIQUE KEY uk_symbol (symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_exchange_symbol (exchange_code, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    UNIQUE KEY uk_kline (exchange_code, symbol, interval_code, open_time),
    KEY idx_symbol_interval_time (symbol, interval_code, open_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    UNIQUE KEY uk_symbol_time_source (symbol, snapshot_time, source),
    KEY idx_symbol_time (symbol, snapshot_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    UNIQUE KEY uk_symbol_date_version (symbol, trade_date, feature_version),
    KEY idx_trade_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    UNIQUE KEY uk_date_version (trade_date, strategy_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    UNIQUE KEY uk_symbol_date_version (symbol, trade_date, strategy_version),
    KEY idx_total_score (trade_date, total_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    KEY idx_symbol_time (symbol, signal_time),
    KEY idx_status_time (signal_status, signal_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

CREATE TABLE ct_strategy_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    strategy_name VARCHAR(64) NOT NULL,
    strategy_version VARCHAR(32) NOT NULL,
    config_json JSON NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 0,
    description VARCHAR(512),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_strategy_version (strategy_name, strategy_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Task execution log. The spec requires job history in MySQL but did not define a table.
-- Business jobs added later must stay idempotent per (job_name, business_date);
-- the Phase 0 probe is an audit row and increments attempt.
CREATE TABLE ct_job_run (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_name VARCHAR(64) NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt INT NOT NULL,
    message VARCHAR(1024),
    started_at DATETIME NOT NULL,
    finished_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_job_date_attempt (job_name, business_date, attempt),
    KEY idx_job_name_time (job_name, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ct_asset (asset_code, asset_name, quote_asset, symbol, asset_type)
VALUES
    ('BTC', 'Bitcoin', 'USDT', 'BTCUSDT', 'CORE'),
    ('ETH', 'Ethereum', 'USDT', 'ETHUSDT', 'CORE'),
    ('SOL', 'Solana', 'USDT', 'SOLUSDT', 'ALT'),
    ('BNB', 'BNB', 'USDT', 'BNBUSDT', 'ALT'),
    ('XRP', 'XRP', 'USDT', 'XRPUSDT', 'ALT'),
    ('LINK', 'Chainlink', 'USDT', 'LINKUSDT', 'ALT'),
    ('AVAX', 'Avalanche', 'USDT', 'AVAXUSDT', 'ALT');
