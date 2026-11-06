"""EMA and the structure readings that are fully determined by it."""

from indicators.momentum import price_return


def ema(values: list[float | None], period: int) -> list[float | None]:
    """Standard EMA. The seed is the SMA of the first `period` valid prices, then 2/(period+1)."""
    if period < 1:
        raise ValueError("period 必须为正")
    out: list[float | None] = [None] * len(values)
    alpha = 2 / (period + 1)
    window: list[float] = []
    previous: float | None = None
    for index, value in enumerate(values):
        if value is None or value <= 0:
            window = []
            previous = None
            continue
        if previous is None:
            window.append(value)
            if len(window) == period:
                previous = sum(window) / period
                out[index] = previous
            continue
        previous = value * alpha + previous * (1 - alpha)
        out[index] = previous
    return out


def bullish_alignment(ema_fast: list[float | None], ema_mid: list[float | None], ema_slow: list[float | None]) -> list[bool | None]:
    out: list[bool | None] = []
    for fast, mid, slow in zip(ema_fast, ema_mid, ema_slow, strict=True):
        if fast is None or mid is None or slow is None:
            out.append(None)
        else:
            out.append(fast > mid and mid > slow)
    return out


def ema_slope(series: list[float | None], lookback: int = 10) -> list[float | None]:
    return price_return(series, lookback)


def ema_distance(closes: list[float | None], baseline: list[float | None]) -> list[float | None]:
    out: list[float | None] = []
    for close, level in zip(closes, baseline, strict=True):
        if close is None or level is None or close <= 0 or level <= 0:
            out.append(None)
        else:
            out.append(close / level - 1)
    return out
