"""Market breadth, BTC trend, volatility penalty, and confirmed regime."""

from decimal import Decimal

from engines.models import DayInput
from engines.weights import config_decimal, text, weighted


def btc_trend_score(close: float | None, ema50: float | None, ema200: float | None) -> int | None:
    if close is None or ema50 is None or ema200 is None:
        return None
    if close > ema200 and ema50 > ema200:
        return 100
    if close > ema200 and ema50 <= ema200:
        return 40
    if close <= ema200 and ema50 >= ema200:
        return -40
    if close < ema200 and ema50 < ema200:
        return -100
    return 0


def volatility_penalty(z_value: float | None, config: dict) -> int | None:
    if z_value is None:
        return None
    bands = config["volatilityScore"]
    magnitude = abs(z_value)
    if magnitude <= bands["normalAbsZ"]:
        return bands["normal"]
    if magnitude <= bands["elevatedAbsZ"]:
        return bands["elevated"]
    return bands["extreme"]


def raw_regime(bar: DayInput | None, breadth: float | None, config: dict) -> str:
    if bar is None or breadth is None or bar.ema50 is None or bar.ema200 is None:
        return "NEUTRAL"
    bullish = config["regime"]["breadthBullish"]
    bearish = config["regime"]["breadthBearish"]
    if bar.close > bar.ema200 and bar.ema50 > bar.ema200 and breadth >= bullish:
        return "BULLISH"
    if bar.close < bar.ema200 and bar.ema50 < bar.ema200 and breadth <= bearish:
        return "BEARISH"
    return "NEUTRAL"


def confirm_regimes(raws: list[str], confirm_days: int) -> tuple[list[str], list[int]]:
    confirmed: list[str] = []
    streaks: list[int] = []
    current = "NEUTRAL"
    streak_raw = None
    streak = 0
    for raw in raws:
        if raw == streak_raw:
            streak += 1
        else:
            streak_raw = raw
            streak = 1
        if streak >= confirm_days:
            current = raw
        confirmed.append(current)
        streaks.append(streak)
    return confirmed, streaks


def breadth_for(bars: list[DayInput]) -> tuple[float | None, int]:
    valid = [bar.close > bar.ema50 for bar in bars if bar.ema50 is not None and bar.data_quality_status != "INVALID"]
    if not valid:
        return None, 0
    return sum(1 for above in valid if above) / len(valid), len(valid)


def market_score(trend: Decimal | None, breadth_score: Decimal | None, volatility: Decimal | None, config: dict):
    weights = {key: config_decimal(value) for key, value in config["marketScoreWeights"].items()}
    return weighted({"btcTrend": trend, "breadth": breadth_score, "btcVolatility": volatility}, weights)


def exposure_limit(regime: str, emergency: bool, config: dict) -> Decimal:
    key = "BEARISH" if emergency else regime
    return config_decimal(config["regime"]["exposureLimit"][key]).quantize(Decimal("0.0001"))


def emergency(btc_return: float | None, previous_breadth: float | None, breadth: float | None, config: dict) -> bool:
    if btc_return is not None and btc_return <= config["regime"]["emergencyBtcDailyReturn"]:
        return True
    drop = config["regime"]["emergencyBreadthDrop"]
    return previous_breadth is not None and breadth is not None and previous_breadth - breadth >= drop


def explain_market(parts: dict[str, Decimal | None], weight_info: dict) -> dict:
    return {"parts": {key: text(value) for key, value in parts.items()}, "weights": weight_info}
