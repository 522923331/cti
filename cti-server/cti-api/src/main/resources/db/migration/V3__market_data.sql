ALTER TABLE ct_kline
    ADD COLUMN archived TINYINT NOT NULL DEFAULT 0 AFTER is_closed;

CREATE TABLE ct_kline_revision (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exchange_code VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    interval_code VARCHAR(8) NOT NULL,
    open_time BIGINT NOT NULL,
    old_close_price DECIMAL(30,12) NOT NULL,
    new_close_price DECIMAL(30,12) NOT NULL,
    old_quote_volume DECIMAL(30,12),
    new_quote_volume DECIMAL(30,12),
    revised_at DATETIME NOT NULL,
    KEY idx_revision_symbol (symbol, open_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_data_quality_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exchange_code VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    interval_code VARCHAR(8) NOT NULL,
    check_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    missing_count INT NOT NULL,
    duplicate_count INT NOT NULL,
    unclosed_count INT NOT NULL,
    anomaly_count INT NOT NULL,
    detail_json JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_quality (exchange_code, symbol, interval_code, check_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
