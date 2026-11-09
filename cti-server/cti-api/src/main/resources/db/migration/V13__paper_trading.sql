CREATE TABLE ct_paper_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id CHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    strategy_code VARCHAR(64) NOT NULL,
    signal_id VARCHAR(128) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(8) NOT NULL,
    order_type VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    quantity DECIMAL(30,12) NOT NULL,
    filled_quantity DECIMAL(30,12) NOT NULL,
    limit_price DECIMAL(30,12) DEFAULT NULL,
    stop_price DECIMAL(30,12) DEFAULT NULL,
    take_profit_price DECIMAL(30,12) DEFAULT NULL,
    fee_rate DECIMAL(20,12) NOT NULL,
    slippage_bps DECIMAL(20,12) NOT NULL,
    reject_reason VARCHAR(255) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_paper_order (order_id),
    UNIQUE KEY uk_paper_signal (account_id, strategy_code, signal_id, side)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_paper_fill (
    id BIGINT NOT NULL AUTO_INCREMENT,
    fill_id CHAR(64) NOT NULL,
    order_id CHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    price DECIMAL(30,12) NOT NULL,
    quantity DECIMAL(30,12) NOT NULL,
    fee DECIMAL(30,12) NOT NULL,
    slippage_bps DECIMAL(20,12) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_paper_fill (fill_id),
    UNIQUE KEY uk_paper_fill_request (order_id, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_portfolio_trade (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trade_id CHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    order_id CHAR(64) NOT NULL,
    fill_id CHAR(64) NOT NULL,
    ledger_request_id VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_portfolio_trade (trade_id),
    UNIQUE KEY uk_portfolio_trade_fill (fill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_allocation_plan (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_id CHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    regime VARCHAR(32) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    cash_weight DECIMAL(20,12) NOT NULL,
    config_json JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_allocation_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_allocation_detail (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_id CHAR(64) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    asset_group VARCHAR(16) NOT NULL,
    target_weight DECIMAL(20,12) NOT NULL,
    target_value DECIMAL(30,12) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_allocation_detail (plan_id, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_risk_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    snapshot_id CHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    plan_id CHAR(64) NOT NULL,
    exposure DECIMAL(20,12) NOT NULL,
    cash_weight DECIMAL(20,12) NOT NULL,
    btc_weight DECIMAL(20,12) NOT NULL,
    eth_weight DECIMAL(20,12) NOT NULL,
    alt_weight DECIMAL(20,12) NOT NULL,
    concentration DECIMAL(20,12) NOT NULL,
    historical_volatility DECIMAL(20,12) DEFAULT NULL,
    max_drawdown DECIMAL(20,12) DEFAULT NULL,
    correlation_json JSON DEFAULT NULL,
    var_value DECIMAL(20,12) DEFAULT NULL,
    cvar_value DECIMAL(20,12) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_snapshot (snapshot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
