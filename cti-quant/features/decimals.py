"""Persist indicator output at the same 12 decimal places as the MySQL columns."""

from decimal import Decimal, ROUND_HALF_UP

_SCALE = Decimal("0.000000000001")


def to_decimal_string(value: float | None) -> str | None:
    if value is None:
        return None
    quantized = Decimal.from_float(value).quantize(_SCALE, rounding=ROUND_HALF_UP)
    return format(quantized, "f")
