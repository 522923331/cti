"""Backtest statistics. A flat return series does not invent a Sharpe ratio."""

import math
from datetime import date
from decimal import Decimal

from engines.risk_engine import D, money_text, q
from indicators.volatility import sample_std

YEAR = Decimal("365")


def evaluate(equity: list, fills: list, benchmark_return: Decimal | None = None, initial: Decimal | None = None) -> dict:
    if len(equity) < 1:
        return _empty()
    start = equity[0].equity if initial is None else initial
    end = equity[-1].equity
    total = q(end / start - 1) if start > 0 else None
    span = (equity[-1].trade_date - equity[0].trade_date).days
    cagr = None
    if total is not None and span > 0 and end > 0 and start > 0:
        cagr = q(D(float((end / start) ** (YEAR / Decimal(span)) - 1)))
    returns = _returns(equity)
    deviation = sample_std(returns)
    volatility = q(D(deviation * math.sqrt(365))) if deviation is not None else None
    sharpe = None
    if deviation not in (None, 0) and returns:
        sharpe = q(D((sum(returns) / len(returns)) / deviation * math.sqrt(365)))
    downside = _downside(returns)
    sortino = None
    if downside not in (None, 0) and returns:
        sortino = q(D((sum(returns) / len(returns)) / downside * math.sqrt(365)))
    peak = start
    max_drawdown = Decimal("0")
    for point in equity:
        if point.equity > peak:
            peak = point.equity
        if peak > 0:
            drawdown = q(point.equity / peak - 1)
            if drawdown < max_drawdown:
                max_drawdown = drawdown
    closed = [fill for fill in fills if fill.pnl is not None]
    wins = [fill for fill in closed if fill.pnl > 0]
    losses = [fill for fill in closed if fill.pnl < 0]
    win_rate = q(D(len(wins) / len(closed))) if closed else None
    gross_profit = sum((fill.pnl for fill in wins), Decimal("0"))
    gross_loss = sum((-fill.pnl for fill in losses), Decimal("0"))
    profit_factor = q(gross_profit / gross_loss) if gross_loss > 0 else None
    holding = [ (fill.trade_date - fill.opened_on).days for fill in closed if fill.opened_on is not None ]
    average_holding = q(sum((D(days) for days in holding), Decimal("0")) / D(len(holding))) if holding else None
    traded = sum((fill.quantity * fill.fill_price for fill in fills), Decimal("0"))
    average_equity = sum((point.equity for point in equity), Decimal("0")) / D(len(equity))
    turnover = q(traded / average_equity) if average_equity > 0 else None
    fee = q(sum((fill.fee for fill in fills), Decimal("0")))
    slippage = q(sum((fill.slippage for fill in fills), Decimal("0")))
    excess = q(total - benchmark_return) if total is not None and benchmark_return is not None else None
    return {
        "totalReturn": money_text(total),
        "annualizedReturn": money_text(cagr),
        "annualizedVolatility": money_text(volatility),
        "sharpeRatio": money_text(sharpe),
        "sortinoRatio": money_text(sortino),
        "maxDrawdown": money_text(max_drawdown),
        "winRate": money_text(win_rate),
        "profitFactor": money_text(profit_factor),
        "totalTrades": len(closed),
        "averageHoldingDays": money_text(average_holding),
        "turnover": money_text(turnover),
        "totalFee": money_text(fee),
        "totalSlippage": money_text(slippage),
        "benchmarkReturn": money_text(benchmark_return),
        "excessReturn": money_text(excess),
    }


def acceptance(metrics: dict, sample: str) -> dict:
    """The published gates are out-of-sample gates. An in-sample run cannot pass them."""
    sharpe = _number(metrics.get("sharpeRatio"))
    factor = _number(metrics.get("profitFactor"))
    drawdown = _number(metrics.get("maxDrawdown"))
    trades = metrics.get("totalTrades") or 0
    out_of_sample = sample == "OUT_OF_SAMPLE"
    checks = {
        "outOfSampleSharpeAbove1": out_of_sample and sharpe is not None and sharpe > 1,
        "profitFactorAbove1_3": factor is not None and factor > D("1.3"),
        "maxDrawdownWithin20": drawdown is not None and drawdown > D("-0.20"),
        "tradesAtLeast100": trades >= 100,
        "costsIncluded": True,
        "outOfSampleReturnPositive": out_of_sample and _number(metrics.get("totalReturn")) is not None and _number(metrics.get("totalReturn")) > 0,
    }
    reasons = []
    if not out_of_sample:
        reasons.append("当前结果不是独立样本外窗口，不能认定策略通过")
    if trades < 100:
        reasons.append("已平仓交易少于 100")
    passed = out_of_sample and all(checks.values())
    return {"passed": passed, "sample": sample, "checks": checks, "reasons": reasons}


def _returns(equity) -> list[float]:
    out = []
    for index in range(1, len(equity)):
        previous = equity[index - 1].equity
        if previous > 0:
            out.append(float(equity[index].equity / previous - 1))
    return out


def _downside(returns: list[float]) -> float | None:
    if len(returns) < 2:
        return None
    squared = sum(min(value, 0.0) ** 2 for value in returns)
    if squared == 0:
        return 0.0
    return math.sqrt(squared / (len(returns) - 1))


def _number(value) -> Decimal | None:
    if value is None:
        return None
    return D(value)


def _empty() -> dict:
    keys = (
        "totalReturn", "annualizedReturn", "annualizedVolatility", "sharpeRatio", "sortinoRatio",
        "maxDrawdown", "winRate", "profitFactor", "averageHoldingDays", "turnover", "totalFee",
        "totalSlippage", "benchmarkReturn", "excessReturn",
    )
    return {key: None for key in keys} | {"totalTrades": 0}
