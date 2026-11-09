CREATE TABLE ct_strategy_registry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    strategy_code VARCHAR(64) NOT NULL,
    strategy_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_strategy_code (strategy_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_strategy_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    strategy_code VARCHAR(64) NOT NULL,
    strategy_version VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_strategy_version (strategy_code, strategy_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_strategy_parameter (
    id BIGINT NOT NULL AUTO_INCREMENT,
    strategy_code VARCHAR(64) NOT NULL,
    strategy_version VARCHAR(32) NOT NULL,
    parameter_hash CHAR(64) NOT NULL,
    parameter_json JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_strategy_parameter (strategy_code, strategy_version, parameter_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_strategy_signal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    signal_id CHAR(64) NOT NULL,
    strategy_code VARCHAR(64) NOT NULL,
    strategy_version VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    signal_time VARCHAR(40) NOT NULL,
    signal_name VARCHAR(16) NOT NULL,
    score DECIMAL(30,12) DEFAULT NULL,
    target_weight DECIMAL(20,12) DEFAULT NULL,
    stop_price DECIMAL(30,12) DEFAULT NULL,
    explanation_json JSON NOT NULL,
    parameter_hash CHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_strategy_signal (signal_id),
    UNIQUE KEY uk_strategy_signal_bar (strategy_code, strategy_version, symbol, signal_time, parameter_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_strategy_execution (
    id BIGINT NOT NULL AUTO_INCREMENT,
    execution_id CHAR(64) NOT NULL,
    strategy_code VARCHAR(64) NOT NULL,
    strategy_version VARCHAR(32) NOT NULL,
    parameter_hash CHAR(64) NOT NULL,
    result_json JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_strategy_execution (execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ct_strategy_registry (strategy_code, strategy_name, status) VALUES
    ('EMA_TREND', 'EMA 趋势', 'ACTIVE'),
    ('DONCHIAN_BREAKOUT', '唐奇安突破', 'ACTIVE'),
    ('MOMENTUM', '动量', 'ACTIVE'),
    ('RELATIVE_STRENGTH', '相对强弱', 'ACTIVE'),
    ('MEAN_REVERSION', '均值回归', 'ACTIVE');

INSERT INTO ct_strategy_version (strategy_code, strategy_version) VALUES
    ('EMA_TREND', '1.0.0'),
    ('DONCHIAN_BREAKOUT', 'n20'),
    ('DONCHIAN_BREAKOUT', 'n55'),
    ('MOMENTUM', '1.0.0'),
    ('RELATIVE_STRENGTH', '1.0.0'),
    ('MEAN_REVERSION', '1.0.0');

INSERT INTO ct_strategy_parameter (strategy_code, strategy_version, parameter_hash, parameter_json)
SELECT 'EMA_TREND', '1.0.0', SHA2(CAST(parameter_json AS CHAR), 256), parameter_json
FROM (SELECT JSON_OBJECT('emaFast', 20, 'emaMid', 50, 'emaSlow', 200, 'adxMinimum', NULL) AS parameter_json) seed
UNION ALL
SELECT 'DONCHIAN_BREAKOUT', 'n20', SHA2(CAST(parameter_json AS CHAR), 256), parameter_json
FROM (SELECT JSON_OBJECT('lookback', 20) AS parameter_json) seed
UNION ALL
SELECT 'DONCHIAN_BREAKOUT', 'n55', SHA2(CAST(parameter_json AS CHAR), 256), parameter_json
FROM (SELECT JSON_OBJECT('lookback', 55) AS parameter_json) seed
UNION ALL
SELECT 'MOMENTUM', '1.0.0', SHA2(CAST(parameter_json AS CHAR), 256), parameter_json
FROM (SELECT JSON_OBJECT('periods', JSON_ARRAY(7, 14, 30), 'leadPeriod', 30) AS parameter_json) seed
UNION ALL
SELECT 'RELATIVE_STRENGTH', '1.0.0', SHA2(CAST(parameter_json AS CHAR), 256), parameter_json
FROM (SELECT JSON_OBJECT('lookback', 30, 'holdings', 1, 'requireUptrend', TRUE, 'emaPeriod', 20) AS parameter_json) seed
UNION ALL
SELECT 'MEAN_REVERSION', '1.0.0', SHA2(CAST(parameter_json AS CHAR), 256), parameter_json
FROM (SELECT JSON_OBJECT('window', 20, 'entryZ', NULL, 'exitZ', NULL) AS parameter_json) seed;
