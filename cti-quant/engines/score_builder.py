"""Build regime rows and per-symbol scores from closed daily inputs."""

from datetime import date

from engines.flow_engine import derivatives_score, flow_score, total_score, volume_score
from engines.levels import signal_level
from engines.market_regime_engine import (
    breadth_for,
    btc_trend_score,
    confirm_regimes,
    emergency,
    exposure_limit,
    market_score,
    raw_regime,
    volatility_penalty,
)
from engines.models import DayInput, RegimeRow, ScoreRow
from engines.series import daily_returns, dated_normalized, dated_zscore, rolling_quote_ratio
from engines.trend_engine import adx_score, ema_score, momentum_score, momentum_series, structure_score, trend_score
from engines.weights import ratio_decimal, score_decimal, text, volatility_decimal

BENCHMARK = "BTCUSDT"


def evaluate(histories: dict[str, list[DayInput]], config: dict, feature_version: str, strategy_version: str):
    if config.get("strategyVersion") != strategy_version:
        raise ValueError("策略配置版本和请求版本不一致")
    if config.get("sentiment", {}).get("mode") != "ABSENT":
        raise ValueError("V1 只接受 sentiment.mode=ABSENT")
    ordered = {symbol: _ordered(symbol, bars) for symbol, bars in histories.items()}
    dates = sorted({bar.trade_date for bars in ordered.values() for bar in bars})
    by_symbol = {symbol: {bar.trade_date: bar for bar in bars} for symbol, bars in ordered.items()}
    breadth = {}
    valid_assets = {}
    for day in dates:
        present = [bars[day] for bars in by_symbol.values() if day in bars]
        breadth[day], valid_assets[day] = breadth_for(present)

    btc_bars = ordered.get(BENCHMARK, [])
    btc_dates = [bar.trade_date for bar in btc_bars]
    btc_index = {bar.trade_date: index for index, bar in enumerate(btc_bars)}
    window = int(config["indicators"]["zscoreWindow"])
    btc_vol_z = dated_zscore(btc_dates, [bar.realized_volatility_20d for bar in btc_bars], window)
    btc_vol_penalty = [volatility_penalty(value, config) for value in btc_vol_z]
    btc_trend = [btc_trend_score(bar.close, bar.ema50, bar.ema200) for bar in btc_bars]
    btc_return = daily_returns(btc_dates, [bar.close for bar in btc_bars])

    market_by_day = {}
    market_meta = {}
    for day in dates:
        index = btc_index.get(day)
        trend = score_decimal(None if index is None else btc_trend[index])
        penalty = score_decimal(None if index is None else btc_vol_penalty[index])
        breadth_score = None if breadth[day] is None else score_decimal(200 * breadth[day] - 100)
        total, info = market_score(trend, breadth_score, penalty, config)
        market_by_day[day] = total
        market_meta[day] = {
            "trend": trend,
            "penalty": penalty,
            "breadthScore": breadth_score,
            "weights": info,
            "volatility": None if index is None else btc_bars[index].realized_volatility_20d,
            "dailyReturn": None if index is None else btc_return[index],
        }

    raws = []
    for day in dates:
        index = btc_index.get(day)
        raws.append(raw_regime(None if index is None else btc_bars[index], breadth[day], config))
    confirmed, streaks = confirm_regimes(raws, int(config["regime"]["confirmDays"]))

    regimes: list[RegimeRow] = []
    previous_breadth = None
    regime_by_day = {}
    for index, day in enumerate(dates):
        meta = market_meta[day]
        is_emergency = emergency(meta["dailyReturn"], previous_breadth, breadth[day], config)
        limit = exposure_limit(confirmed[index], is_emergency, config)
        override = "EMERGENCY" if is_emergency else None
        reason = {
            "rawRegime": raws[index],
            "confirmCount": streaks[index],
            "riskOverride": override,
            "breadth": text(ratio_decimal(breadth[day])),
            "validAssets": valid_assets[day],
            "btcTrend": text(meta["trend"]),
            "btcVolatilityScore": text(meta["penalty"]),
            "btcVolatility": text(volatility_decimal(meta["volatility"])),
            "btcDailyReturn": text(ratio_decimal(meta["dailyReturn"], scale=_eight())),
            "marketScore": text(market_by_day[day]),
            "marketWeights": meta["weights"],
            "sentiment": "ABSENT",
            "universe": sorted(ordered),
        }
        row = RegimeRow(
            day, confirmed[index], raws[index], streaks[index], market_by_day[day], meta["trend"],
            ratio_decimal(breadth[day]), volatility_decimal(meta["volatility"]), limit, override, reason,
        )
        regimes.append(row)
        regime_by_day[day] = row
        previous_breadth = breadth[day]

    scores: list[ScoreRow] = []
    for symbol in sorted(ordered):
        scores.extend(_symbol_scores(symbol, ordered[symbol], config, feature_version, strategy_version, market_by_day, regime_by_day))
    return regimes, scores


