from decimal import Decimal

from engines.intelligence import confirm_4h, decide_signal, evaluate


def _row(**overrides):
    base = {
        "symbol": "BTCUSDT",
        "asset": "BTC",
        "tradeDate": "2026-11-05",
        "close": "100",
        "ema20": "90",
        "ema50": "80",
        "ema200": "70",
        "return30d": "0.30",
        "volumeRatio20d": "2",
        "realizedVolatility20d": "0.20",
        "quality": "NORMAL",
        "regime": "NEUTRAL",
        "fundingZ20": None,
        "newsSentiment": None,
        "onchainContext": None,
        "h4Confirmed": True,
    }
    base.update(overrides)
    return base


def _pair():
    return [
        _row(),
        _row(symbol="ETHUSDT", asset="ETH", return30d="-0.30", volumeRatio20d="0.5"),
    ]


def test_missing_weight_is_renormalized_and_not_treated_as_zero():
    rows = evaluate([
        _row(volumeRatio20d=None, fundingZ20="0", newsSentiment="50"),
        _row(symbol="ETHUSDT", asset="ETH", return30d="-0.30"),
    ])
    btc = rows[0]
    assert btc["dataConfidence"] == "0.790000"
    assert btc["signalType"] == "NO_TRADE"
    assert btc["reason"]["coreComplete"] is False
    assert btc["trendScore"] == "89.2857"


def test_complete_core_can_signal_when_auxiliary_data_is_missing():
    rows = evaluate(_pair())
    btc = next(row for row in rows if row["symbol"] == "BTCUSDT")
    eth = next(row for row in rows if row["symbol"] == "ETHUSDT")
    assert btc["dataConfidence"] == "0.850000"
    assert btc["dataStatus"] == "PARTIAL"
    assert btc["signalType"] == "STRONG_BUY"
    assert btc["sentimentScore"] is None
    assert eth["signalType"] == "NO_TRADE"
    assert rows == evaluate(_pair())


def test_positive_news_does_not_create_a_buy():
    rows = evaluate([
        _row(return30d="-0.30", ema20="70", ema50="80", ema200="90", close="60",
             volumeRatio20d="0.5", newsSentiment="100", h4Confirmed=True),
        _row(symbol="ETHUSDT", asset="ETH", return30d="0.30"),
    ])
    btc = next(row for row in rows if row["symbol"] == "BTCUSDT")
    assert btc["signalType"] != "BUY"
    assert btc["signalType"] != "STRONG_BUY"
    assert Decimal(btc["sentimentScore"]) > Decimal("50")


def test_signal_boundaries_and_position_gates():
    params = {"modelVersion": "intelligence-1.1.0"}
    assert decide_signal(Decimal("75"), Decimal("0.8"), "NORMAL", "LOW", "NEUTRAL", True, False, params)[0] == "BUY"
    assert decide_signal(Decimal("85"), Decimal("0.8"), "NORMAL", "LOW", "NEUTRAL", True, False, params)[0] == "STRONG_BUY"
    assert decide_signal(Decimal("74.9999"), Decimal("0.8"), "NORMAL", "LOW", "NEUTRAL", True, False, params)[0] == "HOLD"
    assert decide_signal(Decimal("60"), Decimal("0.8"), "NORMAL", "LOW", "NEUTRAL", True, False, params)[0] == "HOLD"
    assert decide_signal(Decimal("59.9999"), Decimal("0.8"), "NORMAL", "LOW", "NEUTRAL", True, False, params)[0] == "NO_TRADE"
    assert decide_signal(Decimal("44"), Decimal("0.8"), "NORMAL", "LOW", "NEUTRAL", True, True, params)[0] == "REDUCE"
    assert decide_signal(Decimal("44"), Decimal("0.8"), "NORMAL", "LOW", "NEUTRAL", True, False, params)[0] == "NO_TRADE"
    assert decide_signal(Decimal("29.9999"), Decimal("0.8"), "NORMAL", "LOW", "NEUTRAL", True, True, params)[0] == "EXIT"
    assert decide_signal(Decimal("29.9999"), Decimal("0.8"), "NORMAL", "LOW", "NEUTRAL", True, False, params)[0] == "NO_TRADE"
    assert decide_signal(Decimal("90"), Decimal("0.79"), "NORMAL", "LOW", "NEUTRAL", True, False, params)[0] == "NO_TRADE"
    assert decide_signal(Decimal("90"), Decimal("0.9"), "NORMAL", "CRITICAL", "NEUTRAL", True, False, params)[0] == "NO_TRADE"
    assert decide_signal(Decimal("90"), Decimal("0.9"), "NORMAL", "LOW", "BEARISH", True, False, params)[0] == "NO_TRADE"
    assert decide_signal(Decimal("90"), Decimal("0.9"), "INVALID", "LOW", "NEUTRAL", True, False, params)[0] == "NO_TRADE"
    assert decide_signal(Decimal("90"), Decimal("0.9"), "NORMAL", "LOW", "NEUTRAL", False, False, params)[0] == "NO_TRADE"


def test_unclosed_four_hour_bar_cannot_confirm_trend():
    closed = [{"close": "1", "closed": True}] * 20
    assert confirm_4h(closed + [{"close": "1", "closed": True}]) is False
    assert confirm_4h(closed + [{"close": "2", "closed": False}]) is False
    assert confirm_4h(closed + [{"close": "2", "closed": True}]) is True


def test_model_version_does_not_change_the_numbers():
    first = evaluate(_pair(), {"modelVersion": "intelligence-1.1.0"})
    second = evaluate(_pair(), {"modelVersion": "intelligence-1.1.1"})
    assert first[0]["trendScore"] == second[0]["trendScore"]
    assert first[0]["modelVersion"] == "intelligence-1.1.0"
    assert second[0]["modelVersion"] == "intelligence-1.1.1"
