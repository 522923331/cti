from datetime import date, datetime, timedelta, timezone

from fastapi.testclient import TestClient

from api.routes import get_market_loader
from app.main import app
from data.market_loader import LoadedSeries, keep_closed, trade_date_of
from features.feature_builder import DailyBar, DerivativePoint, build_daily_features

client = TestClient(app)


def bar(day: int, close: float, quote: float | None = 100.0, taker: float | None = 60.0) -> DailyBar:
    moment = datetime(2024, 1, 1, tzinfo=timezone.utc) + timedelta(days=day)
    return DailyBar(moment.date(), int(moment.timestamp() * 1000), close + 1, close - 1, close, quote, taker)


def values(rows, name: str):
    return [getattr(row, name) for row in rows]


def test_closed_bars_only_and_trade_date_is_utc():
    assert trade_date_of(1_704_067_200_000) == date(2024, 1, 1)
    assert len(keep_closed([{"is_closed": 1}, {"is_closed": 0}, {"is_closed": True}])) == 2


def test_gap_restarts_warmup_and_future_bar_does_not_change_history():
    first = [bar(day, 1) for day in range(20)]
    second = [bar(day, 2) for day in range(22, 42)]
    rows = build_daily_features(first + second, [], "BTCUSDT", "NORMAL", "features-1.0.0")
    assert values(rows, "ema20")[19] == 1
    assert values(rows, "ema20")[-1] == 2
    assert values(rows, "ema200") == [None] * 40

    longer = build_daily_features(first + [bar(20, 50)], [], "BTCUSDT", "NORMAL", "features-1.0.0")
    assert values(longer, "ema20")[:20] == values(
        build_daily_features(first, [], "BTCUSDT", "NORMAL", "features-1.0.0"), "ema20")
    assert longer[19].ema20 == 1


def test_missing_derivatives_stay_null_and_exact_oi_gap_is_null():
    bars = [bar(day, 10 + day) for day in range(8)]
    points = [
        DerivativePoint(bars[0].trade_date, 100, 0.01),
        DerivativePoint(bars[1].trade_date, 110, 0.02),
        DerivativePoint(bars[3].trade_date, 121, 0.03),
    ]
    rows = build_daily_features(bars, points, "BTCUSDT", "NORMAL", "features-1.0.0")
    assert rows[1].oi_change_1d == (110 / 100 - 1)
    assert rows[2].oi_change_1d is None
    assert rows[3].oi_change_1d is None
    assert rows[1].funding_zscore is None
    assert rows[1].funding_rate == 0.02
    bare = build_daily_features(bars, [], "BTCUSDT", "INVALID", "features-1.0.0")
    assert all(row.oi_change_1d is None and row.funding_rate is None and row.funding_zscore is None for row in bare)
    assert bare[0].data_quality_status == "INVALID"


def test_zero_quote_volume_does_not_invent_a_ratio_and_rebuild_matches():
    bars = [bar(day, 10, quote=0, taker=0) for day in range(20)]
    once = build_daily_features(bars, [], "ETHUSDT", "NORMAL", "features-1.0.0")
    twice = build_daily_features(bars, [], "ETHUSDT", "NORMAL", "features-1.0.0")
    assert once == twice
    assert once[-1].spot_delta == 0
    assert once[-1].spot_delta_ratio is None
    assert once[-1].volume_ratio_20d is None
    assert once[-1].ema20 == 10


def test_calculate_drops_bars_after_the_requested_date():
    bars = [bar(day, 10) for day in range(20)] + [bar(20, 80)]

    def loader(url, symbols, trade_date):
        assert symbols == ["BTCUSDT"]
        return [LoadedSeries("BTCUSDT", "NORMAL", bars, [DerivativePoint(bar(21, 1).trade_date, 999, 0.5)])]

    app.dependency_overrides[get_market_loader] = lambda: loader
    try:
        response = client.post("/api/v1/features/daily/calculate", json={
            "tradeDate": "2024-01-20",
            "symbols": ["btcusdt"],
        })
    finally:
        app.dependency_overrides.clear()
    assert response.status_code == 200
    body = response.json()
    assert body["featureVersion"] == "features-1.0.0"
    assert len(body["rows"]) == 1
    assert body["rows"][0]["tradeDate"] == "2024-01-20"
    assert body["rows"][0]["ema20"] == "10.000000000000"
    assert body["rows"][0]["fundingZscore"] is None
    assert body["rows"][0]["oiChange1d"] is None


def test_calculate_rejects_an_empty_symbol_list():
    response = client.post("/api/v1/features/daily/calculate", json={"symbols": []})
    assert response.status_code == 422
