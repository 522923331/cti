"""Closed-bar inputs for one decision day. Prices stay floats; money is converted later."""

from dataclasses import dataclass
from datetime import date
from decimal import Decimal


@dataclass(frozen=True)
class SignalDay:
    trade_date: date
    open: float
    high: float
    low: float
    close: float
    ema20: float | None
    ema50: float | None
    atr14: float | None
    ema50_slope: float | None
    volume_ratio_20d: float | None
    prior_high_20: float | None
    spot_delta_7d_score: float | None
    funding_zscore: float | None
    oi_change_7d: float | None
    return_7d: float | None
    quality: str
    trend_score: float | None
    flow_score: float | None
    total_score: float | None
    four_low: float | None = None
    four_close: float | None = None
    four_ema20: float | None = None
    four_atr14: float | None = None


@dataclass(frozen=True)
class RegimeView:
    regime: str
    exposure_limit: float | None
    risk_override: str | None


@dataclass
class EmittedSignal:
    signal_id: str
    symbol: str
    signal_time: str
    trade_date: date
    signal_type: str
    signal_status: str
    score: Decimal | None
    entry_price: Decimal | None
    stop_price: Decimal | None
    suggested_position: Decimal | None
    risk_amount: Decimal | None
    reason: dict
    invalidation: dict | None
    feature_version: str
    strategy_version: str


@dataclass
class EquityPoint:
    trade_date: date
    cash: Decimal
    equity: Decimal
    peak_equity: Decimal
    drawdown: Decimal
    exposure: Decimal


@dataclass
class OpenPosition:
    symbol: str
    quantity: Decimal
    entry_price: Decimal
    stop_price: Decimal
    highest_close: Decimal
    opened_on: date
    updated_on: date
    market_value: Decimal
    atr_at_entry: Decimal
    fee_paid: Decimal = Decimal("0")


@dataclass(frozen=True)
class Fill:
    trade_date: date
    symbol: str
    side: str
    quantity: Decimal
    open_price: Decimal
    fill_price: Decimal
    fee: Decimal
    slippage: Decimal
    pnl: Decimal | None
    opened_on: date | None
