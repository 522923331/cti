"""Run one strategy over a closed-bar window. Bars after `end` are removed before any decision."""

from copy import deepcopy
from datetime import date, timedelta
from decimal import Decimal

from dataclasses import replace

from engines.benchmarks import buy_and_hold, ema_cross, momentum
from engines.book import run_book
from engines.performance import acceptance, evaluate
from engines.risk_engine import D, money_text, q
from engines.signal_models import EquityPoint
from engines.signal_rules import prior_highs

STRATEGIES = {
    "CTI_TREND_V1": ("ALL", "score"),
    "TREND_PULLBACK_V1": ("PULLBACK", "score"),
    "TREND_BREAKOUT_V1": ("BREAKOUT", "score"),
    "MARKET_FILTER_TREND": ("MARKET_FILTER", "score"),
    "BASELINE_BTC_HOLD": None,
    "BASELINE_BTC_EMA50_200": None,
    "BASELINE_BTC_EMA20_50": None,
    "BASELINE_TSMOM_14": None,
}


def apply_scenario(config: dict, name: str) -> dict:
    match = next((item for item in config["costScenarios"] if item["name"] == name), None)
    if match is None:
        raise ValueError("成本情景无法识别")
    updated = deepcopy(config)
    half = D(match["oneWayTotalCost"]) / 2
    updated["execution"]["commissionRate"] = float(half)
    updated["execution"]["slippageRate"] = float(half)
    updated["risk"]["accountCapital"] = config["execution"].get("initialCapital", config["risk"]["accountCapital"])
    return updated


def run_strategy(days: dict, regimes: dict, config: dict, strategy: str, start: date | None, end: date | None,
                 cost_scenario: str, feature_version: str, strategy_version: str):
    if strategy not in STRATEGIES:
        raise ValueError("策略无法识别")
    window_days, window_regimes = _clip(days, regimes, end)
    priced = apply_scenario(config, cost_scenario)
    if STRATEGIES[strategy] is None:
        equity, fills = _baseline(strategy, window_days, priced, start)
        signals = []
    else:
        mode, order = STRATEGIES[strategy]
        blotter = []
        signals, _account, _positions, equity, _risk = run_book(
            window_days, window_regimes, priced, feature_version, strategy_version, strategy,
            setup_mode=mode, entry_order=order, start_date=start, blotter=blotter,
        )
        fills = blotter
        if start is not None:
            equity = [point for point in equity if point.trade_date >= start]
            fills = [fill for fill in fills if fill.trade_date >= start]
            signals = [row for row in signals if row.trade_date >= start]
    benchmark_equity, _benchmark_fills = buy_and_hold(
        _btc(window_days, start), D(priced["risk"]["accountCapital"]),
        D(priced["execution"]["commissionRate"]), D(priced["execution"]["slippageRate"]),
    )
    initial = D(priced["risk"]["accountCapital"])
    benchmark_return = _total(benchmark_equity, initial)
    metrics = evaluate(equity, fills, benchmark_return, initial)
    report = {
        "strategy": strategy,
        "costScenario": cost_scenario,
        "commissionRate": money_text(D(priced["execution"]["commissionRate"])),
        "slippageRate": money_text(D(priced["execution"]["slippageRate"])),
        "entryOrder": "score" if STRATEGIES[strategy] else "baseline",
        "initialCapital": money_text(D(priced["risk"]["accountCapital"])),
        "acceptance": acceptance(metrics, "IN_SAMPLE"),
        "notes": [
            "成交使用信号日之后的下一根日线开盘价。",
            "单边总成本拆成等额手续费和滑点。",
            "基准是同一窗口的 BTC 买入持有。",
        ],
    }
    return equity, fills, signals, metrics, report


