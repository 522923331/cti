ALTER TABLE ct_backtest_result
    ADD COLUMN report_json JSON NULL AFTER excess_return;

CREATE TABLE ct_backtest_trade (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id VARCHAR(64) NOT NULL,
    trade_date DATE NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(16) NOT NULL,
    quantity DECIMAL(30,12) NOT NULL,
    open_price DECIMAL(30,12) NOT NULL,
    fill_price DECIMAL(30,12) NOT NULL,
    fee DECIMAL(30,12) NOT NULL,
    slippage DECIMAL(30,12) NOT NULL,
    pnl DECIMAL(30,12) NULL,
    opened_on DATE NULL,
    holding_days INT NULL,
    KEY idx_backtest_trade (run_id, trade_date, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ct_backtest_equity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id VARCHAR(64) NOT NULL,
    trade_date DATE NOT NULL,
    cash_balance DECIMAL(30,12) NOT NULL,
    equity DECIMAL(30,12) NOT NULL,
    peak_equity DECIMAL(30,12) NOT NULL,
    drawdown DECIMAL(20,12) NOT NULL,
    exposure DECIMAL(20,12) NOT NULL,
    UNIQUE KEY uk_backtest_equity (run_id, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
