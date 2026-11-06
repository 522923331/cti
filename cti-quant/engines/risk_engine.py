"""Position size, correlation cluster, and drawdown budget. Missing correlation is not zero."""

import math
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP

from indicators.volatility import sample_std

TWELVE = Decimal("0.000000000001")


def D(value) -> Decimal:
    return Decimal(str(value))


def q(value: Decimal) -> Decimal:
    return value.quantize(TWELVE, rounding=ROUND_HALF_UP)


def money_text(value: Decimal | None) -> str | None:
    if value is None:
        return None
    return format(q(value), "f")


def drawdown_policy(drawdown: Decimal, config: dict) -> tuple[Decimal, bool]:
    """drawdown is equity / peak - 1. The deepest breached band wins."""
    scale = Decimal("1")
    pause = False
    for band in sorted(config["risk"]["drawdown"], key=lambda item: D(item["drawdown"])):
        if drawdown <= -D(band["drawdown"]):
            scale = D(band["riskBudgetScale"])
            pause = bool(band["pauseNewEntries"])
    return scale, pause


def pearson(pairs: list[tuple[float, float]]) -> float | None:
    count = len(pairs)
    if count < 2:
        return None
    xs = [left for left, _ in pairs]
    ys = [right for _, right in pairs]
    mean_x = sum(xs) / count
    mean_y = sum(ys) / count
    covariance = sum((left - mean_x) * (right - mean_y) for left, right in pairs)
    spread_x = math.sqrt(sum((value - mean_x) ** 2 for value in xs))
    spread_y = math.sqrt(sum((value - mean_y) ** 2 for value in ys))
    if spread_x == 0 or spread_y == 0:
        return None
    return covariance / (spread_x * spread_y)


def correlation(left: dict[date, float], right: dict[date, float], day: date, window: int) -> float | None:
    """Both series need a log return on each of the last `window` calendar days."""
    pairs: list[tuple[float, float]] = []
    for offset in range(window):
        current = day - timedelta(days=window - 1 - offset)
        first = left.get(current)
        second = right.get(current)
        if first is None or second is None:
            return None
        pairs.append((first, second))
    return pearson(pairs)


def log_returns(dates: list[date], closes: list[float]) -> dict[date, float]:
    out: dict[date, float] = {}
    for index in range(1, len(closes)):
        if dates[index] - dates[index - 1] != timedelta(days=1):
            continue
        previous = closes[index - 1]
        current = closes[index]
        if previous <= 0 or current <= 0:
            continue
        out[dates[index]] = math.log(current / previous)
    return out


def cluster_weight(symbol: str, held: dict[str, Decimal], correlations: dict[frozenset[str], float | None],
                   threshold: Decimal) -> tuple[Decimal, list[str]]:
    total = Decimal("0")
    mates: list[str] = []
    for other, weight in held.items():
        if other == symbol:
            continue
        value = correlations.get(frozenset((symbol, other)))
        if value is not None and D(value) >= threshold:
            total += weight
            mates.append(other)
    return total, sorted(mates)


def size_entry(symbol: str, close: float, atr: float | None, equity: Decimal, cash: Decimal,
               held_weights: dict[str, Decimal], held_values: dict[str, Decimal], correlations: dict,
               regime_limit: Decimal | None, asset_class: str, derivatives_missing: bool,
               scale: Decimal, pause: bool, config: dict) -> tuple[Decimal | None, Decimal | None, dict]:
    """Return quote value, risk amount, and the explanation. None quote means do not open."""
    risk = config["risk"]
    info: dict = {"pause": pause, "scale": format(scale, "f"), "derivatives": "ABSENT" if derivatives_missing else "PRESENT"}
    if pause or equity <= 0 or atr is None or atr <= 0 or close <= 0 or regime_limit is None:
        info["blocked"] = "PAUSED" if pause else "MISSING_RISK_INPUT"
        return None, None, info
    distance = q(D(atr) * D(risk["atrStopMultiplier"]))
    if distance <= 0 or D(close) <= distance:
        info["blocked"] = "STOP_DISTANCE"
        return None, None, info
    budget = q(equity * D(risk["riskPerTrade"]) * scale)
    risk_quote = q(budget / distance * D(close))
    exposure_cap = min(D(risk["maxTotalExposure"]), Decimal("1") - D(risk["minCashReserve"]), regime_limit)
    used_value = sum(held_values.values(), Decimal("0"))
    room_exposure = q(equity * exposure_cap - used_value)
    room_cash = q(cash - equity * D(risk["minCashReserve"]))
    unit_cash = D("1") + D(config["execution"]["commissionRate"])
    if unit_cash <= 0:
        info["blocked"] = "COMMISSION"
        return None, None, info
    cash_quote = q(room_cash / unit_cash) if room_cash > 0 else Decimal("0")
    class_cap = {
        "BTC": D(risk["maxBtcWeight"]),
        "ETH": D(risk["maxEthWeight"]),
        "ALT": D(risk["maxAltcoinExposure"]),
    }[asset_class]
    if asset_class == "ALT":
        class_used = sum((value for name, value in held_values.items() if _class_of(name) == "ALT"), Decimal("0"))
    else:
        class_used = held_values.get(symbol, Decimal("0"))
    room_class = q(equity * class_cap - class_used)
    mates_weight, mates = cluster_weight(symbol, held_weights, correlations, D(risk["correlationCluster"]))
    room_cluster = q(equity * (D(risk["maxSingleAssetWeight"]) - mates_weight))
    room_single = q(equity * D(risk["maxSingleAssetWeight"]) - held_values.get(symbol, Decimal("0")))
    quote = min(risk_quote, room_exposure, cash_quote, room_class, room_cluster, room_single)
    if derivatives_missing:
        quote = q(quote * D(risk["warningPositionScale"]))
    else:
        quote = q(quote)
    info.update({
        "riskQuote": format(risk_quote, "f"),
        "clusterMates": mates,
        "clusterWeight": format(mates_weight, "f"),
        "exposureCap": format(exposure_cap, "f"),
    })
    floor = q(equity * D(risk["minPositionWeight"]))
    if quote < floor:
        info["blocked"] = "BELOW_MIN_WEIGHT"
        return None, None, info
    actual_risk = q(quote / D(close) * distance)
    info["quote"] = format(quote, "f")
    return quote, actual_risk, info


