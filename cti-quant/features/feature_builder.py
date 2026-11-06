"""Assemble one daily feature row per closed bar. Calculations never read a later bar."""

from dataclasses import dataclass
from datetime import date

from indicators.derivatives import funding_zscore, oi_change
from indicators.momentum import price_return, rsi
from indicators.trend import ema
from indicators.volatility import adx, atr, atr_percent, realized_volatility
from indicators.volume import rolling_sum, spot_delta, spot_delta_ratio, volume_ratio

DAY_MS = 86_400_000
FUNDING_WINDOW = 90


@dataclass(frozen=True)
class DailyBar:
    trade_date: date
    open_time: int
    high: float
    low: float
    close: float
    quote_volume: float | None
    taker_buy_quote: float | None


@dataclass(frozen=True)
class DerivativePoint:
    trade_date: date
    open_interest_value: float | None
    funding_rate: float | None


@dataclass(frozen=True)
class DailyFeature:
    symbol: str
    trade_date: date
    ema20: float | None
    ema50: float | None
    ema200: float | None
    return_7d: float | None
    return_14d: float | None
    return_30d: float | None
    return_90d: float | None
    atr14: float | None
    atr_pct: float | None
    adx14: float | None
    rsi14: float | None
    realized_volatility_20d: float | None
    volume_ratio_20d: float | None
    spot_delta: float | None
    spot_delta_ratio: float | None
    spot_delta_7d: float | None
    oi_change_1d: float | None
    oi_change_7d: float | None
    funding_rate: float | None
    funding_zscore: float | None
    feature_version: str
    data_quality_status: str


def split_sessions(bars: list[DailyBar]) -> list[list[DailyBar]]:
    """A missing day starts a new warmup. Adjacent bars are exactly one day apart."""
    if not bars:
        return []
    sessions: list[list[DailyBar]] = [[bars[0]]]
    for bar in bars[1:]:
        previous = sessions[-1][-1]
        if bar.open_time == previous.open_time:
            raise ValueError(f"{bar.trade_date} 日线重复")
        if bar.open_time < previous.open_time:
            raise ValueError(f"{bar.trade_date} 日线时间倒退")
        if bar.open_time - previous.open_time == DAY_MS:
            sessions[-1].append(bar)
        else:
            sessions.append([bar])
    return sessions


def _collapse(points: list[DerivativePoint]) -> dict[date, DerivativePoint]:
    by_date: dict[date, DerivativePoint] = {}
    for point in points:
        by_date[point.trade_date] = point
    return by_date


def build_daily_features(bars: list[DailyBar], derivatives: list[DerivativePoint], symbol: str,
                         data_quality_status: str, feature_version: str) -> list[DailyFeature]:
    if not data_quality_status:
        raise ValueError("特征必须带数据质量状态")
    rows: list[DailyFeature] = []
    for session in split_sessions(bars):
        rows.extend(_price_rows(session, symbol, data_quality_status, feature_version))
    _attach_derivatives(rows, derivatives)
    return rows


def _price_rows(session: list[DailyBar], symbol: str, status: str, version: str) -> list[DailyFeature]:
    closes = [bar.close for bar in session]
    highs = [bar.high for bar in session]
    lows = [bar.low for bar in session]
    quotes = [bar.quote_volume for bar in session]
    deltas = [spot_delta(bar.taker_buy_quote, bar.quote_volume) for bar in session]
    ratios = [spot_delta_ratio(delta, bar.quote_volume) for delta, bar in zip(deltas, session, strict=True)]
    atr_values = atr(highs, lows, closes, 14)
    ema20 = ema(closes, 20)
    ema50 = ema(closes, 50)
    ema200 = ema(closes, 200)
    seven_day = rolling_sum(deltas, 7)
    fields = {
        "ema20": ema20,
        "ema50": ema50,
        "ema200": ema200,
        "return_7d": price_return(closes, 7),
        "return_14d": price_return(closes, 14),
        "return_30d": price_return(closes, 30),
        "return_90d": price_return(closes, 90),
        "atr14": atr_values,
        "atr_pct": atr_percent(atr_values, closes),
        "adx14": adx(highs, lows, closes, 14),
        "rsi14": rsi(closes, 14),
        "realized_volatility_20d": realized_volatility(closes, 20),
        "volume_ratio_20d": volume_ratio(quotes, 20),
        "spot_delta": deltas,
        "spot_delta_ratio": ratios,
        "spot_delta_7d": seven_day,
    }
    rows: list[DailyFeature] = []
    for index, bar in enumerate(session):
        rows.append(DailyFeature(
            symbol=symbol,
            trade_date=bar.trade_date,
            ema20=fields["ema20"][index],
            ema50=fields["ema50"][index],
            ema200=fields["ema200"][index],
            return_7d=fields["return_7d"][index],
            return_14d=fields["return_14d"][index],
            return_30d=fields["return_30d"][index],
            return_90d=fields["return_90d"][index],
            atr14=fields["atr14"][index],
            atr_pct=fields["atr_pct"][index],
            adx14=fields["adx14"][index],
            rsi14=fields["rsi14"][index],
            realized_volatility_20d=fields["realized_volatility_20d"][index],
            volume_ratio_20d=fields["volume_ratio_20d"][index],
            spot_delta=fields["spot_delta"][index],
            spot_delta_ratio=fields["spot_delta_ratio"][index],
            spot_delta_7d=fields["spot_delta_7d"][index],
            oi_change_1d=None,
            oi_change_7d=None,
            funding_rate=None,
            funding_zscore=None,
            feature_version=version,
            data_quality_status=status,
        ))
    return rows


def _attach_derivatives(rows: list[DailyFeature], points: list[DerivativePoint]) -> None:
    if not rows:
        return
    by_date = _collapse(points)
    dates = [row.trade_date for row in rows]
    oi_values = {day: point.open_interest_value for day, point in by_date.items() if point.open_interest_value is not None}
    funding_values = {day: point.funding_rate for day, point in by_date.items() if point.funding_rate is not None}
    changes_1d = oi_change(oi_values, dates, 1)
    changes_7d = oi_change(oi_values, dates, 7)
    funding_z = funding_zscore(funding_values, dates, FUNDING_WINDOW)
    attached: list[DailyFeature] = []
    for index, row in enumerate(rows):
        point = by_date.get(row.trade_date)
        attached.append(DailyFeature(
            symbol=row.symbol,
            trade_date=row.trade_date,
            ema20=row.ema20,
            ema50=row.ema50,
            ema200=row.ema200,
            return_7d=row.return_7d,
            return_14d=row.return_14d,
            return_30d=row.return_30d,
            return_90d=row.return_90d,
            atr14=row.atr14,
            atr_pct=row.atr_pct,
            adx14=row.adx14,
            rsi14=row.rsi14,
            realized_volatility_20d=row.realized_volatility_20d,
            volume_ratio_20d=row.volume_ratio_20d,
            spot_delta=row.spot_delta,
            spot_delta_ratio=row.spot_delta_ratio,
            spot_delta_7d=row.spot_delta_7d,
            oi_change_1d=changes_1d[index],
            oi_change_7d=changes_7d[index],
            funding_rate=None if point is None else point.funding_rate,
            funding_zscore=funding_z[index],
            feature_version=row.feature_version,
            data_quality_status=row.data_quality_status,
        ))
    rows.clear()
    rows.extend(attached)
