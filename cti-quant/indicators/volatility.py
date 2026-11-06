"""Wilder ATR, Wilder ADX, and 365-day realized volatility."""

import math


def wilder_smooth(values: list[float | None], period: int) -> list[float | None]:
    """SMA seed over the first `period` numbers, then Wilder: (prev * (n-1) + current) / n.

    A None resets the seed. Zero is a real observation and does not reset it.
    """
    if period < 1:
        raise ValueError("period 必须为正")
    out: list[float | None] = [None] * len(values)
    window: list[float] = []
    previous: float | None = None
    for index, value in enumerate(values):
        if value is None:
            window = []
            previous = None
            continue
        if previous is None:
            window.append(value)
            if len(window) == period:
                previous = sum(window) / period
                out[index] = previous
            continue
        previous = (previous * (period - 1) + value) / period
        out[index] = previous
    return out


def true_range(highs: list[float], lows: list[float], closes: list[float]) -> list[float | None]:
    out: list[float | None] = []
    for index, (high, low, close) in enumerate(zip(highs, lows, closes, strict=True)):
        span = high - low
        if index == 0:
            out.append(span)
            continue
        previous = closes[index - 1]
        out.append(max(span, abs(high - previous), abs(low - previous)))
    return out


def atr(highs: list[float], lows: list[float], closes: list[float], period: int) -> list[float | None]:
    return wilder_smooth(true_range(highs, lows, closes), period)


def atr_percent(atr_values: list[float | None], closes: list[float]) -> list[float | None]:
    out: list[float | None] = []
    for value, close in zip(atr_values, closes, strict=True):
        if value is None or close <= 0:
            out.append(None)
        else:
            out.append(value / close)
    return out


def _directional(highs: list[float], lows: list[float]) -> tuple[list[float | None], list[float | None]]:
    plus: list[float | None] = [None]
    minus: list[float | None] = [None]
    for index in range(1, len(highs)):
        up = highs[index] - highs[index - 1]
        down = lows[index - 1] - lows[index]
        plus.append(up if up > down and up > 0 else 0.0)
        minus.append(down if down > up and down > 0 else 0.0)
    return plus, minus


def adx(highs: list[float], lows: list[float], closes: list[float], period: int) -> list[float | None]:
    """Wilder ADX. Directional movement starts at the second bar, so the first true range is left out of this sum."""
    ranges = true_range(highs, lows, closes)
    ranges[0] = None
    plus, minus = _directional(highs, lows)
    smooth_range = wilder_smooth(ranges, period)
    smooth_plus = wilder_smooth(plus, period)
    smooth_minus = wilder_smooth(minus, period)
    dx: list[float | None] = [None] * len(closes)
    for index, (total, up, down) in enumerate(zip(smooth_range, smooth_plus, smooth_minus, strict=True)):
        if total is None or up is None or down is None or total == 0:
            continue
        plus_di = 100 * up / total
        minus_di = 100 * down / total
        denominator = plus_di + minus_di
        if denominator == 0:
            continue
        dx[index] = 100 * abs(plus_di - minus_di) / denominator
    return wilder_smooth(dx, period)


def sample_std(values: list[float]) -> float | None:
    count = len(values)
    if count < 2:
        return None
    mean = sum(values) / count
    variance = sum((value - mean) ** 2 for value in values) / (count - 1)
    return math.sqrt(variance)


def realized_volatility(closes: list[float | None], period: int, annualization: int = 365) -> list[float | None]:
    """Sample standard deviation of `period` log returns ending today, times sqrt(365).

    A window that did not move has volatility 0. A short window is NULL.
    """
    if period < 2:
        raise ValueError("实现波动率至少需要 2 个收益")
    log_returns: list[float | None] = [None]
    for index in range(1, len(closes)):
        previous = closes[index - 1]
        current = closes[index]
        if previous is None or current is None or previous <= 0 or current <= 0:
            log_returns.append(None)
        else:
            log_returns.append(math.log(current / previous))
    out: list[float | None] = [None] * len(closes)
    for index in range(len(closes)):
        start = index - period + 1
        if start < 0:
            continue
        window = log_returns[start:index + 1]
        if len(window) < period or any(value is None for value in window):
            continue
        deviation = sample_std([value for value in window if value is not None])
        if deviation is None:
            continue
        out[index] = deviation * math.sqrt(annualization)
    return out
