import math
import statistics
from datetime import date, timedelta

import pytest

from indicators.derivatives import funding_zscore, oi_change
from indicators.momentum import price_return, rsi
from indicators.trend import bullish_alignment, ema, ema_distance, ema_slope
from indicators.volatility import adx, atr, realized_volatility
from indicators.volume import rolling_sum, spot_delta, spot_delta_ratio, volume_ratio
from features.normalizer import normalized_score, rolling_zscore


def test_ema_uses_sma_seed_then_standard_smoothing():
    series = ema([1, 2, 3, 4, 5], 3)
    assert series[:2] == [None, None]
    assert series[2:] == pytest.approx([2, 3, 4])


def test_ema_is_null_until_the_window_is_full():
    assert ema([10] * 19, 20) == [None] * 19
    assert ema([10] * 20, 20)[-1] == pytest.approx(10)


def test_return_uses_only_the_close_n_bars_back():
    series = price_return([10, 11, 12], 2)
    assert series[0] is None
    assert series[1] is None
    assert series[2] == pytest.approx(0.2)


def test_atr_wilder_matches_hand_calculation():
    highs = [10, 12, 11, 13]
    lows = [8, 9, 10, 10]
    closes = [9, 11, 10.5, 12]
    series = atr(highs, lows, closes, 3)
    assert series[0] is None
    assert series[1] is None
    assert series[2] == pytest.approx(2)
    assert series[3] == pytest.approx(7 / 3)


def test_adx_wilder_period_two():
    highs = [10, 12, 11, 13, 14]
    lows = [8, 9, 7, 10, 11]
    closes = [9, 11, 10, 12, 13]
    series = adx(highs, lows, closes, 2)
    assert series[:3] == [None, None, None]
    assert series[3] == pytest.approx(25)
    assert series[4] == pytest.approx(275 / 6)


def test_rsi_wilder_and_flat_market_is_null():
    series = rsi([10, 11, 10, 12, 11], 3)
    assert series[3] == pytest.approx(75)
    assert series[4] == pytest.approx(600 / 11)
    assert rsi([5, 5, 5, 5], 3) == [None, None, None, None]


def test_rsi_is_100_when_the_window_has_no_losses():
    assert rsi([1, 2, 3, 4], 3)[-1] == pytest.approx(100)


def test_realized_volatility_uses_sample_std_and_365_days():
    closes = [100, 110, 100, 110]
    log_returns = [math.log(110 / 100), math.log(100 / 110), math.log(110 / 100)]
    expected = statistics.stdev(log_returns) * math.sqrt(365)
    series = realized_volatility(closes, 3)
    assert series[-1] == pytest.approx(expected)
    assert realized_volatility([5, 5, 5, 5], 3)[-1] == pytest.approx(0)
    assert realized_volatility([5, 6], 3)[-1] is None


def test_volume_ratio_and_spot_delta():
    assert volume_ratio([10, 20, 30, 40], 3)[2:] == pytest.approx([1.5, 40 / 30])
    assert volume_ratio([0, 0, 0], 3)[-1] is None
    assert spot_delta(60, 100) == pytest.approx(20)
    assert spot_delta_ratio(20, 100) == pytest.approx(0.2)
    assert spot_delta(0, 0) == 0
    assert spot_delta_ratio(0, 0) is None
    assert rolling_sum([1, None, 1, 1], 2)[2:] == [None, 2]


def test_structure_readings_stay_null_without_both_sides():
    fast = [3, 3]
    mid = [2, None]
    slow = [1, 1]
    assert bullish_alignment(fast, mid, slow) == [True, None]
    slope = ema_slope([1] * 10 + [1.1], 10)
    assert slope[-1] == pytest.approx(0.1)
    assert ema_distance([110], [100]) == pytest.approx([0.1])


def test_zscore_excludes_the_current_observation():
    assert rolling_zscore([1, 2, 3, 6], 3)[-1] == pytest.approx(4)
    assert rolling_zscore([1, 1, 1, 5], 3)[-1] is None
    assert normalized_score([1, 2, 3, 6], 3)[-1] == pytest.approx(100)
    assert rolling_zscore([1, 2, None, 4, 5], 3)[-1] is None


def test_funding_z_uses_settled_history_and_oi_requires_the_exact_day():
    day0 = date(2024, 1, 1)
    rates = {
        day0: 1.0,
        day0 + timedelta(days=1): 2.0,
        day0 + timedelta(days=3): 3.0,
        day0 + timedelta(days=4): 6.0,
    }
    dates = [day0 + timedelta(days=offset) for offset in range(5)]
    scores = funding_zscore(rates, dates, 3)
    assert scores[:4] == [None, None, None, None]
    assert scores[4] == pytest.approx(4)

    oi = {day0: 100.0, day0 + timedelta(days=1): 110.0, day0 + timedelta(days=3): 121.0}
    assert oi_change(oi, dates, 1) == pytest.approx([None, 0.1, None, None, None], rel=0, abs=1e-12)
    assert oi_change(oi, dates, 7) == [None, None, None, None, None]
