CREATE TABLE ct_book_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id VARCHAR(64) NOT NULL,
    account_name VARCHAR(128) NOT NULL,
    account_type VARCHAR(16) NOT NULL,
    quote_currency VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_book_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_portfolio_balance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id VARCHAR(64) NOT NULL,
    asset_code VARCHAR(32) NOT NULL,
    available_balance DECIMAL(30,12) NOT NULL,
    frozen_balance DECIMAL(30,12) NOT NULL,
    total_balance DECIMAL(30,12) NOT NULL,
    version INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_balance_asset (account_id, asset_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_book_position (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    quantity DECIMAL(30,12) NOT NULL,
    available_quantity DECIMAL(30,12) NOT NULL,
    average_cost DECIMAL(30,12) DEFAULT NULL,
    cost_amount DECIMAL(30,12) NOT NULL,
    market_price DECIMAL(30,12) DEFAULT NULL,
    market_value DECIMAL(30,12) DEFAULT NULL,
    unrealized_pnl DECIMAL(30,12) DEFAULT NULL,
    realized_pnl DECIMAL(30,12) NOT NULL,
    weight DECIMAL(20,12) DEFAULT NULL,
    version INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_book_position (account_id, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_position_lot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lot_id CHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(8) NOT NULL,
    quantity DECIMAL(30,12) NOT NULL,
    price DECIMAL(30,12) NOT NULL,
    fee DECIMAL(30,12) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lot (lot_id),
    UNIQUE KEY uk_lot_request (account_id, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_portfolio_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ledger_id CHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) DEFAULT NULL,
    price DECIMAL(30,12) DEFAULT NULL,
    fee DECIMAL(30,12) NOT NULL,
    cash_delta DECIMAL(30,12) NOT NULL,
    balance_after DECIMAL(30,12) NOT NULL,
    quantity_after DECIMAL(30,12) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger (ledger_id),
    UNIQUE KEY uk_ledger_request (account_id, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