def _symbol_scores(symbol: str, bars: list[DayInput], config: dict, feature_version: str, strategy_version: str,
                   market_by_day: dict, regime_by_day: dict) -> list[ScoreRow]:
    dates = [bar.trade_date for bar in bars]
    window = int(config["indicators"]["zscoreWindow"])
    returns = daily_returns(dates, [bar.close for bar in bars])
    return_map = {
        "return7d": [bar.return_7d for bar in bars],
        "return14d": [bar.return_14d for bar in bars],
        "return30d": [bar.return_30d for bar in bars],
        "return90d": [bar.return_90d for bar in bars],
    }
    spot_norm = dated_normalized(dates, [bar.spot_delta_ratio for bar in bars], window)
    seven_ratio = rolling_quote_ratio(dates, [bar.spot_delta for bar in bars], [bar.quote_volume for bar in bars], 7)
    seven_norm = dated_normalized(dates, seven_ratio, window)
    oi_z = dated_zscore(dates, [bar.oi_change_7d for bar in bars], window)
    momentum = momentum_series(dates, return_map, config)
    highs = [bar.high for bar in bars]
    lows = [bar.low for bar in bars]
    indicators = config["indicators"]
    rows: list[ScoreRow] = []
    for index, bar in enumerate(bars):
        ema = ema_score(bar.ema20, bar.ema50, bar.ema200)
        momentum_value, momentum_info = momentum_score(momentum, config, index)
        adx = adx_score(bar.adx14)
        structure, structure_info = structure_score(
            highs, lows, dates, index,
            int(indicators["swingLeft"]), int(indicators["swingRight"]),
            int(indicators["swingLookbackBars"]), int(indicators["minBarsForStructure"]),
        )
        trend, trend_info = trend_score(ema, momentum_value, adx, structure, config)
        volume = volume_score(bar.volume_ratio_20d, returns[index], config)
        spot = score_decimal(spot_norm[index])
        spot7 = score_decimal(seven_norm[index])
        flow, flow_info = flow_score(volume, spot, spot7, config)
        derivative, derivative_info = derivatives_score(bar.funding_zscore, bar.oi_change_7d, oi_z[index], bar.return_7d, config)
        derivative_score = score_decimal(derivative)
        market = market_by_day[bar.trade_date]
        total, total_info = total_score(market, trend, flow, derivative_score, config)
        regime = regime_by_day[bar.trade_date]
        reason = {
            "sentiment": "ABSENT",
            "quality": bar.data_quality_status,
            "ema": text(score_decimal(ema)),
            "momentum": text(momentum_value),
            "momentumDetail": momentum_info,
            "adx": text(score_decimal(adx)),
            "structure": text(score_decimal(structure)),
            "structureDetail": structure_info,
            "volume": text(score_decimal(volume)),
            "spotDelta": text(spot),
            "spotDelta7d": text(spot7),
            "trendWeights": trend_info,
            "flowWeights": flow_info,
            "derivatives": derivative_info,
            "totalWeights": total_info,
        }
        risk = {
            "regime": regime.regime,
            "rawRegime": regime.raw_regime,
            "exposureLimit": text(regime.total_exposure_limit),
            "riskOverride": regime.risk_override,
        }
        rows.append(ScoreRow(
            symbol, bar.trade_date, market, trend, flow, derivative_score, None, total, signal_level(total),
            reason, risk, feature_version, strategy_version,
        ))
    return rows


def _ordered(symbol: str, bars: list[DayInput]) -> list[DayInput]:
    ordered = sorted(bars, key=lambda bar: bar.trade_date)
    previous: date | None = None
    for bar in ordered:
        if previous is not None and bar.trade_date <= previous:
            raise ValueError(f"{symbol} 日期重复或倒退")
        previous = bar.trade_date
    return ordered


def _eight():
    from decimal import Decimal
    return Decimal("0.00000001")
