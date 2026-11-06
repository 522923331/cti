from datetime import date, timedelta
from decimal import Decimal

from engines.book import run_book
from engines.intraday import FOUR_MS, last_four_hour
from engines.risk_engine import drawdown_policy, size_entry
from engines.signal_models import RegimeView, SignalDay
from data.market_loader import trade_date_of
from engines.signal_rules import prior_highs, pullback_setup


def config(**overrides):
    base = {
        "indicators": {"emaSlopeLookback": 10, "zscoreWindow": 30},
        "entry": {
            "pullbackScore": 65,
            "breakoutScore": 70,
            "minimumTrendScore": 60,
            "breakoutTrendScore": 65,
            "breakoutVolumeRatio": 1.5,
            "breakoutLookback": 20,
            "pullbackAtrMultiplier4h": 0.5,
            "pullbackDailyAtrLower": 1.0,
            "pullbackDailyAtrUpper": 0.5,
            "spotDelta7dMinScore": -40,
            "watchTotalScore": 50,
        },
        "exit": {"timeReviewDays": 14, "timeSellBelowScore": 50, "timeReduceBelowScore": 65},
        "derivativesRisk": {"fundingZSoft": 2, "oiCrowdingChange7d": 0},
        "risk": {
            "accountCapital": 100000,
            "riskPerTrade": 0.005,
            "atrStopMultiplier": 2,
            "maxHoldingAssets": 5,
            "maxTotalExposure": 0.80,
            "maxSingleAssetWeight": 0.15,
            "maxBtcWeight": 0.50,
            "maxEthWeight": 0.35,
            "maxAltcoinExposure": 0.30,
            "minCashReserve": 0.20,
            "warningPositionScale": 0.7,
            "reduceFraction": 0.5,
            "minPositionWeight": 0.01,
            "correlationWindow": 30,
            "correlationCluster": 0.80,
            "drawdown": [
                {"drawdown": 0.05, "riskBudgetScale": 0.75, "pauseNewEntries": False},
                {"drawdown": 0.08, "riskBudgetScale": 0.50, "pauseNewEntries": False},
                {"drawdown": 0.12, "riskBudgetScale": 0.00, "pauseNewEntries": True},
            ],
        },
        "execution": {"commissionRate": 0, "slippageRate": 0, "quoteAsset": "USDT", "initialCapital": 100000},
        "costScenarios": [
            {"name": "LOW", "oneWayTotalCost": 0.0005},
            {"name": "BASE", "oneWayTotalCost": 0.0020},
            {"name": "HIGH", "oneWayTotalCost": 0.0040},
        ],
    }
    base.update(overrides)
    return base


def day(offset: int, **overrides) -> SignalDay:
    values = dict(
        open=100.0, high=101.0, low=99.0, close=100.0,
        ema20=90.0, ema50=80.0, atr14=2.0, ema50_slope=0.01,
        volume_ratio_20d=1.6, prior_high_20=90.0, spot_delta_7d_score=0.0,
        funding_zscore=0.0, oi_change_7d=0.0, return_7d=0.0, quality="NORMAL",
        trend_score=80.0, flow_score=10.0, total_score=80.0,
    )
    values.update(overrides)
    return SignalDay(trade_date=date(2024, 1, 1) + timedelta(days=offset), **values)


def neutral(limit=0.80):
    return RegimeView("NEUTRAL", limit, None)


def run(days, regimes=None, **overrides):
    if regimes is None:
        dates = {bar.trade_date for bars in days.values() for bar in bars}
        regimes = {current: neutral() for current in dates}
    return run_book(days, regimes, config(**overrides), "features-1.0.0", "1.0.0", "CTI_TREND_V1")


def test_prior_high_excludes_today_and_stops_at_a_gap():
    dates = [date(2024, 1, 1) + timedelta(days=offset) for offset in range(21)]
    highs = [float(value) for value in range(1, 22)]
    assert prior_highs(dates, highs, 20)[20] == 20
    gapped = dates.copy()
    gapped[20] = gapped[19] + timedelta(days=2)
    assert prior_highs(gapped, highs, 20)[20] is None


def test_four_hour_gap_restarts_ema():
    start = 1_704_067_200_000
    bars = [(start + index * FOUR_MS, 1.0, 1.0, 1.0) for index in range(20)]
    restart = bars[-1][0] + 2 * FOUR_MS
    bars.extend((restart + index * FOUR_MS, 2.0, 2.0, 2.0) for index in range(20))
    snapshot = last_four_hour(bars)
    assert snapshot[trade_date_of(bars[-1][0])][2] == 2


def test_breakout_buy_sizes_stop_and_does_not_repeat():
    signals, _account, positions, _equity, _risk = run({"ETHUSDT": [day(0)]})
    assert signals[0].signal_type == "BUY"
    assert signals[0].signal_status == "VALID"
    assert signals[0].signal_id == "1.0.0:ETHUSDT:2024-01-01:BUY:CTI_TREND_V1"
    assert signals[0].entry_price == Decimal("100.000000000000")
    assert signals[0].stop_price == Decimal("96.000000000000")
    assert signals[0].suggested_position == Decimal("0.125000000000")
    assert positions == []
    again, *_rest = run({"ETHUSDT": [day(0)]})
    assert [(row.signal_id, row.suggested_position) for row in again] == [
        (row.signal_id, row.suggested_position) for row in signals
    ]


def test_missing_derivatives_warn_and_scale_the_position():
    signals, *_rest = run({"ETHUSDT": [day(0, funding_zscore=None, oi_change_7d=None)]})
    assert signals[0].signal_status == "WARNING"
    assert signals[0].suggested_position == Decimal("0.087500000000")