def walk_forward(days, regimes, config, start, end, cost_scenario, feature_version, strategy_version):
    first, last = _span(days, start, end)
    folds = []
    if first is not None:
        cursor = first
        while True:
            train_end = _add_months(cursor, 24)
            validate_end = _add_months(train_end, 3)
            test_end = _add_months(validate_end, 3)
            if test_end > last:
                break
            folds.append({
                "trainStart": cursor.isoformat(),
                "trainEnd": train_end.isoformat(),
                "validateEnd": validate_end.isoformat(),
                "testStart": (validate_end + timedelta(days=1)).isoformat(),
                "testEnd": test_end.isoformat(),
            })
            cursor = _add_months(cursor, 3)
    status = "INSUFFICIENT_SAMPLE" if not folds else "SUCCEEDED"
    stitched: list[EquityPoint] = []
    if folds:
        for fold in folds:
            test_start = date.fromisoformat(fold["testStart"])
            test_end = date.fromisoformat(fold["testEnd"])
            equity, fills, _signals, metrics, _report = run_strategy(
                days, regimes, config, "CTI_TREND_V1", test_start, test_end, cost_scenario,
                feature_version, strategy_version,
            )
            fold["metrics"] = metrics
            fold["trades"] = len([fill for fill in fills if fill.pnl is not None])
            stitched = _chain(stitched, equity)
    metrics = evaluate(stitched, []) if stitched else evaluate([], [])
    return {
        "status": status,
        "startDate": None if first is None else first.isoformat(),
        "endDate": None if last is None else last.isoformat(),
        "sample": "OUT_OF_SAMPLE" if folds else "INSUFFICIENT_SAMPLE",
        "folds": folds,
        "metrics": metrics,
        "acceptance": acceptance(metrics, "OUT_OF_SAMPLE" if folds else "IN_SAMPLE"),
        "reason": None if folds else "历史长度不够 24 个月训练、3 个月验证和 3 个月测试",
    }


def sensitivity(days, regimes, config, start, end, feature_version, strategy_version):
    """One knob at a time. EMA and momentum stay on the price baselines."""
    groups = []
    groups.append(_knob(days, regimes, config, start, end, feature_version, strategy_version,
                        "atrStopMultiplier", ["1.5", "2", "2.5", "3"], _atr))
    groups.append(_knob(days, regimes, config, start, end, feature_version, strategy_version,
                        "entryScore", ["60", "65", "70", "75"], _entry_score))
    groups.append(_knob(days, regimes, config, start, end, feature_version, strategy_version,
                        "breakoutVolumeRatio", ["1.2", "1.5", "1.8", "2.0"], _volume))
    groups.append(_lookback(days, regimes, config, start, end, feature_version, strategy_version))
    groups.append(_ema_grid(days, config, start, end))
    groups.append(_momentum_grid(days, config, start, end))
    first, last = _span(days, start, end)
    return {
        "startDate": None if first is None else first.isoformat(),
        "endDate": None if last is None else last.isoformat(),
        "groups": groups,
        "note": "EMA 和动量周期只重算价格基准，不改写已落库的特征版本。",
    }


def _knob(days, regimes, config, start, end, feature_version, strategy_version, name, values, edit):
    rows = []
    for value in values:
        updated = edit(deepcopy(config), value)
        _equity, _fills, _signals, metrics, _report = run_strategy(
            days, regimes, updated, "CTI_TREND_V1", start, end, "BASE", feature_version, strategy_version,
        )
        rows.append({"value": value, "totalReturn": metrics["totalReturn"], "maxDrawdown": metrics["maxDrawdown"],
                     "sharpeRatio": metrics["sharpeRatio"], "totalTrades": metrics["totalTrades"]})
    return {"parameter": name, "rows": rows}


def _atr(config, value):
    config["risk"]["atrStopMultiplier"] = float(value)
    return config


def _entry_score(config, value):
    config["entry"]["pullbackScore"] = float(value)
    config["entry"]["breakoutScore"] = float(value)
    return config


def _volume(config, value):
    config["entry"]["breakoutVolumeRatio"] = float(value)
    return config


def _lookback(days, regimes, config, start, end, feature_version, strategy_version):
    rows = []
    for value in (10, 15, 20, 30):
        shifted = {}
        for symbol, bars in days.items():
            ordered = sorted(bars, key=lambda bar: bar.trade_date)
            dates = [bar.trade_date for bar in ordered]
            highs = prior_highs(dates, [bar.high for bar in ordered], value)
            shifted[symbol] = [replace(bar, prior_high_20=highs[index]) for index, bar in enumerate(ordered)]
        _equity, _fills, _signals, metrics, _report = run_strategy(
            shifted, regimes, config, "CTI_TREND_V1", start, end, "BASE", feature_version, strategy_version,
        )
        rows.append({"value": str(value), "totalReturn": metrics["totalReturn"], "maxDrawdown": metrics["maxDrawdown"],
                     "sharpeRatio": metrics["sharpeRatio"], "totalTrades": metrics["totalTrades"]})
    return {"parameter": "breakoutLookback", "rows": rows}


