"""Rolling z-score. Mean and standard deviation use only observations before the current one."""

import math


def zscore(current: float, sample: list[float]) -> float | None:
    if len(sample) < 2:
        return None
    mean = sum(sample) / len(sample)
    variance = sum((value - mean) ** 2 for value in sample) / (len(sample) - 1)
    if variance == 0:
        return None
    return (current - mean) / math.sqrt(variance)


def rolling_zscore(values: list[float | None], window: int) -> list[float | None]:
    """Requires `window` immediately previous observations. A hole in that window is NULL, not zero."""
    if window < 2:
        raise ValueError("标准化窗口至少为 2")
    out: list[float | None] = [None] * len(values)
    for index, current in enumerate(values):
        if current is None or index < window:
            continue
        sample = values[index - window:index]
        if any(value is None for value in sample):
            continue
        out[index] = zscore(current, [value for value in sample if value is not None])
    return out


def normalized_score(values: list[float | None], window: int) -> list[float | None]:
    out: list[float | None] = []
    for score in rolling_zscore(values, window):
        if score is None:
            out.append(None)
        else:
            clipped = max(-1.0, min(1.0, score / 2))
            out.append(100 * clipped)
    return out
