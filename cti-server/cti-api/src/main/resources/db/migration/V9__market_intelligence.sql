CREATE TABLE ct_derivatives_metric (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(32) NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    interval_code VARCHAR(16) NOT NULL,
    event_time_ms BIGINT NOT NULL,
    collect_time_ms BIGINT NOT NULL,
    metric_value DECIMAL(30,12) DEFAULT NULL,
    provider_code VARCHAR(64) NOT NULL,
    data_status VARCHAR(32) NOT NULL,
    raw_data JSON DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_deriv_metric (symbol, metric_code, interval_code, event_time_ms, provider_code),
    KEY idx_deriv_event_time (event_time_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_market_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    snapshot_time_ms BIGINT NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    metric_value DECIMAL(30,12) DEFAULT NULL,
    metric_unit VARCHAR(32) DEFAULT NULL,
    provider_code VARCHAR(64) NOT NULL,
    data_status VARCHAR(32) NOT NULL,
    raw_data JSON DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_metric_time_provider (metric_code, snapshot_time_ms, provider_code),
    KEY idx_snapshot_time (snapshot_time_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
