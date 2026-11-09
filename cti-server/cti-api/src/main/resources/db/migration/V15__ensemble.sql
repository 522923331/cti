CREATE TABLE ct_regime_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    history_id CHAR(64) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    trade_date DATE DEFAULT NULL,
    direction_regime VARCHAR(16) DEFAULT NULL,
    volatility_regime VARCHAR(16) DEFAULT NULL,
    model_version VARCHAR(32) NOT NULL,
    inputs_json JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_regime_history (history_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_ensemble_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    config_id CHAR(64) NOT NULL,
    weight_json JSON NOT NULL,
    config_json JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ensemble_config (config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_ensemble_result (
    id BIGINT NOT NULL AUTO_INCREMENT,
    result_id CHAR(64) NOT NULL,
    config_id CHAR(64) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    trade_date DATE DEFAULT NULL,
    ensemble_score DECIMAL(30,12) DEFAULT NULL,
    confidence DECIMAL(20,12) NOT NULL,
    signal_name VARCHAR(16) NOT NULL,
    direction_regime VARCHAR(16) DEFAULT NULL,
    volatility_regime VARCHAR(16) DEFAULT NULL,
    contribution_json JSON NOT NULL,
    model_version VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ensemble_result (result_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
