"""Open-interest changes and funding z-scores. Missing observations stay missing."""

from datetime import date, timedelta

from features.normalizer import zscore


def oi_change(values_by_date: dict[date, float | None], dates: list[date], lag_days: int) -> list[float | None]:
    out: list[float | None] = []
    for current_date in dates:
        current = values_by_date.get(current_date)
        previous = values_by_date.get(current_date - timedelta(days=lag_days))
        if current is None or previous is None or previous == 0:
            out.append(None)
        else:
            out.append(current / previous - 1)
    return out


def funding_zscore(rates_by_date: dict[date, float], dates: list[date], window: int) -> list[float | None]:
    """Z-score against the previous `window` settled rates. Today is not part of the mean or the deviation."""
    if window < 2:
        raise ValueError("资金费率 Z 分数至少需要 2 个历史样本")
    settled = sorted((day, rate) for day, rate in rates_by_date.items())
    out: list[float | None] = []
    cursor = 0
    for current in dates:
        while cursor < len(settled) and settled[cursor][0] < current:
            cursor += 1
        today = rates_by_date.get(current)
        if today is None or cursor < window:
            out.append(None)
            continue
        sample = [rate for _, rate in settled[cursor - window:cursor]]
        out.append(zscore(today, sample))
    return out
