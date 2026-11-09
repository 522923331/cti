from decimal import Decimal, ROUND_HALF_UP

from strategies.base import closed_bars

SCALE = Decimal("0.000000000001")


def run_isolated(strategies, bars, initial, quantity, fee_rate, slippage_bps):
    return {
        strategy.strategy_code(): research_backtest(
            strategy, bars, initial=initial, quantity=quantity, fee_rate=fee_rate, slippage_bps=slippage_bps,
        )
        for strategy in strategies
    }


def research_backtest(strategy, bars, initial, quantity, fee_rate, slippage_bps):
    cash = Decimal(initial)
    held = {}
    pending = None
    history = closed_bars(bars)
    size = Decimal(quantity)
    fee = Decimal(fee_rate)
    slip = Decimal(slippage_bps) / Decimal(10000)
    for index, bar in enumerate(history):
        if pending is not None:
            cash = _fill(cash, held, pending, bar, size, fee, slip)
            pending = None
        window = {"bars": history[:index + 1], "regime": bar.get("regime")}
        symbol = bar["symbol"]
        if symbol in held:
            decision = strategy.calculate_exit(window, {"symbol": symbol, "quantity": held[symbol]})
            if decision["signal"] == "EXIT":
                pending = ("SELL", symbol)
        else:
            decision = strategy.generate_signal(window)
            if decision["signal"] == "BUY":
                pending = ("BUY", symbol)
    return {
        "strategyCode": strategy.strategy_code(),
        "cash": _text(cash),
        "positions": {symbol: _text(quantity) for symbol, quantity in held.items()},
        "parameters": strategy.get_parameters(),
    }


def _fill(cash, held, pending, bar, size, fee, slip):
    side, symbol = pending
    open_price = Decimal(bar["open"])
    if side == "BUY":
        price = open_price * (Decimal(1) + slip)
        cost = price * size
        charge = cost * fee
        if cost + charge > cash:
            return cash
        held[symbol] = size
        return cash - cost - charge
    price = open_price * (Decimal(1) - slip)
    proceeds = price * held[symbol]
    charge = proceeds * fee
    del held[symbol]
    return cash + proceeds - charge


def _text(value):
    return format(value.quantize(SCALE, rounding=ROUND_HALF_UP), "f")
