CREATE TABLE ct_optimization_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id CHAR(64) NOT NULL,
    grade VARCHAR(32) NOT NULL,
    passed TINYINT NOT NULL,
    selected_json JSON DEFAULT NULL,
    fold_json JSON NOT NULL,
    constraint_json JSON NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_optimization_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_optimization_result (
    id BIGINT NOT NULL AUTO_INCREMENT,
    result_id CHAR(64) NOT NULL,
    run_id CHAR(64) NOT NULL,
    parameter_json JSON NOT NULL,
    eligible TINYINT NOT NULL,
    selected TINYINT NOT NULL,
    validation_json JSON NOT NULL,
    test_json JSON DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_optimization_result (result_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_model_health_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    snapshot_id CHAR(64) NOT NULL,
    strategy_code VARCHAR(64) NOT NULL,
    strategy_version VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    action VARCHAR(32) NOT NULL,
    parameter_change JSON DEFAULT NULL,
    metrics_json JSON NOT NULL,
    rules_json JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_health_snapshot (snapshot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
