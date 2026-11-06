from datetime import date, timedelta
from decimal import Decimal

from engines.flow_engine import derivatives_score, total_score, volume_score
from engines.levels import signal_level
from engines.market_regime_engine import btc_trend_score, confirm_regimes, volatility_penalty
from engines.models import DayInput
from engines.score_builder import evaluate
from engines.trend_engine import adx_score, ema_score, structure_score
from engines.weights import config_decimal


def config():
    return {
        "strategyVersion": "1.0.0",
        "sentiment": {"mode": "ABSENT"},
        "indicators": {
            "zscoreWindow": 252,
            "swingLeft": 1,
            "swingRight": 1,
            "swingLookbackBars": 20,
            "minBarsForStructure": 60,
        },
        "scoreWeights": {"market": 0.20, "trend": 0.35, "flow": 0.25, "derivatives": 0.10, "sentiment": 0.10},
        "marketScoreWeights": {"btcTrend": 0.40, "breadth": 0.30, "btcVolatility": 0.30},
        "trendScoreWeights": {"ema": 0.30, "momentum": 0.25, "adx": 0.20, "structure": 0.25},
        "momentumWeights": {"return7d": 0.20, "return14d": 0.35, "return30d": 0.30, "return90d": 0.15},
        "flowScoreWeights": {"volume": 0.35, "spotDelta": 0.35, "spotDelta7d": 0.30},
        "normalization": {"coreComponents": ["market", "trend", "flow"]},
        "regime": {
            "confirmDays": 2,
            "breadthBullish": 0.60,
            "breadthBearish": 0.30,
            "exposureLimit": {"BULLISH": 0.80, "NEUTRAL": 0.35, "BEARISH": 0.10},
            "emergencyBtcDailyReturn": -0.08,
            "emergencyBreadthDrop": 0.40,
        },
        "volumeScore": {
            "highRatio": 1.5, "midRatio": 1.0, "lowRatio": 0.7,
            "highWithUp": 80, "midWithUp": 40, "highWithDown": -80, "midWithDown": -40, "low": -20,
        },
        "volatilityScore": {"normalAbsZ": 1, "elevatedAbsZ": 2, "normal": 0, "elevated": -40, "extreme": -80},
        "derivativesRisk": {
            "fundingZSoft": 2, "fundingZHard": 3, "fundingSoftPoints": 40, "fundingHardPoints": 60,
            "oiZPoints": 30, "oiZThreshold": 2, "crowdedExtraPoints": 30, "oiCrowdingChange7d": 0,
        },
    }


def bar(offset: int, close: float, **overrides) -> DayInput:
    values = dict(
        high=close + 1, low=close - 1, quote_volume=100.0,
        ema20=95.0, ema50=90.0, ema200=80.0, adx14=30.0,
        return_7d=None, return_14d=None, return_30d=None, return_90d=None,
        realized_volatility_20d=None, volume_ratio_20d=1.2,
        spot_delta=0.0, spot_delta_ratio=0.0, oi_change_7d=None, funding_zscore=None,
        data_quality_status="NORMAL",
    )
    values.update(overrides)
    return DayInput(date(2024, 1, 1) + timedelta(days=offset), close=close, **values)


def test_component_boundaries_and_missing_inputs():
    assert btc_trend_score(110, 100, 90) == 100
    assert btc_trend_score(110, 90, 100) == 40
    assert btc_trend_score(90, 100, 100) == -40
    assert btc_trend_score(80, 90, 100) == -100
    assert btc_trend_score(100, 90, 100) == 0
    assert btc_trend_score(100, None, 90) is None
    assert ema_score(12, 10, 8) == 100
    assert ema_score(8, 10, 12) == -100
    assert ema_score(12, 10, None) == 50
    assert ema_score(10, 10, 8) == 0
    assert adx_score(14.9) == -20
    assert adx_score(15) == 0
    assert adx_score(20) == 40
    assert adx_score(25) == 70
    assert adx_score(None) is None
    bands = config()
    assert volatility_penalty(1, bands) == 0
    assert volatility_penalty(2, bands) == -40
    assert volatility_penalty(2.1, bands) == -80
    assert volatility_penalty(None, bands) is None
    assert volume_score(1.6, 0.01, bands) == 80
    assert volume_score(1.6, -0.01, bands) == -80
    assert volume_score(0.5, 0.01, bands) == -20
    assert volume_score(None, 0.01, bands) is None


