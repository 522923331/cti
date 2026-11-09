from decimal import Decimal

import pytest

from engines.ensemble import EnsembleRejected, classify_regime, combine


def _bull():
    return {
        "close": "100",
        "ema50": "90",
        "ema200": "80",
        "adx": "30",
        "atrPercent": "0.05",
        "realizedVol": "0.04",
    }


def _config():
    return {"adxThreshold": "25", "highVolatilityThreshold": "0.03", "lowVolatilityThreshold": "0.01"}


def test_high_volatility_is_a_label_and_does_not_replace_direction():
    regime = classify_regime(_bull(), _config())
    assert regime["direction"] == "BULL"
    assert regime["volatility"] == "HIGH"


def test_a_later_bar_cannot_change_the_earlier_regime():
    bear = {"close": "70", "ema50": "75", "ema200": "90", "adx": "30", "atrPercent": "0.01", "realizedVol": "0.01"}
    regime = classify_regime([_bull(), bear], _config(), index=0)
    assert regime["direction"] == "BULL"
    assert regime["volatility"] == "HIGH"


def test_missing_strategy_is_excluded_instead_of_scored_as_zero():
    result = combine({
        "weights": {"EMA_TREND": "0.50", "DONCHIAN_BREAKOUT": "0.30", "MOMENTUM": "0.20"},
        "scores": {"EMA_TREND": "100", "DONCHIAN_BREAKOUT": "50", "MOMENTUM": None},
        "direction": "BULL",
        "volatility": "NORMAL",
    })
    assert result["score"] == "81.250000000000"
    assert result["confidence"] == "0.800000000000"
    assert sum(Decimal(row["weight"]) for row in result["contributions"] if row["included"]) == Decimal("1")
    missing = next(row for row in result["contributions"] if row["strategy"] == "MOMENTUM")
    assert missing["included"] is False
    assert missing["score"] is None
    assert missing["contribution"] is None
    assert result["signal"] == "NO_TRADE"


def test_weights_must_sum_to_one_and_a_single_valid_strategy_keeps_its_score():
    with pytest.raises(EnsembleRejected):
        combine({"weights": {"EMA_TREND": "0.60", "MOMENTUM": "0.60"}, "scores": {"EMA_TREND": "80", "MOMENTUM": "80"}})
    result = combine({
        "weights": {"EMA_TREND": "0.50", "MOMENTUM": "0.50"},
        "scores": {"EMA_TREND": "80", "MOMENTUM": None},
        "direction": "BEAR",
        "volatility": "HIGH",
    })
    assert result["score"] == "80.000000000000"
    assert result["direction"] == "BEAR"
    assert result["volatility"] == "HIGH"
    assert result["signal"] == "NO_TRADE"