def test_bearish_regime_blocks_a_breakout():
    current = day(0).trade_date
    signals, *_rest = run({"ETHUSDT": [day(0)]}, {current: RegimeView("BEARISH", 0.10, None)})
    assert signals[0].signal_type == "NO_TRADE"
    assert signals[0].signal_status == "VALID"


def test_invalid_quality_does_not_buy():
    signals, *_rest = run({"ETHUSDT": [day(0, quality="INVALID")]})
    assert signals[0].signal_type == "NO_TRADE"
    assert signals[0].signal_status == "INVALID"


def test_pullback_requires_spot_delta_score_and_can_watch():
    shared = dict(
        close=100.0, low=100.0, ema20=100.0, ema50=90.0, atr14=10.0,
        volume_ratio_20d=1.0, prior_high_20=200.0, trend_score=70.0, total_score=70.0,
        four_low=100.0, four_close=101.0, four_ema20=100.0, four_atr14=1.0,
    )
    bought, *_rest = run({"BTCUSDT": [day(0, spot_delta_7d_score=-40, **shared)]})
    assert bought[0].signal_type == "BUY"
    assert "TREND_PULLBACK_V1" in bought[0].reason["setups"]
    watched, *_rest = run({"BTCUSDT": [day(0, spot_delta_7d_score=None, **shared)]})
    assert watched[0].signal_type == "WATCH"
    failed, detail = pullback_setup(day(0, spot_delta_7d_score=-40.01, **shared), neutral(), day(0), config())
    assert failed is False
    assert detail["spotDelta7d"] == -40.01


def test_stop_trails_up_and_a_gap_exits_at_the_open():
    trail, _account, positions, _equity, _risk = run({"ETHUSDT": [
        day(0),
        day(1, open=100, close=110, high=111, low=99),
        day(2, open=120, close=120, high=121, low=119, atr14=10),
    ]})
    assert [row.signal_type for row in trail] == ["BUY", "HOLD", "HOLD"]
    assert trail[2].stop_price == Decimal("106.000000000000")
    assert positions[0].stop_price == Decimal("106.000000000000")

    gap, account, positions, _equity, _risk = run({"ETHUSDT": [
        day(0),
        day(1, open=100, close=100),
        day(2, open=90, close=110, high=112, low=89, prior_high_20=90, volume_ratio_20d=2),
    ]})
    assert [row.signal_type for row in gap] == ["BUY", "HOLD", "SELL"]
    assert gap[2].reason["reasons"] == ["GAP_STOP"]
    assert positions == []
    assert account["cash"] == "98750.000000000000"


def test_close_through_the_stop_sells_and_time_exit_reduces():
    stopped, _account, positions, _equity, _risk = run({"ETHUSDT": [day(0), day(1, open=100, close=90, low=89)]})
    assert stopped[1].signal_type == "SELL"
    assert "STOP" in stopped[1].reason["reasons"]
    assert positions[0].quantity == Decimal("125.000000000000")

    bars = [day(offset) for offset in range(15)]
    bars[14] = day(14, total_score=60)
    reduced, _account, positions, _equity, _risk = run({"ETHUSDT": bars + [day(15)]})
    assert reduced[14].signal_type == "REDUCE"
    assert reduced[14].reason["reasons"] == ["TIME_REDUCE"]
    assert positions[0].quantity == Decimal("62.500000000000")

    bars[14] = day(14, total_score=40)
    sold, *_rest = run({"ETHUSDT": bars})
    assert sold[14].signal_type == "SELL"
    assert "TIME_EXIT" in sold[14].reason["reasons"]


def test_same_day_buys_share_one_correlation_cluster():
    def quiet(offset, close):
        return day(offset, close=close, open=close, high=close + 1, low=close - 1,
                   volume_ratio_20d=1.0, prior_high_20=None, total_score=40, trend_score=40)

    def breaking(close):
        return day(2, close=close, open=close, high=close + 1, low=close - 1, prior_high_20=close - 1)

    left = [quiet(0, 100), quiet(1, 110), breaking(130)]
    right = [quiet(0, 100), quiet(1, 110), breaking(130)]
    btc = [quiet(0, 50), quiet(1, 55), quiet(2, 60)]
    settings = config()
    settings["risk"]["correlationWindow"] = 2
    signals, *_rest = run_book(
        {"AAAUSDT": left, "BBBUSDT": right, "BTCUSDT": btc},
        {bar.trade_date: neutral() for bar in left},
        settings, "features-1.0.0", "1.0.0", "CTI_TREND_V1",
    )
    last = {row.symbol: row for row in signals if row.trade_date == date(2024, 1, 3)}
    assert last["AAAUSDT"].signal_type == "BUY"
    assert last["AAAUSDT"].suggested_position == Decimal("0.150000000000")
    assert last["BBBUSDT"].signal_type == "WATCH"
    assert last["BBBUSDT"].reason["sizing"]["blocked"] == "BELOW_MIN_WEIGHT"


def test_drawdown_pause_and_a_later_bar_do_not_rewrite_history():
    scale, pause = drawdown_policy(Decimal("-0.12"), config())
    assert pause is True
    assert scale == Decimal("0")
    quote, _risk, info = size_entry(
        "ETHUSDT", 100, 2, Decimal("100000"), Decimal("100000"), {}, {}, {},
        Decimal("0.80"), "ETH", False, scale, pause, config(),
    )
    assert quote is None
    assert info["blocked"] == "PAUSED"
    first, *_rest = run({"ETHUSDT": [day(0), day(1)]})
    second, *_rest = run({"ETHUSDT": [day(0), day(1), day(2, close=1000, high=1001)]})
    assert [(row.signal_id, row.signal_type, row.stop_price) for row in first] == [
        (row.signal_id, row.signal_type, row.stop_price) for row in second[:2]
    ]