def test_structure_uses_only_confirmed_swings_and_mixed_is_flat():
    dates = [date(2024, 1, 1) + timedelta(days=offset) for offset in range(7)]
    highs = [1, 4, 1, 5, 1, 6, 1]
    lows = [5, 1, 5, 2, 5, 3, 5]
    score, detail = structure_score(highs, lows, dates, 6, left=1, right=1, lookback=20, min_bars=7)
    assert score == 100
    assert detail["hh"] and detail["hl"]
    early, _early_detail = structure_score(highs, lows, dates, 4, left=1, right=1, lookback=20, min_bars=5)
    assert early == 100
    mixed_lows = [5, 1, 5, 2, 5, 0, 5]
    mixed, mixed_detail = structure_score(highs, mixed_lows, dates, 6, left=1, right=1, lookback=20, min_bars=7)
    assert mixed == 0
    assert mixed_detail["hh"] and mixed_detail["ll"]
    short, _short_detail = structure_score(highs, lows, dates, 6, left=1, right=1, lookback=20, min_bars=60)
    assert short is None


def test_confirmation_waits_two_days_and_levels_do_not_overlap():
    confirmed, streaks = confirm_regimes(["BULLISH", "BULLISH", "NEUTRAL", "NEUTRAL"], 2)
    assert confirmed == ["NEUTRAL", "BULLISH", "BULLISH", "NEUTRAL"]
    assert streaks == [1, 2, 1, 2]
    assert signal_level(Decimal("75")) == "STRONG_BULLISH"
    assert signal_level(Decimal("65")) == "BULLISH"
    assert signal_level(Decimal("50")) == "WATCH_BULLISH"
    assert signal_level(Decimal("49.9999")) == "NEUTRAL"
    assert signal_level(Decimal("-30")) == "NEUTRAL"
    assert signal_level(Decimal("-50")) == "WATCH_BEARISH"
    assert signal_level(Decimal("-64.9999")) == "BEARISH"
    assert signal_level(Decimal("-65")) == "STRONG_BEARISH"
    assert signal_level(None) is None


def test_missing_derivatives_are_not_zero_and_core_gap_blocks_the_total():
    present, detail = derivatives_score(3.5, 0.1, 2.5, 0.02, config())
    assert present == -100
    assert detail["oiDecline"] is False
    absent, absent_detail = derivatives_score(None, 0.1, None, 0.02, config())
    assert absent is None
    assert absent_detail["derivatives"] == "ABSENT"
    weights = config()
    hundred = config_decimal(100)
    total, _info = total_score(hundred, hundred, hundred, None, weights)
    assert total == Decimal("100.0000")
    blocked, _blocked = total_score(hundred, None, hundred, None, weights)
    assert blocked is None


def test_regime_confirmation_emergency_and_repeatable_scores():
    histories = {"BTCUSDT": [bar(0, 100), bar(1, 100), bar(2, 90, volume_ratio_20d=1.6)]}
    ignored = bar(0, 10, ema50=50, data_quality_status="INVALID")
    histories["ETHUSDT"] = [ignored, bar(1, 10, ema50=50, data_quality_status="INVALID"), bar(2, 10, ema50=50, data_quality_status="INVALID")]
    regimes, scores = evaluate(histories, config(), "features-1.0.0", "1.0.0")
    again, again_scores = evaluate(histories, config(), "features-1.0.0", "1.0.0")
    assert regimes == again
    assert scores == again_scores
    assert [row.raw_regime for row in regimes] == ["BULLISH", "BULLISH", "NEUTRAL"]
    assert [row.regime for row in regimes] == ["NEUTRAL", "BULLISH", "BULLISH"]
    assert regimes[0].market_breadth == Decimal("1.0000")
    assert regimes[1].total_exposure_limit == Decimal("0.8000")
    assert regimes[2].regime == "BULLISH"
    assert regimes[2].risk_override == "EMERGENCY"
    assert regimes[2].total_exposure_limit == Decimal("0.1000")
    btc = [row for row in scores if row.symbol == "BTCUSDT"]
    assert btc[0].flow_score is None
    assert btc[0].total_score is None
    assert btc[0].derivatives_score is None
    assert btc[0].sentiment_score is None
    assert btc[1].total_score == Decimal("63.5000")
    assert btc[1].signal_level == "WATCH_BULLISH"
    longer = {"BTCUSDT": histories["BTCUSDT"] + [bar(3, 50)], "ETHUSDT": histories["ETHUSDT"]}
    longer_regimes, longer_scores = evaluate(longer, config(), "features-1.0.0", "1.0.0")
    assert longer_regimes[:3] == regimes
    assert [row for row in longer_scores if row.symbol == "BTCUSDT"][:3] == btc
