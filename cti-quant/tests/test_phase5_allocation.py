from decimal import Decimal

import pytest

from engines.allocation import AllocationRejected, allocate, correlations


def _config():
    return {
        "maxSingleAssetWeight": "0.20",
        "maxAltcoinWeight": "0.40",
        "maxBtcEthWeight": "0.70",
        "minCashWeight": "0.15",
        "riskBudgetPerTrade": "0.0075",
        "stopAtrMultiplier": "2",
        "scoreThreshold": "60",
        "volatilityFloor": "0.01",
        "minTradeValue": "0",
        "feeRate": "0.001",
        "slippageBps": "8",
    }


def _asset(symbol, group, trend="100", volatility="0.20", price="100", atr="1", current="0"):
    return {
        "symbol": symbol,
        "group": group,
        "trendScore": trend,
        "volatility": volatility,
        "price": price,
        "atr": atr,
        "currentValue": current,
    }


def test_a_high_score_cannot_break_the_single_asset_cap():
    plan = allocate({
        "cash": "1000",
        "regime": "NEUTRAL",
        "official": True,
        "config": _config(),
        "assets": [_asset("BTCUSDT", "BTC")],
    })
    assert plan["targets"][0]["targetWeight"] == "0.200000000000"
    assert plan["targets"][0]["targetValue"] == "200.000000000000"
    assert Decimal(plan["cashWeight"]) >= Decimal("0.15")


def test_altcoin_weights_stay_inside_the_group_cap():
    plan = allocate({
        "cash": "1000",
        "regime": "NEUTRAL",
        "official": True,
        "config": _config(),
        "assets": [
            _asset("AVAXUSDT", "ALT"),
            _asset("SOLUSDT", "ALT"),
            _asset("XRPUSDT", "ALT"),
        ],
    })
    weights = [Decimal(row["targetWeight"]) for row in plan["targets"]]
    assert sum(weights) == Decimal("0.400000000000")
    assert all(weight <= Decimal("0.20") for weight in weights)


def test_low_scores_keep_all_cash_and_a_bearish_regime_vetoes_buys():
    low = allocate({
        "cash": "1000",
        "regime": "NEUTRAL",
        "official": True,
        "config": _config(),
        "assets": [_asset("BTCUSDT", "BTC", trend="50")],
    })
    bearish = allocate({
        "cash": "1000",
        "regime": "BEARISH",
        "official": True,
        "config": _config(),
        "assets": [_asset("BTCUSDT", "BTC", trend="100")],
    })
    assert low["targets"] == []
    assert low["cashWeight"] == "1.000000000000"
    assert bearish["targets"] == []
    assert bearish["cashWeight"] == "1.000000000000"


def test_missing_score_or_negative_volatility_rejects_the_plan():
    with pytest.raises(AllocationRejected):
        allocate({
            "cash": "1000",
            "regime": "NEUTRAL",
            "official": True,
            "config": _config(),
            "assets": [_asset("BTCUSDT", "BTC", trend=None)],
        })
    with pytest.raises(AllocationRejected):
        allocate({
            "cash": "1000",
            "regime": "NEUTRAL",
            "official": True,
            "config": _config(),
            "assets": [_asset("BTCUSDT", "BTC", volatility="-0.1")],
        })


def test_no_spendable_cash_does_not_open_a_new_position():
    plan = allocate({
        "cash": "10",
        "regime": "NEUTRAL",
        "official": True,
        "config": _config(),
        "assets": [
            _asset("BTCUSDT", "BTC", current="90"),
            _asset("ETHUSDT", "ETH"),
        ],
    })
    eth = next(row for row in plan["targets"] if row["symbol"] == "ETHUSDT")
    assert eth["targetValue"] == "0.000000000000"
    assert Decimal(plan["cashWeight"]) >= Decimal("0.15")


def test_correlation_drops_dates_that_are_not_on_both_series():
    matrix = correlations({
        "AAA": [("2026-11-01", "1"), ("2026-11-02", "2"), ("2026-11-03", "4")],
        "BBB": [("2026-11-01", "1"), ("2026-11-03", "3")],
    })
    assert matrix["AAA|BBB"]["observations"] == 2
    assert matrix["AAA|BBB"]["value"] is not None
