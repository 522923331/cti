"""Quote-volume ratio and the taker-buy spot-delta proxy."""


def volume_ratio(quote_volumes: list[float | None], period: int) -> list[float | None]:
    if period < 1:
        raise ValueError("period 必须为正")
    out: list[float | None] = [None] * len(quote_volumes)
    for index in range(period - 1, len(quote_volumes)):
        window = quote_volumes[index - period + 1:index + 1]
        if any(value is None or value < 0 for value in window):
            continue
        average = sum(value for value in window if value is not None) / period
        if average == 0:
            continue
        out[index] = quote_volumes[index] / average
    return out


def spot_delta(taker_buy_quote: float | None, quote_volume: float | None) -> float | None:
    if taker_buy_quote is None or quote_volume is None or taker_buy_quote < 0 or quote_volume < 0:
        return None
    return 2 * taker_buy_quote - quote_volume


def spot_delta_ratio(delta: float | None, quote_volume: float | None) -> float | None:
    if delta is None or quote_volume is None or quote_volume == 0:
        return None
    return delta / quote_volume


def rolling_sum(values: list[float | None], period: int) -> list[float | None]:
    if period < 1:
        raise ValueError("period 必须为正")
    out: list[float | None] = [None] * len(values)
    for index in range(period - 1, len(values)):
        window = values[index - period + 1:index + 1]
        if any(value is None for value in window):
            continue
        out[index] = sum(value for value in window if value is not None)
    return out
