"""Shared score types. Indicator math stays in the indicator modules."""

from dataclasses import dataclass
from datetime import date
from decimal import Decimal


@dataclass(frozen=True)
class DayInput:
    trade_date: date
    high: float
    low: float
    close: float
    quote_volume: float | None
    ema20: float | None
    ema50: float | None
    ema200: float | None
    adx14: float | None
    return_7d: float | None
    return_14d: float | None
    return_30d: float | None
    return_90d: float | None
    realized_volatility_20d: float | None
    volume_ratio_20d: float | None
    spot_delta: float | None
    spot_delta_ratio: float | None
    oi_change_7d: float | None
    funding_zscore: float | None
    data_quality_status: str


@dataclass(frozen=True)
class RegimeRow:
    trade_date: date
    regime: str
    raw_regime: str
    confirm_count: int
    market_score: Decimal | None
    btc_trend_score: Decimal | None
    market_breadth: Decimal | None
    btc_volatility: Decimal | None
    total_exposure_limit: Decimal
    risk_override: str | None
    reason: dict


@dataclass(frozen=True)
class ScoreRow:
    symbol: str
    trade_date: date
    market_score: Decimal | None
    trend_score: Decimal | None
    flow_score: Decimal | None
    derivatives_score: Decimal | None
    sentiment_score: Decimal | None
    total_score: Decimal | None
    signal_level: str | None
    reason: dict
    risk: dict
    feature_version: str
    strategy_version: str
