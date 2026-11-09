CREATE TABLE ct_news_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id CHAR(64) NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    title VARCHAR(512) NOT NULL,
    source_url VARCHAR(1024) NOT NULL,
    source_name VARCHAR(128) DEFAULT NULL,
    url_hash CHAR(64) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    category VARCHAR(32) DEFAULT NULL,
    sentiment VARCHAR(32) DEFAULT NULL,
    impact_level VARCHAR(32) DEFAULT NULL,
    confidence DECIMAL(8,6) DEFAULT NULL,
    summary TEXT,
    published_at_ms BIGINT DEFAULT NULL,
    event_at_ms BIGINT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_id (event_id),
    UNIQUE KEY uk_provider_hash (provider_code, content_hash),
    UNIQUE KEY uk_provider_url (provider_code, url_hash),
    KEY idx_published_at (published_at_ms),
    KEY idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_news_asset_rela (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id CHAR(64) NOT NULL,
    asset_code VARCHAR(32) NOT NULL,
    relevance_score DECIMAL(8,6) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_asset (event_id, asset_code),
    KEY idx_asset_code (asset_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_onchain_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    asset_code VARCHAR(32) NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    interval_code VARCHAR(16) NOT NULL,
    event_time_ms BIGINT NOT NULL,
    collect_time_ms BIGINT NOT NULL,
    metric_value DECIMAL(30,12) DEFAULT NULL,
    metric_unit VARCHAR(32) DEFAULT NULL,
    provider_code VARCHAR(64) NOT NULL,
    data_status VARCHAR(32) NOT NULL,
    is_delayed TINYINT NOT NULL DEFAULT 0,
    raw_data JSON DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_onchain_metric (asset_code, metric_code, interval_code, event_time_ms, provider_code),
    KEY idx_onchain_event_time (event_time_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
