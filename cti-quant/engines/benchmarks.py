"""Long-only baselines. A signal known at the close fills on the next daily open."""

from datetime import date, timedelta
from decimal import Decimal, ROUND_DOWN

from engines.risk_engine import D, q
from engines.signal_models import EquityPoint, Fill
from indicators.trend import ema


def buy_and_hold(bars: list, capital: Decimal, commission: Decimal, slippage: Decimal):
    """Enter at the first open. The decision is the start of the window, not a future close."""
    if not bars:
        return [], []
    targets = [True] * len(bars)
    return _simulate(bars, targets, capital, commission, slippage, enter_on_first_open=True)


def ema_cross(bars: list, fast: int, slow: int, capital: Decimal, commission: Decimal, slippage: Decimal, start: date | None = None):
    closes = [bar.close for bar in bars]
    fast_ema = ema(closes, fast)
    slow_ema = ema(closes, slow)
    targets = [
        fast_ema[index] is not None and slow_ema[index] is not None and fast_ema[index] > slow_ema[index]
        for index in range(len(bars))
    ]
    return _window(bars, targets, capital, commission, slippage, start)


def momentum(bars: list, period: int, capital: Decimal, commission: Decimal, slippage: Decimal, start: date | None = None):
    targets = []
    for index, bar in enumerate(bars):
        if index < period or bars[index].trade_date - bars[index - period].trade_date != timedelta(days=period):
            targets.append(False)
            continue
        previous = bars[index - period].close
        targets.append(previous > 0 and bar.close / previous - 1 > 0)
    return _window(bars, targets, capital, commission, slippage, start)


def _window(bars, targets, capital, commission, slippage, start):
    pending = None
    chosen_bars = []
    chosen_targets = []
    for bar, target in zip(bars, targets, strict=True):
        if start is not None and bar.trade_date < start:
            pending = "BUY" if target else None
            continue
        chosen_bars.append(bar)
        chosen_targets.append(target)
    return _simulate(chosen_bars, chosen_targets, capital, commission, slippage, enter_on_first_open=False, initial_pending=pending)


def _simulate(bars, targets, capital, commission, slippage, enter_on_first_open: bool, initial_pending=None):
    cash = q(capital)
    quantity = Decimal("0")
    entry = Decimal("0")
    opened: date | None = None
    fee_paid = Decimal("0")
    pending = "BUY" if enter_on_first_open else initial_pending
    fills: list[Fill] = []
    equity: list[EquityPoint] = []
    peak = cash
    for index, bar in enumerate(bars):
        if pending == "BUY" and quantity == 0:
            cash, quantity, entry, opened, fee_paid = _buy(bar, cash, commission, slippage, fills)
        elif pending == "SELL" and quantity > 0:
            cash = q(cash + _sell(bar, quantity, entry, opened, fee_paid, commission, slippage, fills))
            quantity = Decimal("0")
            fee_paid = Decimal("0")
        market = q(quantity * D(bar.close))
        equity_value = q(cash + market)
        if equity_value > peak:
            peak = equity_value
        drawdown = q(equity_value / peak - 1) if peak > 0 else Decimal("0")
        exposure = q(market / equity_value) if equity_value > 0 else Decimal("0")
        equity.append(EquityPoint(bar.trade_date, cash, equity_value, peak, drawdown, exposure))
        if enter_on_first_open and index == 0 and quantity > 0:
            pending = None
        else:
            pending = "BUY" if targets[index] and quantity == 0 else "SELL" if quantity > 0 and not targets[index] else None
    return equity, fills


def _buy(bar, cash, commission, slippage, fills):
    fill = q(D(bar.open) * (Decimal("1") + slippage))
    if fill <= 0 or cash <= 0:
        return cash, Decimal("0"), Decimal("0"), None, Decimal("0")
    unit = fill * (Decimal("1") + commission)
    step = Decimal("0.000000000001")
    quantity = (cash / unit).quantize(step, rounding=ROUND_DOWN) if unit > 0 else Decimal("0")
    gross = q(quantity * fill)
    fee = q(gross * commission)
    while quantity > 0 and q(gross + fee) > cash:
        quantity = q(quantity - step)
        gross = q(quantity * fill)
        fee = q(gross * commission)
    if quantity <= 0:
        return cash, Decimal("0"), Decimal("0"), None, Decimal("0")
    fills.append(Fill(bar.trade_date, "BTCUSDT", "BUY", quantity, D(bar.open), fill, fee, q(quantity * (fill - D(bar.open))), None, None))
    return q(cash - gross - fee), quantity, fill, bar.trade_date, fee


def _sell(bar, quantity, entry, opened, fee_paid, commission, slippage, fills):
    fill = q(D(bar.open) * (Decimal("1") - slippage))
    gross = q(quantity * fill)
    fee = q(gross * commission)
    pnl = q((fill - entry) * quantity - fee - fee_paid)
    fills.append(Fill(bar.trade_date, "BTCUSDT", "SELL", quantity, D(bar.open), fill, fee, q(quantity * (D(bar.open) - fill)), pnl, opened))
    return q(gross - fee)
