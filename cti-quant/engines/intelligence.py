"""V1.1 trend, risk, sentiment, and confidence. Missing inputs stay missing."""

from decimal import Decimal, ROUND_HALF_UP

MODEL_VERSION = "intelligence-1.1.0"

_Q4 = Decimal("0.0001")
_Q6 = Decimal("0.000001")

_TREND_WEIGHTS = {
    "trend_structure": Decimal(25),
    "momentum": Decimal(15),
    "relative_strength": Decimal(15),
    "volume": Decimal(15),
    "market_environment": Decimal(15),
}
_DERIVATIVES_WEIGHT = Decimal(10)
_EVENT_WEIGHT = Decimal(5)
_TOTAL_WEIGHT = Decimal(100)
_RISK_WEIGHTS = {
    "volatility": Decimal(40),
    "derivatives": Decimal(30),
    "regime": Decimal(20),
    "quality": Decimal(10),
}


def evaluate(rows, params=None):
    params = _params(params)
    grouped = {}
    for row in rows:
        grouped.setdefault(row["tradeDate"], []).append(row)
    scored = []
    positions = {}
    for trade_date in sorted(grouped):
        day_rows = sorted(grouped[trade_date], key=lambda item: item["symbol"])
        strength = _relative_strength(day_rows)
        for row in day_rows:
            result = _score_row(row, strength.get(row["symbol"]), positions.get(row["symbol"], False), params)
            positions[row["symbol"]] = _next_position(positions.get(row["symbol"], False), result["signalType"])
            scored.append(result)
    return scored


def decide_signal(trend, confidence, quality, risk_level, regime, h4_confirmed, position_open, params):
    params = _params(params)
    if trend is None or confidence is None or confidence < Decimal(params["minimumConfidence"]):
        return "NO_TRADE", "core_data"
    if quality == "INVALID":
        return "NO_TRADE", "invalid_quality"
    if risk_level == "CRITICAL":
        return "NO_TRADE", "risk_critical"
    if trend >= Decimal(params["buyScore"]) and regime == "BEARISH":
        return "NO_TRADE", "bearish_regime"
    if trend >= Decimal(params["buyScore"]) and h4_confirmed is not True:
        return "NO_TRADE", "h4_not_confirmed"
    if trend >= Decimal(params["strongBuyScore"]):
        return "STRONG_BUY", "trend_strong"
    if trend >= Decimal(params["buyScore"]):
        return "BUY", "trend_buy"
    if Decimal(params["holdScore"]) <= trend < Decimal(params["buyScore"]):
        return "HOLD", "trend_hold"
    if Decimal(params["reduceScore"]) <= trend < Decimal(params["gapScore"]):
        if position_open:
            return "REDUCE", "trend_reduce"
        return "NO_TRADE", "flat_position"
    if trend < Decimal(params["exitScore"]):
        if position_open:
            return "EXIT", "trend_exit"
        return "NO_TRADE", "flat_position"
    return "NO_TRADE", "trend_between_rules"


def confirm_4h(bars):
    closes = [Decimal(str(bar["close"])) for bar in bars if bar.get("closed") is True and bar.get("close") is not None]
    period = 20
    if len(closes) < period:
        return False
    multiplier = Decimal(2) / Decimal(period + 1)
    ema = sum(closes[:period], Decimal(0)) / Decimal(period)
    for close in closes[period:]:
        ema = close * multiplier + ema * (Decimal(1) - multiplier)
    return closes[-1] > ema


