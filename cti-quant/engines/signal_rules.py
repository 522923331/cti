"""Entry and exit conditions. A missing input fails the condition; it is never treated as zero."""

from datetime import date, timedelta

from engines.signal_models import RegimeView, SignalDay

PULLBACK = "TREND_PULLBACK_V1"
BREAKOUT = "TREND_BREAKOUT_V1"


def prior_highs(dates: list[date], highs: list[float], lookback: int) -> list[float | None]:
    """Highest high of the previous `lookback` calendar days, excluding today."""
    out: list[float | None] = [None] * len(dates)
    if lookback < 1:
        raise ValueError("突破回看至少为 1")
    for index in range(lookback, len(dates)):
        if dates[index] - dates[index - lookback] != timedelta(days=lookback):
            continue
        window = highs[index - lookback:index]
        out[index] = max(window)
    return out


def ema50_slopes(dates: list[date], values: list[float | None], lookback: int) -> list[float | None]:
    out: list[float | None] = [None] * len(dates)
    for index in range(lookback, len(dates)):
        if dates[index] - dates[index - lookback] != timedelta(days=lookback):
            continue
        previous = values[index - lookback]
        current = values[index]
        if previous is None or current is None or previous <= 0 or current <= 0:
            continue
        out[index] = current / previous - 1
    return out


def daily_pullback(day: SignalDay, lower: float, upper: float) -> bool:
    if day.ema20 is None or day.atr14 is None or day.atr14 < 0:
        return False
    floor = day.ema20 - lower * day.atr14
    ceiling = day.ema20 + upper * day.atr14
    return floor <= day.low <= ceiling


def four_hour_pullback(day: SignalDay, multiplier: float) -> bool:
    if None in (day.four_low, day.four_close, day.four_ema20, day.four_atr14):
        return False
    return day.four_low <= day.four_ema20 + multiplier * day.four_atr14 and day.four_close > day.four_ema20


def btc_filter(btc: SignalDay | None, regime: RegimeView | None, config: dict) -> tuple[bool, str]:
    """False means a pullback buy is vetoed. Missing derivatives do not veto."""
    if regime is None:
        return False, "NO_REGIME"
    if regime.regime == "BEARISH":
        return False, "BEARISH"
    if btc is None:
        return False, "BTC_MISSING"
    if btc.quality == "INVALID":
        return False, "BTC_INVALID"
    risk = config["derivativesRisk"]
    crowded = (
        btc.funding_zscore is not None
        and btc.oi_change_7d is not None
        and btc.funding_zscore > float(risk["fundingZSoft"])
        and btc.oi_change_7d > float(risk["oiCrowdingChange7d"])
        and btc.return_7d is not None
        and btc.return_7d > 0
    )
    if crowded:
        return False, "BTC_CROWDED"
    return True, "PASS"


def pullback_setup(day: SignalDay, regime: RegimeView | None, btc: SignalDay | None, config: dict) -> tuple[bool, dict]:
    entry = config["entry"]
    allowed, filter_reason = btc_filter(btc, regime, config)
    checks = {
        "regime": regime.regime if regime is not None else None,
        "btcFilter": filter_reason,
        "ema20AboveEma50": day.ema20 is not None and day.ema50 is not None and day.ema20 > day.ema50,
        "ema50Slope": day.ema50_slope,
        "trend": day.trend_score,
        "total": day.total_score,
        "dailyPullback": daily_pullback(day, float(entry["pullbackDailyAtrLower"]), float(entry["pullbackDailyAtrUpper"])),
        "fourHourPullback": four_hour_pullback(day, float(entry["pullbackAtrMultiplier4h"])),
        "spotDelta7d": day.spot_delta_7d_score,
    }
    slope_ok = day.ema50_slope is not None and day.ema50_slope > 0
    spot_ok = day.spot_delta_7d_score is not None and day.spot_delta_7d_score >= float(entry["spotDelta7dMinScore"])
    trend_ok = day.trend_score is not None and day.trend_score >= float(entry["minimumTrendScore"])
    total_ok = day.total_score is not None and day.total_score >= float(entry["pullbackScore"])
    passed = all((
        allowed,
        checks["ema20AboveEma50"],
        slope_ok,
        trend_ok,
        total_ok,
        checks["dailyPullback"],
        checks["fourHourPullback"],
        spot_ok,
        day.quality != "INVALID",
    ))
    checks["passed"] = passed
    return passed, checks


