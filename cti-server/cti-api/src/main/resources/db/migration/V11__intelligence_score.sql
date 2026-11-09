CREATE TABLE ct_intelligence_score (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(32) NOT NULL,
    trade_date DATE NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    feature_version VARCHAR(32) NOT NULL,
    trend_score DECIMAL(10,4) DEFAULT NULL,
    risk_score DECIMAL(10,4) DEFAULT NULL,
    sentiment_score DECIMAL(10,4) DEFAULT NULL,
    data_confidence DECIMAL(8,6) DEFAULT NULL,
    risk_level VARCHAR(32) NOT NULL,
    signal_type VARCHAR(32) NOT NULL,
    data_status VARCHAR(32) NOT NULL,
    reason_json JSON DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_intel_score (symbol, trade_date, model_version),
    KEY idx_intel_score_date (trade_date, trend_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_intelligence_signal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    signal_id VARCHAR(128) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    trade_date DATE NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    signal_type VARCHAR(32) NOT NULL,
    data_confidence DECIMAL(8,6) DEFAULT NULL,
    reason_json JSON DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_intel_signal (signal_id),
    UNIQUE KEY uk_intel_signal_day (symbol, trade_date, model_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_alert_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rule_code VARCHAR(64) NOT NULL,
    enabled TINYINT NOT NULL,
    threshold_value DECIMAL(30,12) DEFAULT NULL,
    lookback_window VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    cooldown_minutes INT NOT NULL,
    target_scope VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_alert_rule (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_market_alert (
    id BIGINT NOT NULL AUTO_INCREMENT,
    alert_id VARCHAR(64) NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_code VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    alert_status VARCHAR(32) NOT NULL,
    trigger_value DECIMAL(30,12) DEFAULT NULL,
    threshold_value DECIMAL(30,12) DEFAULT NULL,
    explanation JSON DEFAULT NULL,
    triggered_at_ms BIGINT NOT NULL,
    resolved_at_ms BIGINT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_alert_id (alert_id),
    KEY idx_alert_target (rule_code, target_code, alert_status),
    KEY idx_alert_triggered (triggered_at_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_intelligence_report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trade_date DATE NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    report_json JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_intel_report (trade_date, model_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ct_alert_rule
    (rule_code, enabled, threshold_value, lookback_window, severity, cooldown_minutes, target_scope)
VALUES
    ('BTC_TREND_BREAKDOWN', 1, NULL, '1d', 'HIGH', 1440, 'BTC'),
    ('BTC_VOLATILITY_SPIKE', 1, 0.800000000000, '20d', 'HIGH', 1440, 'BTC'),
    ('ALT_BREADTH_COLLAPSE', 1, 0.150000000000, '1d', 'MEDIUM', 1440, 'MARKET'),
    ('ASSET_VOLUME_SPIKE', 1, 2.500000000000, '20d', 'MEDIUM', 1440, 'SYMBOL'),
    ('ASSET_RELATIVE_STRENGTH_SPIKE', 1, 90.000000000000, '1d', 'LOW', 1440, 'SYMBOL'),
    ('OI_SPIKE', 1, 0.200000000000, '4h', 'MEDIUM', 1440, 'SYMBOL'),
    ('FUNDING_EXTREME', 1, 2.000000000000, '20', 'HIGH', 1440, 'SYMBOL'),
    ('ONCHAIN_ANOMALY', 0, NULL, '1d', 'MEDIUM', 1440, 'ASSET'),
    ('HIGH_IMPACT_NEWS', 1, NULL, '1d', 'HIGH', 1440, 'EVENT');
