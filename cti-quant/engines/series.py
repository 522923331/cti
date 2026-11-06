"""Calendar guards so a score window cannot skip a missing day or read tomorrow."""

from datetime import date, timedelta

from features.normalizer import normalized_score, rolling_zscore


def daily_returns(dates: list[date], closes: list[float]) -> list[float | None]:
    out: list[float | None] = [None]
    for index in range(1, len(closes)):
        previous = closes[index - 1]
        current = closes[index]
        if dates[index] - dates[index - 1] != timedelta(days=1) or previous <= 0 or current <= 0:
            out.append(None)
        else:
            out.append(current / previous - 1)
    return out


def _contiguous(dates: list[date], index: int, window: int) -> bool:
    return index >= window and dates[index] - dates[index - window] == timedelta(days=window)


def dated_zscore(dates: list[date], values: list[float | None], window: int) -> list[float | None]:
    raw = rolling_zscore(values, window)
    return [value if value is not None and _contiguous(dates, index, window) else None for index, value in enumerate(raw)]


def dated_normalized(dates: list[date], values: list[float | None], window: int) -> list[float | None]:
    raw = normalized_score(values, window)
    return [value if value is not None and _contiguous(dates, index, window) else None for index, value in enumerate(raw)]


def rolling_quote_ratio(dates: list[date], numerator: list[float | None], denominator: list[float | None], period: int) -> list[float | None]:
    out: list[float | None] = [None] * len(dates)
    for index in range(period - 1, len(dates)):
        if dates[index] - dates[index - period + 1] != timedelta(days=period - 1):
            continue
        nums = numerator[index - period + 1:index + 1]
        dens = denominator[index - period + 1:index + 1]
        if any(value is None or value < 0 for value in nums) or any(value is None or value < 0 for value in dens):
            continue
        total = sum(value for value in dens if value is not None)
        if total == 0:
            continue
        out[index] = sum(value for value in nums if value is not None) / total
    return out