def _score_row(row, relative_strength, position_open, params):
    structure = _trend_structure(_d(row.get("ema20")), _d(row.get("ema50")), _d(row.get("ema200")), _d(row.get("close")))
    momentum = _scale(_d(row.get("return30d")), Decimal("-0.30"), Decimal("0.30"))
    volume = _scale(_d(row.get("volumeRatio20d")), Decimal("0.5"), Decimal("2"))
    environment = _environment(row.get("regime"))
    parts = {
        "trend_structure": structure,
        "momentum": momentum,
        "relative_strength": relative_strength,
        "volume": volume,
        "market_environment": environment,
    }
    trend, trend_weight = _weighted(parts, _TREND_WEIGHTS)
    funding_z = _d(row.get("fundingZ20"))
    derivatives_risk = None if funding_z is None else _scale(abs(funding_z), Decimal(0), Decimal(3))
    volatility_risk = _scale(_d(row.get("realizedVolatility20d")), Decimal(0), Decimal(1))
    regime_risk = _regime_risk(row.get("regime"))
    quality_risk = _quality_risk(row.get("quality"))
    risk, _risk_weight = _weighted(
        {
            "volatility": volatility_risk,
            "derivatives": derivatives_risk,
            "regime": regime_risk,
            "quality": quality_risk,
        },
        _RISK_WEIGHTS,
    )
    news = _d(row.get("newsSentiment"))
    onchain = _d(row.get("onchainContext"))
    funding_sentiment = None if funding_z is None else _scale(funding_z, Decimal(-2), Decimal(2))
    sentiment, _sentiment_weight = _weighted(
        {"funding": funding_sentiment, "news": news, "onchain": onchain},
        {"funding": Decimal(1), "news": Decimal(1), "onchain": Decimal(1)},
    )
    valid = trend_weight
    if derivatives_risk is not None:
        valid += _DERIVATIVES_WEIGHT
    if news is not None or onchain is not None:
        valid += _EVENT_WEIGHT
    confidence = Decimal(0) if _TOTAL_WEIGHT == 0 else valid / _TOTAL_WEIGHT
    core_complete = all(part is not None for part in parts.values())
    if not core_complete:
        confidence = min(confidence, Decimal(params["coreCap"]))
    signal, signal_reason = decide_signal(
        trend, confidence, row.get("quality"), _risk_level(risk, params), row.get("regime"),
        row.get("h4Confirmed") is True, position_open, params,
    )
    if trend is None:
        status = "INSUFFICIENT"
    elif valid == _TOTAL_WEIGHT:
        status = "COMPLETE"
    else:
        status = "PARTIAL"
    return {
        "symbol": row["symbol"],
        "tradeDate": row["tradeDate"],
        "modelVersion": params["modelVersion"],
        "trendScore": _q4(trend),
        "riskScore": _q4(risk),
        "sentimentScore": _q4(sentiment),
        "dataConfidence": _q6(confidence),
        "riskLevel": _risk_level(risk, params),
        "signalType": signal,
        "dataStatus": status,
        "reason": {
            "coreComplete": core_complete,
            "signalReason": signal_reason,
            "components": {name: _q4(value) for name, value in parts.items()},
            "sources": {
                "trend_structure": ["ct_feature_daily", "ema20"],
                "momentum": ["ct_feature_daily", "return_30d"],
                "relative_strength": ["ct_feature_daily", "return_30d"],
                "volume": ["ct_feature_daily", "volume_ratio_20d"],
                "market_environment": ["ct_market_regime", "regime"],
            },
            "inputs": {
                "fundingZ20": None if funding_z is None else format(funding_z, "f"),
                "newsSentiment": _q4(news),
                "onchainContext": _q4(onchain),
                "h4Confirmed": row.get("h4Confirmed") is True,
                "quality": row.get("quality"),
                "regime": row.get("regime"),
            },
        },
    }


def _relative_strength(rows):
    values = {}
    for row in rows:
        value = _d(row.get("return30d"))
        if value is not None:
            values[row["symbol"]] = value
    if len(values) < 2:
        return {row["symbol"]: None for row in rows}
    ordered = sorted(values, key=lambda symbol: (-values[symbol], symbol))
    count = len(ordered)
    scores = {}
    index = 0
    while index < count:
        end = index
        while end + 1 < count and values[ordered[end + 1]] == values[ordered[index]]:
            end += 1
        score = Decimal(count - 1 - index) / Decimal(count - 1) * Decimal(100)
        for cursor in range(index, end + 1):
            scores[ordered[cursor]] = score
        index = end + 1
    return {row["symbol"]: scores.get(row["symbol"]) for row in rows}


def _weighted(parts, weights):
    valid = sum((weights[name] for name, value in parts.items() if value is not None), Decimal(0))
    if valid == 0:
        return None, Decimal(0)
    total = sum((parts[name] * weights[name] for name, value in parts.items() if value is not None), Decimal(0))
    return total / valid, valid


def _trend_structure(ema20, ema50, ema200, close):
    if ema20 is None or ema50 is None or ema200 is None or close is None:
        return None
    if ema20 > ema50 > ema200 and close > ema20:
        return Decimal(100)
    if ema20 > ema50 > ema200:
        return Decimal(80)
    if ema50 > ema200 and close > ema50:
        return Decimal(60)
    if close > ema200:
        return Decimal(40)
    return Decimal(0)


def _environment(regime):
    return {"BULLISH": Decimal(100), "NEUTRAL": Decimal(50), "BEARISH": Decimal(0)}.get(regime)


def _regime_risk(regime):
    return {"BULLISH": Decimal(0), "NEUTRAL": Decimal(40), "BEARISH": Decimal(100)}.get(regime)


def _quality_risk(quality):
    return {"NORMAL": Decimal(0), "WARNING": Decimal(50), "INVALID": Decimal(100)}.get(quality)


def _risk_level(risk, params):
    if risk is None:
        return "UNKNOWN"
    if risk >= Decimal(params["criticalRisk"]):
        return "CRITICAL"
    if risk >= Decimal(70):
        return "HIGH"
    if risk >= Decimal(40):
        return "MEDIUM"
    return "LOW"


def _scale(value, low, high):
    if value is None:
        return None
    if value <= low:
        return Decimal(0)
    if value >= high:
        return Decimal(100)
    return (value - low) / (high - low) * Decimal(100)


def _next_position(open_position, signal_type):
    if signal_type in ("BUY", "STRONG_BUY"):
        return True
    if signal_type == "EXIT":
        return False
    return open_position


def _params(params):
    merged = {
        "modelVersion": MODEL_VERSION,
        "minimumConfidence": "0.8",
        "coreCap": "0.79",
        "strongBuyScore": "85",
        "buyScore": "75",
        "holdScore": "60",
        "gapScore": "45",
        "reduceScore": "30",
        "exitScore": "30",
        "criticalRisk": "85",
    }
    if params:
        merged.update(params)
    return merged


def _d(value):
    if value is None or value == "":
        return None
    return Decimal(str(value))


def _q4(value):
    if value is None:
        return None
    return format(value.quantize(_Q4, rounding=ROUND_HALF_UP), "f")


def _q6(value):
    return format(value.quantize(_Q6, rounding=ROUND_HALF_UP), "f")
