import pytest

from indicators.derivatives import funding_z20, oi_change_4h, taker_buy_ratio

FOUR_HOURS = 4 * 60 * 60 * 1000


def test_oi_change_uses_the_previous_four_hour_point():
    times = [0, FOUR_HOURS, FOUR_HOURS * 2]
    changes = oi_change_4h(times, [100, 110, 121])
    assert changes[0] is None
    assert changes[1] == pytest.approx(0.1)
    assert changes[2] == pytest.approx(0.1)


def test_oi_change_is_null_when_the_previous_bar_is_missing_or_unusable():
    times = [0, FOUR_HOURS * 2, FOUR_HOURS * 3, FOUR_HOURS * 4]
    changes = oi_change_4h(times, [100, 110, 0, -1])
    assert changes[0] is None
    assert changes[1] is None
    assert changes[2] == pytest.approx(-1)
    assert changes[3] is None


def test_funding_z20_ignores_the_current_rate_and_a_flat_window():
    flat = [0.001] * 20 + [0.004]
    assert funding_z20(flat)[-1] is None
    varied = [0.001] * 19 + [0.003, 0.004]
    score = funding_z20(varied)[-1]
    assert score is not None
    assert score > 0


def test_taker_buy_ratio_keeps_missing_and_invalid_volume_empty():
    assert taker_buy_ratio(3, 1) == 0.75
    assert taker_buy_ratio(None, 1) is None
    assert taker_buy_ratio(1, None) is None
    assert taker_buy_ratio(-1, 1) is None
    assert taker_buy_ratio(0, 0) is None
