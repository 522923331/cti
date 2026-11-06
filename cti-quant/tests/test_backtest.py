from datetime import date, timedelta
from decimal import Decimal

from engines.backtest import run_strategy, walk_forward
from engines.benchmarks import buy_and_hold
from engines.performance import acceptance, evaluate
from engines.risk_engine import D
from engines.signal_models import EquityPoint, Fill
from tests.test_signals import config, day, neutral


def market():
    bars = [day(0), day(1, open=100, close=100), day(2, open=1, close=1, low=1, high=2)]
    regimes = {bar.trade_date: neutral() for bar in bars}
    return {"ETHUSDT": bars, "BTCUSDT": [day(offset, close=100, open=100, volume_ratio_20d=1, prior_high_20=10**9, total_score=10, trend_score=10) for offset in range(3)]}, regimes


def test_end_date_hides_the_next_open_and_repeat_matches():
    days, regimes = market()
    settings = config()
    short = run_strategy(days, regimes, settings, "CTI_TREND_V1", None, date(2024, 1, 2), "BASE", "features-1.0.0", "1.0.0")
    full = run_strategy(days, regimes, settings, "CTI_TREND_V1", None, date(2024, 1, 3), "BASE", "features-1.0.0", "1.0.0")
    again = run_strategy(days, regimes, settings, "CTI_TREND_V1", None, date(2024, 1, 2), "BASE", "features-1.0.0", "1.0.0")
    assert [point.trade_date for point in short[0]] == [date(2024, 1, 1), date(2024, 1, 2)]
    assert [point.equity for point in short[0]] == [point.equity for point in again[0]]
    assert full[0][-1].equity < short[0][-1].equity
    assert any(fill.side == "SELL" and fill.open_price == D("1") for fill in full[1])
    assert acceptance(short[3], "IN_SAMPLE")["passed"] is False


def test_buy_and_hold_uses_the_open_and_counts_cost():
    bars = [day(0, open=100, close=100), day(1, open=100, close=110)]
    equity, fills = buy_and_hold(bars, Decimal("100000"), Decimal("0"), Decimal("0.001"))
    assert fills[0].fill_price == Decimal("100.100000000000")
    assert fills[0].slippage > 0
    assert equity[-1].equity > Decimal("100000")
    metrics = evaluate(equity, fills, Decimal("0"))
    assert metrics["totalFee"] == "0.000000000000"
    assert metrics["totalSlippage"] != "0.000000000000"
    assert metrics["excessReturn"] == metrics["totalReturn"]


def test_walk_forward_reports_a_short_sample():
    days, regimes = market()
    result = walk_forward(days, regimes, config(), None, None, "BASE", "features-1.0.0", "1.0.0")
    assert result["status"] == "INSUFFICIENT_SAMPLE"
    assert result["folds"] == []
    assert result["acceptance"]["passed"] is False


def test_flat_equity_does_not_invent_a_sharpe_ratio():
    equity = [
        EquityPoint(date(2023, 1, 1), Decimal("100"), Decimal("100"), Decimal("100"), Decimal("0"), Decimal("0")),
        EquityPoint(date(2023, 7, 1), Decimal("100"), Decimal("100"), Decimal("100"), Decimal("0"), Decimal("0")),
        EquityPoint(date(2024, 1, 1), Decimal("200"), Decimal("200"), Decimal("200"), Decimal("0"), Decimal("0")),
    ]
    win = Fill(date(2024, 1, 2), "ETHUSDT", "SELL", Decimal("1"), Decimal("10"), Decimal("12"), Decimal("0"), Decimal("0"), Decimal("2"), date(2024, 1, 1))
    loss = Fill(date(2024, 1, 3), "ETHUSDT", "SELL", Decimal("1"), Decimal("10"), Decimal("9"), Decimal("0"), Decimal("0"), Decimal("-1"), date(2024, 1, 2))
    metrics = evaluate(equity, [win, loss])
    assert metrics["totalReturn"] == "1.000000000000"
    assert metrics["annualizedReturn"] == "1.000000000000"
    assert metrics["maxDrawdown"] == "0.000000000000"
    assert metrics["winRate"] == "0.500000000000"
    assert metrics["profitFactor"] == "2.000000000000"
    assert metrics["totalTrades"] == 2
    assert metrics["averageHoldingDays"] == "1.000000000000"
    assert metrics["sharpeRatio"] is not None
