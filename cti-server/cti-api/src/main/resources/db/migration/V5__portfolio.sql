CREATE TABLE ct_portfolio_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_code VARCHAR(64) NOT NULL,
    strategy_name VARCHAR(64) NOT NULL,
    strategy_version VARCHAR(32) NOT NULL,
    quote_asset VARCHAR(16) NOT NULL,
    initial_capital DECIMAL(30,12) NOT NULL,
    cash_balance DECIMAL(30,12) NOT NULL,
    equity DECIMAL(30,12) NOT NULL,
    peak_equity DECIMAL(30,12) NOT NULL,
    as_of_date DATE NULL,
    risk_json JSON NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_account_version (account_code, strategy_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_portfolio_position (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_code VARCHAR(64) NOT NULL,
    strategy_version VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    quantity DECIMAL(30,12) NOT NULL,
    avg_entry_price DECIMAL(30,12) NOT NULL,
    stop_price DECIMAL(30,12) NOT NULL,
    highest_close DECIMAL(30,12) NOT NULL,
    opened_on DATE NOT NULL,
    updated_on DATE NOT NULL,
    market_value DECIMAL(30,12) NOT NULL,
    UNIQUE KEY uk_account_symbol (account_code, strategy_version, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_portfolio_equity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_code VARCHAR(64) NOT NULL,
    strategy_version VARCHAR(32) NOT NULL,
    trade_date DATE NOT NULL,
    cash_balance DECIMAL(30,12) NOT NULL,
    equity DECIMAL(30,12) NOT NULL,
    peak_equity DECIMAL(30,12) NOT NULL,
    drawdown DECIMAL(20,12) NOT NULL,
    exposure DECIMAL(20,12) NOT NULL,
    UNIQUE KEY uk_account_date (account_code, strategy_version, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
