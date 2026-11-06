"""Trend score: EMA stack, momentum, ADX strength, and swing structure."""

from datetime import date, timedelta

from engines.series import dated_normalized
from engines.weights import config_decimal, score_decimal, text, weighted


def ema_score(ema20: float | None, ema50: float | None, ema200: float | None) -> int | None:
    if ema20 is None or ema50 is None:
        return None
    if ema200 is not None and ema20 > ema50 > ema200:
        return 100
    if ema200 is not None and ema20 < ema50 < ema200:
        return -100
    if ema20 > ema50:
        return 50
    if ema20 < ema50:
        return -50
    return 0


def adx_score(adx: float | None) -> int | None:
    if adx is None:
        return None
    if adx < 15:
        return -20
    if adx < 20:
        return 0
    if adx < 25:
        return 40
    return 70


def structure_score(highs: list[float], lows: list[float], dates: list[date], index: int,
                    left: int, right: int, lookback: int, min_bars: int) -> tuple[int | None, dict]:
    start = index
    while start > 0 and dates[start] - dates[start - 1] == timedelta(days=1):
        start -= 1
    available = index - start + 1
    if available < min_bars:
        return None, {"bars": available}
    window_start = max(start, index - lookback + 1)
    begin = window_start + left
    end = index - right
    swing_highs: list[tuple[int, float]] = []
    swing_lows: list[tuple[int, float]] = []
    for cursor in range(begin, end + 1):
        if highs[cursor] > max(highs[cursor - left:cursor]) and highs[cursor] > max(highs[cursor + 1:cursor + right + 1]):
            swing_highs.append((cursor, highs[cursor]))
        if lows[cursor] < min(lows[cursor - left:cursor]) and lows[cursor] < min(lows[cursor + 1:cursor + right + 1]):
            swing_lows.append((cursor, lows[cursor]))
    detail = {"swingHighs": len(swing_highs), "swingLows": len(swing_lows)}
    if len(swing_highs) < 2 or len(swing_lows) < 2:
        return 0, detail
    higher_high = swing_highs[-1][1] > swing_highs[-2][1]
    lower_high = swing_highs[-1][1] < swing_highs[-2][1]
    higher_low = swing_lows[-1][1] > swing_lows[-2][1]
    lower_low = swing_lows[-1][1] < swing_lows[-2][1]
    detail.update({"hh": higher_high, "hl": higher_low, "lh": lower_high, "ll": lower_low})
    if higher_high and higher_low:
        return 100, detail
    if lower_high and lower_low:
        return -100, detail
    bullish = higher_high or higher_low
    bearish = lower_high or lower_low
    if bullish and not bearish:
        return 50, detail
    if bearish and not bullish:
        return -50, detail
    return 0, detail


def momentum_series(dates: list[date], returns: dict[str, list[float | None]], config: dict) -> dict[str, list[float | None]]:
    window = int(config["indicators"]["zscoreWindow"])
    return {key: dated_normalized(dates, returns[key], window) for key in config["momentumWeights"]}


def momentum_score(series: dict[str, list[float | None]], config: dict, index: int):
    weights = {key: config_decimal(value) for key, value in config["momentumWeights"].items()}
    parts = {key: score_decimal(values[index]) for key, values in series.items()}
    total, info = weighted(parts, weights)
    info["parts"] = {key: text(value) for key, value in parts.items()}
    return total, info


def trend_score(ema: int | None, momentum, adx: int | None, structure: int | None, config: dict):
    weights = {key: config_decimal(value) for key, value in config["trendScoreWeights"].items()}
    return weighted({
        "ema": score_decimal(ema),
        "momentum": momentum,
        "adx": score_decimal(adx),
        "structure": score_decimal(structure),
    }, weights)
