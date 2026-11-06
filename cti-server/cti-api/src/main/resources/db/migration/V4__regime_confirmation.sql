ALTER TABLE ct_market_regime
    ADD COLUMN raw_regime VARCHAR(32) NOT NULL DEFAULT 'NEUTRAL' AFTER regime,
    ADD COLUMN confirm_count INT NOT NULL DEFAULT 0 AFTER raw_regime,
    ADD COLUMN risk_override VARCHAR(32) NULL AFTER confirm_count;
