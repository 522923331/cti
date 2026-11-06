CREATE TABLE ct_daily_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trade_date DATE NOT NULL,
    strategy_version VARCHAR(32) NOT NULL,
    feature_version VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    report_json JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_daily_report (trade_date, strategy_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
