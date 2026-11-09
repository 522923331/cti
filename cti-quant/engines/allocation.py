from decimal import Decimal, ROUND_HALF_UP

SCALE = Decimal("0.000000000001")
REQUIRED = (
    "maxSingleAssetWeight",
    "maxAltcoinWeight",
    "maxBtcEthWeight",
    "minCashWeight",
    "riskBudgetPerTrade",
    "stopAtrMultiplier",
    "scoreThreshold",
    "volatilityFloor",
    "minTradeValue",
    "feeRate",
    "slippageBps",
)


class AllocationRejected(Exception):
    pass


def allocate(payload):
    config = _config(payload.get("config"))
    cash = _positive(payload.get("cash"), allow_zero=True)
    regime = payload.get("regime")
    if regime not in ("BULLISH", "NEUTRAL", "BEARISH"):
        raise AllocationRejected("市场状态不能识别")
    assets = [_asset(row) for row in payload.get("assets") or []]
    equity = cash + sum(asset["current"] for asset in assets)
    if regime == "BEARISH":
        held = sum(asset["current"] for asset in assets)
        return _plan(_ratio(equity - held, equity), [], "regime_bearish")
    weights = _weights(assets, config)
    values = _values(assets, weights, equity, config)
    _limit_new_buys(assets, values, cash, equity, config)
    targets = []
    invested = Decimal(0)
    for asset in sorted(assets, key=lambda row: row["symbol"]):
        if weights[asset["symbol"]] == 0 and values[asset["symbol"]] == 0:
            continue
        value = values[asset["symbol"]]
        invested += value
        targets.append({
            "symbol": asset["symbol"],
            "group": asset["group"],
            "targetWeight": _text(_ratio(value, equity)),
            "targetValue": _text(value),
        })
    return _plan(_ratio(equity - invested, equity), targets, "allocated")


def correlations(series):
    names = sorted(series)
    matrix = {}
    for left_index, left in enumerate(names):
        for right in names[left_index + 1:]:
            paired = _paired(series[left], series[right])
            matrix[left + "|" + right] = {
                "observations": len(paired),
                "value": None if len(paired) < 2 else _correlation(paired),
            }
    return matrix


def _weights(assets, config):
    raw = {}
    for asset in assets:
        raw[asset["symbol"]] = max(Decimal(0), asset["trend"] - config["scoreThreshold"]) / max(
            asset["volatility"], config["volatilityFloor"])
    weights = {symbol: Decimal(0) for symbol in raw}
    total = sum(raw.values())
    if total == 0:
        return weights
    investable = Decimal(1) - config["minCashWeight"]
    for symbol, value in raw.items():
        weights[symbol] = value / total * investable
    single = config["maxSingleAssetWeight"]
    for symbol in weights:
        if weights[symbol] > single:
            weights[symbol] = single
    _cap(weights, [asset["symbol"] for asset in assets if asset["group"] in ("BTC", "ETH")], config["maxBtcEthWeight"])
    _cap(weights, [asset["symbol"] for asset in assets if asset["group"] == "ALT"], config["maxAltcoinWeight"])
    return weights


def _values(assets, weights, equity, config):
    budget = equity * config["riskBudgetPerTrade"]
    single_value = config["maxSingleAssetWeight"] * equity
    values = {}
    for asset in assets:
        stop = asset["atr"] * config["stopAtrMultiplier"]
        if stop <= 0:
            raise AllocationRejected("止损距离必须大于 0")
        risk_value = budget / stop * asset["price"]
        desired = min(weights[asset["symbol"]] * equity, single_value, risk_value)
        if asset["maxValue"] is not None:
            desired = min(desired, asset["maxValue"])
        values[asset["symbol"]] = _q(desired)
    return values