def asset_class(symbol: str) -> str:
    return _class_of(symbol)


def _class_of(symbol: str) -> str:
    if symbol == "BTCUSDT":
        return "BTC"
    if symbol == "ETHUSDT":
        return "ETH"
    return "ALT"


def build_risk(equity_rows: list, positions: list, return_maps: dict[str, dict[date, float]], config: dict) -> dict:
    """equity_rows are chronological objects with equity, cash, peak_equity, drawdown, exposure, trade_date."""
    risk = config["risk"]
    if not equity_rows:
        scale, pause = drawdown_policy(Decimal("0"), config)
        return {
            "asOfDate": None,
            "equity": None,
            "cash": None,
            "peakEquity": None,
            "drawdown": None,
            "exposure": None,
            "pauseNewEntries": pause,
            "riskBudgetScale": format(scale, "f"),
            "volatility30d": None,
            "maxDrawdown": None,
            "weights": {},
            "correlation30d": {},
            "riskContribution": None,
        }
    last = equity_rows[-1]
    scale, pause = drawdown_policy(last.drawdown, config)
    peak = last.equity
    max_drawdown = Decimal("0")
    for row in equity_rows:
        if row.peak_equity > peak:
            peak = row.peak_equity
        if row.drawdown < max_drawdown:
            max_drawdown = row.drawdown
    returns: list[float] = []
    for index in range(1, len(equity_rows)):
        previous = equity_rows[index - 1].equity
        current = equity_rows[index].equity
        if previous > 0:
            returns.append(float(current / previous - 1))
    window = int(risk["correlationWindow"])
    volatility = None
    if len(returns) >= window:
        deviation = sample_std(returns[-window:])
        if deviation is not None:
            volatility = format(q(D(deviation * math.sqrt(365))), "f")
    weights = {}
    if last.equity > 0:
        for position in positions:
            weights[position.symbol] = format(q(position.market_value / last.equity), "f")
    names = sorted(return_maps)
    matrix = {}
    as_of = last.trade_date
    for left_index, left in enumerate(names):
        for right in names[left_index + 1:]:
            value = correlation(return_maps[left], return_maps[right], as_of, window)
            if value is not None:
                matrix[f"{left}|{right}"] = format(q(D(value)), "f")
    contribution = _contribution(positions, return_maps, as_of, window, last.equity)
    return {
        "asOfDate": as_of.isoformat(),
        "equity": money_text(last.equity),
        "cash": money_text(last.cash),
        "peakEquity": money_text(last.peak_equity),
        "drawdown": money_text(last.drawdown),
        "exposure": money_text(last.exposure),
        "pauseNewEntries": pause,
        "riskBudgetScale": format(scale, "f"),
        "volatility30d": volatility,
        "maxDrawdown": money_text(max_drawdown),
        "weights": weights,
        "correlation30d": matrix,
        "riskContribution": contribution,
    }


def _contribution(positions, return_maps, as_of: date, window: int, equity: Decimal):
    if equity <= 0 or not positions:
        return {}
    dates = [as_of - timedelta(days=window - 1 - offset) for offset in range(window)]
    series = {}
    for position in positions:
        values = return_maps.get(position.symbol, {})
        row = []
        for current in dates:
            value = values.get(current)
            if value is None:
                return None
            row.append(value)
        series[position.symbol] = row
    weights = {position.symbol: float(position.market_value / equity) for position in positions}
    names = sorted(series)
    count = len(dates)
    means = {name: sum(series[name]) / count for name in names}
    covariance = {}
    for left in names:
        for right in names:
            covariance[(left, right)] = sum(
                (series[left][index] - means[left]) * (series[right][index] - means[right]) for index in range(count)
            ) / (count - 1)
    variance = 0.0
    for left in names:
        for right in names:
            variance += weights[left] * weights[right] * covariance[(left, right)]
    if variance <= 0:
        return None
    sigma = math.sqrt(variance)
    out = {}
    for name in names:
        marginal = sum(weights[other] * covariance[(name, other)] for other in names)
        out[name] = format(q(D(weights[name] * marginal / sigma)), "f")
    return out
