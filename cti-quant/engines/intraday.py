"""4h EMA20 and ATR14 for the last closed bar of each UTC date. A gap starts a new warmup."""

from datetime import date

from data.market_loader import trade_date_of
from indicators.trend import ema
from indicators.volatility import atr

FOUR_MS = 14_400_000


def last_four_hour(bars: list[tuple[int, float, float, float]]) -> dict[date, tuple[float, float, float | None, float | None]]:
    """bars are (open_time, high, low, close), closed and sorted. Value is low, close, ema20, atr14."""
    snapshots: dict[date, tuple[float, float, float | None, float | None]] = {}
    for session in _sessions(bars):
        highs = [bar[1] for bar in session]
        lows = [bar[2] for bar in session]
        closes = [bar[3] for bar in session]
        ema20 = ema(closes, 20)
        atr14 = atr(highs, lows, closes, 14)
        for index, bar in enumerate(session):
            snapshots[trade_date_of(bar[0])] = (bar[2], bar[3], ema20[index], atr14[index])
    return snapshots


def _sessions(bars: list[tuple[int, float, float, float]]) -> list[list[tuple[int, float, float, float]]]:
    if not bars:
        return []
    sessions = [[bars[0]]]
    for bar in bars[1:]:
        previous = sessions[-1][-1][0]
        if bar[0] == previous:
            raise ValueError("4 小时 K 线重复")
        if bar[0] < previous:
            raise ValueError("4 小时 K 线时间倒退")
        if bar[0] - previous == FOUR_MS:
            sessions[-1].append(bar)
        else:
            sessions.append([bar])
    return sessions