def breakout_setup(day: SignalDay, regime: RegimeView | None, config: dict) -> tuple[bool, dict]:
    entry = config["entry"]
    above = day.prior_high_20 is not None and day.close > day.prior_high_20
    volume_ok = day.volume_ratio_20d is not None and day.volume_ratio_20d >= float(entry["breakoutVolumeRatio"])
    trend_ok = day.trend_score is not None and day.trend_score >= float(entry["breakoutTrendScore"])
    total_ok = day.total_score is not None and day.total_score >= float(entry["breakoutScore"])
    regime_ok = regime is not None and regime.regime != "BEARISH"
    checks = {
        "closeAbovePriorHigh": above,
        "priorHigh": day.prior_high_20,
        "volumeRatio": day.volume_ratio_20d,
        "trend": day.trend_score,
        "total": day.total_score,
        "regime": regime.regime if regime is not None else None,
        "passed": above and volume_ok and trend_ok and total_ok and regime_ok and day.quality != "INVALID",
    }
    return checks["passed"], checks


def watch_setup(day: SignalDay, regime: RegimeView | None, config: dict) -> bool:
    entry = config["entry"]
    return (
        regime is not None
        and regime.regime != "BEARISH"
        and day.quality != "INVALID"
        and day.trend_score is not None
        and day.trend_score >= float(entry["minimumTrendScore"])
        and day.total_score is not None
        and day.total_score >= float(entry["watchTotalScore"])
        and day.ema20 is not None
        and day.ema50 is not None
        and day.ema20 > day.ema50
    )


def exit_reasons(day: SignalDay, regime: RegimeView | None, stop: float, holding_days: int, config: dict) -> tuple[str, list[str]]:
    """Return SELL, REDUCE, or HOLD and the reasons that fired. INVALID quality is not decided here."""
    review = int(config["exit"]["timeReviewDays"])
    sell_below = float(config["exit"]["timeSellBelowScore"])
    reduce_below = float(config["exit"]["timeReduceBelowScore"])
    reasons: list[str] = []
    trend_exit = (
        day.ema50 is not None
        and day.close < day.ema50
        and day.flow_score is not None
        and day.flow_score < 0
    )
    bearish_exit = (
        day.ema20 is not None
        and day.ema50 is not None
        and day.ema20 < day.ema50
        and regime is not None
        and regime.regime == "BEARISH"
    )
    stopped = day.close <= stop
    time_sell = holding_days >= review and day.total_score is not None and day.total_score < sell_below
    if trend_exit:
        reasons.append("TREND_EXIT")
    if bearish_exit:
        reasons.append("BEARISH_EXIT")
    if stopped:
        reasons.append("STOP")
    if time_sell:
        reasons.append("TIME_EXIT")
    if reasons:
        return "SELL", reasons
    trend_reduce = (
        day.ema20 is not None
        and day.ema50 is not None
        and day.close < day.ema20
        and day.flow_score is not None
        and day.flow_score < 0
        and day.close >= day.ema50
    )
    time_reduce = (
        holding_days >= review
        and day.total_score is not None
        and sell_below <= day.total_score < reduce_below
    )
    if trend_reduce:
        reasons.append("TREND_REDUCE")
    if time_reduce:
        reasons.append("TIME_REDUCE")
    if reasons:
        return "REDUCE", reasons
    return "HOLD", ["HOLD"]
