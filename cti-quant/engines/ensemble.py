from decimal import Decimal, ROUND_HALF_UP

SCALE = Decimal("0.000000000001")


class EnsembleRejected(Exception):
    pass


def classify_regime(observation, config, index=None):
    if isinstance(observation, list):
        if index is None:
            index = len(observation) - 1
        if index < 0 or index >= len(observation):
            raise EnsembleRejected("没有这根 K 线")
        observation = observation[index]
    threshold = _required(config.get("adxThreshold"), "ADX 门槛")
    high_line = _required(config.get("highVolatilityThreshold"), "高波动门槛")
    low_line = None if config.get("lowVolatilityThreshold") is None else _required(config.get("lowVolatilityThreshold"), "低波动门槛")
    close = _optional(observation.get("close"))
    ema50 = _optional(observation.get("ema50"))
    ema200 = _optional(observation.get("ema200"))
    adx = _optional(observation.get("adx"))
    direction = None
    if None not in (close, ema50, ema200, adx):
        if close > ema200 and ema50 > ema200 and adx >= threshold:
            direction = "BULL"
        elif close < ema200 and ema50 < ema200 and adx >= threshold:
            direction = "BEAR"
        else:
            direction = "SIDEWAYS"
    atr_percent = _optional(observation.get("atrPercent"))
    realized = _optional(observation.get("realizedVol"))
    volatility = None
    present = [value for value in (atr_percent, realized) if value is not None]
    if present:
        if any(value >= high_line for value in present):
            volatility = "HIGH"
        elif low_line is not None and all(value < low_line for value in present):
            volatility = "LOW"
        else:
            volatility = "NORMAL"
    return {"direction": direction, "volatility": volatility}


def combine(payload):
    weights = {name: _weight(value) for name, value in payload.get("weights", {}).items()}
    if not weights or sum(weights.values(), Decimal(0)) != 1:
        raise EnsembleRejected("策略权重之和必须等于 1")
    scores = payload.get("scores", {})
    valid = {name: weight for name, weight in weights.items() if scores.get(name) is not None}
    valid_total = sum(valid.values(), Decimal(0))
    contributions = []
    weighted_score = Decimal(0)
    for name in sorted(weights):
        if name not in valid:
            contributions.append({
                "strategy": name,
                "included": False,
                "weight": None,
                "score": None,
                "contribution": None,
            })
            continue
        weight = valid[name] / valid_total
        score = Decimal(str(scores[name]))
        contribution = weight * score
        weighted_score += contribution
        contributions.append({
            "strategy": name,
            "included": True,
            "weight": _text(weight),
            "score": _text(score),
            "contribution": _text(contribution),
        })
    direction = payload.get("direction")
    volatility = payload.get("volatility")
    score = None if valid_total == 0 else weighted_score
    buy_score = payload.get("buyScore")
    signal = "NO_TRADE"
    if score is not None and buy_score is not None and score >= Decimal(str(buy_score)) and direction == "BULL" and volatility not in (None, "HIGH"):
        signal = "BUY"
    return {
        "score": None if score is None else _text(score),
        "confidence": _text(valid_total),
        "direction": direction,
        "volatility": volatility,
        "signal": signal,
        "contributions": contributions,
    }


def _weight(value):
    parsed = Decimal(str(value))
    if parsed < 0 or parsed > 1:
        raise EnsembleRejected("策略权重必须在 0 和 1 之间")
    return parsed


def _required(value, label):
    if value is None:
        raise EnsembleRejected(label + "缺失")
    return Decimal(str(value))


def _optional(value):
    if value is None:
        return None
    return Decimal(str(value))


def _text(value):
    return format(Decimal(value).quantize(SCALE, rounding=ROUND_HALF_UP), "f")
