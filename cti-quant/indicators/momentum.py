"""Returns and Wilder RSI."""

from indicators.volatility import wilder_smooth


def price_return(values: list[float | None], period: int) -> list[float | None]:
    if period < 1:
        raise ValueError("period 必须为正")
    out: list[float | None] = [None] * len(values)
    for index in range(period, len(values)):
        previous = values[index - period]
        current = values[index]
        if previous is None or current is None or previous <= 0 or current <= 0:
            continue
        out[index] = current / previous - 1
    return out


def rsi(closes: list[float | None], period: int) -> list[float | None]:
    """Wilder RSI. Flat changes stay NULL. A window with no losses is 100."""
    gains: list[float | None] = [None]
    losses: list[float | None] = [None]
    for index in range(1, len(closes)):
        previous = closes[index - 1]
        current = closes[index]
        if previous is None or current is None:
            gains.append(None)
            losses.append(None)
            continue
        change = current - previous
        gains.append(max(change, 0.0))
        losses.append(max(-change, 0.0))
    average_gain = wilder_smooth(gains, period)
    average_loss = wilder_smooth(losses, period)
    out: list[float | None] = [None] * len(closes)
    for index, (gain, loss) in enumerate(zip(average_gain, average_loss, strict=True)):
        if gain is None or loss is None:
            continue
        if gain == 0 and loss == 0:
            continue
        if loss == 0:
            out[index] = 100.0
            continue
        relative = gain / loss
        out[index] = 100 - 100 / (1 + relative)
    return out
