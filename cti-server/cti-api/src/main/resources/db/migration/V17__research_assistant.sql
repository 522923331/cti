CREATE TABLE ct_ai_research_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id CHAR(64) NOT NULL,
    question VARCHAR(1000) NOT NULL,
    intent_name VARCHAR(32) NOT NULL,
    explanation VARCHAR(64) NOT NULL,
    answer_json JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_ai_research_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    evidence_id CHAR(64) NOT NULL,
    session_id CHAR(64) NOT NULL,
    source_table VARCHAR(64) NOT NULL,
    data_range VARCHAR(128) DEFAULT NULL,
    metric_version VARCHAR(64) DEFAULT NULL,
    strategy_version VARCHAR(32) DEFAULT NULL,
    fact_json JSON NOT NULL,
    missing_json JSON NOT NULL,
    queried_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_evidence (evidence_id),
    KEY idx_ai_evidence_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
