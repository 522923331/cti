from decimal import Decimal

from strategies.library import build
from strategies.research import run_isolated


def _bars():
    rows = [
        ("2026-11-01", "5", "5", "5", "5"),
        ("2026-11-02", "6", "6", "6", "6"),
        ("2026-11-03", "7", "7", "7", "7"),
        ("2026-11-04", "10", "20", "8", "8"),
        ("2026-11-05", "10", "8", "8", "8"),
    ]
    return [
        {
            "symbol": "BTCUSDT",
            "tradeDate": day,
            "open": open_,
            "high": high,
            "low": low,
            "close": close,
            "closed": True,
            "signalTime": day + "T00:00:00Z",
        }
        for day, open_, high, low, close in rows
    ]


def test_donchian_breakout_excludes_the_current_bar():
    strategy = build("DONCHIAN_BREAKOUT", {"lookback": 3})
    signal = strategy.generate_signal({"bars": _bars()[:4]})
    assert signal["signal"] == "BUY"
    assert signal["explanation"] == ["close 8 > prior_high 7", "window_excludes_current_bar"]
    blocked = _bars()[:4]
    blocked[-1]["closed"] = False
    assert strategy.generate_signal({"bars": blocked})["signal"] == "NO_TRADE"


def test_mean_reversion_only_trades_sideways():
    closes = ["10", "10", "10", "7"]
    bars = [
        {"symbol": "BTCUSDT", "tradeDate": f"2026-11-0{index + 1}", "open": close, "high": close, "low": close,
         "close": close, "closed": True, "signalTime": f"2026-11-0{index + 1}T00:00:00Z"}
        for index, close in enumerate(closes)
    ]
    strategy = build("MEAN_REVERSION", {"window": 3, "entryZ": "1", "exitZ": "0"})
    trending = strategy.generate_signal({"bars": bars, "regime": "TRENDING_BULL"})
    sideways = strategy.generate_signal({"bars": bars, "regime": "SIDEWAYS"})
    assert trending["signal"] == "NO_TRADE"
    assert "not_sideways" in trending["explanation"]
    assert sideways["signal"] == "BUY"
    flat = [{**bar, "close": "10", "open": "10", "high": "10", "low": "10"} for bar in bars]
    assert strategy.generate_signal({"bars": flat, "regime": "SIDEWAYS"})["signal"] == "NO_TRADE"


def test_each_strategy_backtests_in_its_own_book():
    donchian = build("DONCHIAN_BREAKOUT", {"lookback": 3})
    ema = build("EMA_TREND", {"emaFast": 2, "emaMid": 3, "emaSlow": 4, "adxMinimum": "25"})
    books = run_isolated([donchian, ema], _bars(), initial="100", quantity="1", fee_rate="0.001", slippage_bps="8")
    assert books["DONCHIAN_BREAKOUT"]["cash"] == "89.981992000000"
    assert books["DONCHIAN_BREAKOUT"]["positions"] == {"BTCUSDT": "1.000000000000"}
    assert books["EMA_TREND"]["cash"] == "100.000000000000"
    assert books["EMA_TREND"]["positions"] == {}
    assert donchian.get_parameters()["lookback"] == 3
    assert "lookback" not in ema.get_parameters()


def test_positive_momentum_and_relative_strength_rank_are_deterministic():
    momentum = build("MOMENTUM", {"periods": [1, 2], "leadPeriod": 1})
    rising = [
        {"symbol": "ETHUSDT", "tradeDate": day, "open": close, "high": close, "low": close, "close": close,
         "closed": True, "signalTime": day + "T00:00:00Z"}
        for day, close in (("2026-11-01", "1"), ("2026-11-02", "2"), ("2026-11-03", "3"))
    ]
    assert momentum.generate_signal({"bars": rising})["signal"] == "BUY"
    strength = build("RELATIVE_STRENGTH", {"lookback": 1, "holdings": 1, "requireUptrend": False})
    panel = strength.generate_signal({
        "regime": "NEUTRAL",
        "assets": [
            {"symbol": "AAAUSDT", "bars": [{"close": "10"}, {"close": "12"}]},
            {"symbol": "BBBUSDT", "bars": [{"close": "10"}, {"close": "11"}]},
            {"symbol": "BTCUSDT", "bars": [{"close": "10"}, {"close": "10"}]},
        ],
    })
    chosen = {row["symbol"]: row["signal"] for row in panel}
    assert chosen == {"AAAUSDT": "BUY", "BBBUSDT": "NO_TRADE", "BTCUSDT": "NO_TRADE"}
    assert Decimal(next(row["score"] for row in panel if row["symbol"] == "AAAUSDT")) == Decimal("0.2")
    filtered = build("RELATIVE_STRENGTH", {"lookback": 1, "holdings": 1, "requireUptrend": True, "emaPeriod": 1})
    blocked = {row["symbol"]: row["signal"] for row in filtered.generate_signal({
        "assets": [
            {"symbol": "AAAUSDT", "bars": [{"close": "10"}, {"close": "12"}]},
            {"symbol": "BTCUSDT", "bars": [{"close": "10"}, {"close": "10"}]},
        ],
    })}
    assert blocked == {"AAAUSDT": "NO_TRADE", "BTCUSDT": "NO_TRADE"}