def _limit_new_buys(assets, values, cash, equity, config):
    spendable = max(Decimal(0), cash - config["minCashWeight"] * equity)
    minimum = config["minTradeValue"]
    current = {asset["symbol"]: asset["current"] for asset in assets}
    for symbol in sorted(values):
        desired = values[symbol]
        held = current[symbol]
        if desired <= held:
            continue
        increment = desired - held
        if minimum > 0 and increment < minimum:
            values[symbol] = held
            continue
        if increment > spendable:
            values[symbol] = _q(held + spendable)
            spendable = Decimal(0)
        else:
            spendable -= increment


def _cap(weights, symbols, limit):
    ordered = sorted(symbols)
    total = sum((weights[symbol] for symbol in ordered), Decimal(0))
    if not ordered or total <= limit:
        return
    factor = limit / total
    consumed = Decimal(0)
    for symbol in ordered[:-1]:
        weights[symbol] = _q(weights[symbol] * factor)
        consumed += weights[symbol]
    weights[ordered[-1]] = _q(limit - consumed)


def _config(config):
    if not isinstance(config, dict):
        raise AllocationRejected("风险配置缺失")
    parsed = {}
    for key in REQUIRED:
        if key not in config or config[key] is None:
            raise AllocationRejected("风险配置不完整")
        parsed[key] = _positive(config[key], allow_zero=key in ("minTradeValue", "feeRate", "slippageBps"))
    if parsed["minCashWeight"] >= 1:
        raise AllocationRejected("现金比例必须小于 1")
    return parsed


def _asset(row):
    group = row.get("group")
    if group not in ("BTC", "ETH", "ALT"):
        raise AllocationRejected("资产分组不能识别")
    volatility = _required(row.get("volatility"))
    if volatility < 0:
        raise AllocationRejected("波动率异常")
    return {
        "symbol": row.get("symbol"),
        "group": group,
        "trend": _required(row.get("trendScore")),
        "volatility": volatility,
        "price": _positive(row.get("price"), allow_zero=False),
        "atr": _positive(row.get("atr"), allow_zero=False),
        "current": _positive(row.get("currentValue"), allow_zero=True),
        "maxValue": None if row.get("maxValue") is None else _positive(row.get("maxValue"), allow_zero=True),
    }


def _paired(left, right):
    right_by_date = {day: Decimal(value) for day, value in right}
    return [(Decimal(value), right_by_date[day]) for day, value in left if day in right_by_date]


def _correlation(paired):
    count = Decimal(len(paired))
    left_mean = sum(left for left, _right in paired) / count
    right_mean = sum(right for _left, right in paired) / count
    left_dev = [left - left_mean for left, _right in paired]
    right_dev = [right - right_mean for _left, right in paired]
    denominator = count - 1
    left_scale = _root(sum(value * value for value in left_dev) / denominator)
    right_scale = _root(sum(value * value for value in right_dev) / denominator)
    if left_scale == 0 or right_scale == 0:
        return None
    covariance = sum(left * right for left, right in zip(left_dev, right_dev)) / denominator
    return _text(covariance / (left_scale * right_scale))


def _root(value):
    if value == 0:
        return Decimal(0)
    return value.sqrt()


def _plan(cash_weight, targets, reason):
    return {"cashWeight": _text(Decimal(cash_weight)), "targets": targets, "reason": reason}


def _ratio(numerator, denominator):
    if denominator == 0:
        raise AllocationRejected("权益必须大于 0")
    return numerator / denominator


def _required(value):
    if value is None:
        raise AllocationRejected("评分或波动率缺失")
    return Decimal(str(value))


def _positive(value, allow_zero):
    if value is None:
        raise AllocationRejected("金额缺失")
    parsed = Decimal(str(value))
    if parsed < 0 or (parsed == 0 and not allow_zero):
        raise AllocationRejected("金额必须大于 0")
    return parsed


def _q(value):
    return Decimal(value).quantize(SCALE, rounding=ROUND_HALF_UP)


def _text(value):
    return format(_q(value), "f")