def _ema_grid(days, config, start, end):
    bars = [bar for bar in _btc(days, None) if end is None or bar.trade_date <= end]
    capital = D(config["execution"]["initialCapital"])
    commission = D("0.001")
    slippage = D("0.001")
    rows = []
    for fast in (10, 15, 20, 25):
        for slow in (40, 50, 60, 80):
            if fast >= slow:
                continue
            equity, fills = ema_cross(bars, fast, slow, capital, commission, slippage, start)
            metrics = evaluate(equity, fills)
            rows.append({"value": f"{fast}/{slow}", "totalReturn": metrics["totalReturn"],
                         "maxDrawdown": metrics["maxDrawdown"], "sharpeRatio": metrics["sharpeRatio"],
                         "totalTrades": metrics["totalTrades"]})
    return {"parameter": "baselineEma", "rows": rows}


def _momentum_grid(days, config, start, end):
    bars = [bar for bar in _btc(days, None) if end is None or bar.trade_date <= end]
    capital = D(config["execution"]["initialCapital"])
    rows = []
    for period in (7, 10, 14, 21):
        equity, fills = momentum(bars, period, capital, D("0.001"), D("0.001"), start)
        metrics = evaluate(equity, fills)
        rows.append({"value": str(period), "totalReturn": metrics["totalReturn"],
                     "maxDrawdown": metrics["maxDrawdown"], "sharpeRatio": metrics["sharpeRatio"],
                     "totalTrades": metrics["totalTrades"]})
    return {"parameter": "baselineMomentum", "rows": rows}


def _baseline(strategy, days, config, start):
    bars = _btc(days, None)
    capital = D(config["risk"]["accountCapital"])
    commission = D(config["execution"]["commissionRate"])
    slippage = D(config["execution"]["slippageRate"])
    if strategy == "BASELINE_BTC_HOLD":
        selected = [bar for bar in bars if (start is None or bar.trade_date >= start)]
        return buy_and_hold(selected, capital, commission, slippage)
    if strategy == "BASELINE_BTC_EMA50_200":
        return ema_cross(bars, 50, 200, capital, commission, slippage, start)
    if strategy == "BASELINE_BTC_EMA20_50":
        return ema_cross(bars, 20, 50, capital, commission, slippage, start)
    if strategy == "BASELINE_TSMOM_14":
        return momentum(bars, 14, capital, commission, slippage, start)
    raise ValueError("基准无法识别")


def _btc(days, start):
    bars = sorted(days.get("BTCUSDT", []), key=lambda bar: bar.trade_date)
    if start is None:
        return bars
    return bars


def _clip(days, regimes, end):
    if end is None:
        return days, regimes
    clipped = {
        symbol: [bar for bar in bars if bar.trade_date <= end]
        for symbol, bars in days.items()
    }
    return clipped, {day: row for day, row in regimes.items() if day <= end}


def _total(equity, initial: Decimal | None = None):
    start = None if not equity else equity[0].equity if initial is None else initial
    if start is None or start <= 0 or not equity:
        return None
    return q(equity[-1].equity / start - 1)


def _span(days, start, end):
    dates = sorted(bar.trade_date for bars in days.values() for bar in bars if (end is None or bar.trade_date <= end))
    if start is not None:
        dates = [day for day in dates if day >= start]
    if not dates:
        return None, None
    return dates[0], dates[-1]


def _chain(previous: list[EquityPoint], nxt: list[EquityPoint]) -> list[EquityPoint]:
    if not nxt:
        return previous
    if not previous:
        return list(nxt)
    base = nxt[0].equity
    scale = previous[-1].equity / base if base > 0 else Decimal("1")
    chained = list(previous)
    for point in nxt:
        equity = q(point.equity * scale)
        peak = max(chained[-1].peak_equity, equity)
        drawdown = q(equity / peak - 1) if peak > 0 else Decimal("0")
        chained.append(EquityPoint(point.trade_date, q(point.cash * scale), equity, peak, drawdown, point.exposure))
    return chained


def _add_months(day: date, months: int) -> date:
    month_index = day.month - 1 + months
    year = day.year + month_index // 12
    month = month_index % 12 + 1
    leap = year % 4 == 0 and (year % 100 != 0 or year % 400 == 0)
    lengths = [31, 29 if leap else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    return date(year, month, min(day.day, lengths[month - 1]))
