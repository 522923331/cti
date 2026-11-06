"""Levels follow the first matching boundary. None stays None, not NEUTRAL."""


def signal_level(score) -> str | None:
    if score is None:
        return None
    value = float(score)
    if value >= 75:
        return "STRONG_BULLISH"
    if value >= 65:
        return "BULLISH"
    if value >= 50:
        return "WATCH_BULLISH"
    if value >= -30:
        return "NEUTRAL"
    if value >= -50:
        return "WATCH_BEARISH"
    if value > -65:
        return "BEARISH"
    return "STRONG_BEARISH"
