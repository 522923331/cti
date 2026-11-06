"""Weight renormalization. A missing component is left out; it is never treated as zero."""

from decimal import Decimal, ROUND_HALF_UP

FOUR = Decimal("0.0001")
TWELVE = Decimal("0.000000000001")


def score_decimal(value: float | int | None) -> Decimal | None:
    if value is None:
        return None
    if isinstance(value, int) or (isinstance(value, float) and value.is_integer()):
        return Decimal(int(value)).quantize(FOUR)
    return Decimal.from_float(value).quantize(FOUR, rounding=ROUND_HALF_UP)


def ratio_decimal(value: float | None, scale: Decimal = FOUR) -> Decimal | None:
    if value is None:
        return None
    return Decimal.from_float(value).quantize(scale, rounding=ROUND_HALF_UP)


def volatility_decimal(value: float | None) -> Decimal | None:
    return ratio_decimal(value, TWELVE)


def config_decimal(value) -> Decimal:
    return Decimal(str(value))


def weighted(parts: dict[str, Decimal | None], weights: dict[str, Decimal]) -> tuple[Decimal | None, dict]:
    available = {key: parts[key] for key in weights if parts.get(key) is not None}
    if not available:
        return None, {"used": [], "dropped": [key for key in weights if parts.get(key) is None], "scale": None}
    scale = sum((weights[key] for key in available), Decimal("0"))
    if scale == 0:
        return None, {"used": [], "dropped": list(weights), "scale": "0"}
    total = sum((available[key] * weights[key] for key in available), Decimal("0")) / scale
    return total.quantize(FOUR, rounding=ROUND_HALF_UP), {
        "used": {key: format(weights[key], "f") for key in available},
        "dropped": [key for key in weights if key not in available],
        "scale": format(scale, "f"),
    }


def text(value: Decimal | None) -> str | None:
    if value is None:
        return None
    return format(value, "f")
