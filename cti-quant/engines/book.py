"""Walk the research book one closed day at a time. Orders fill on the next daily open."""

from datetime import date
from decimal import Decimal

from engines.risk_engine import D, asset_class, build_risk, correlation, drawdown_policy, log_returns, money_text, q, size_entry
from engines.signal_models import EmittedSignal, EquityPoint, Fill, OpenPosition, RegimeView, SignalDay
from engines.signal_rules import BREAKOUT, PULLBACK, breakout_setup, exit_reasons, pullback_setup, watch_setup
from engines.weights import score_decimal

BENCHMARK = "BTCUSDT"


class PendingOrder:
    def __init__(self, symbol: str, action: str, quantity: Decimal | None, quote: Decimal | None, atr: Decimal | None):
        self.symbol = symbol
        self.action = action
        self.quantity = quantity
        self.quote = quote
        self.atr = atr


def run_book(days: dict[str, list[SignalDay]], regimes: dict[date, RegimeView], config: dict,
             feature_version: str, strategy_version: str, strategy_name: str, *,
             setup_mode: str = "ALL", entry_order: str = "symbol", start_date: date | None = None,
             blotter: list | None = None):
    indexed = {symbol: {bar.trade_date: bar for bar in bars} for symbol, bars in days.items()}
    returns = {
        symbol: log_returns([bar.trade_date for bar in bars], [bar.close for bar in bars])
        for symbol, bars in days.items()
    }
    calendar = sorted({bar.trade_date for bars in days.values() for bar in bars})
    cash = q(D(config["risk"]["accountCapital"]))
    peak = cash
    positions: dict[str, OpenPosition] = {}
    pending: list[PendingOrder] = []
    signals: list[EmittedSignal] = []
    equity_rows: list[EquityPoint] = []
    initial = cash
    for current in calendar:
        if start_date is not None and current < start_date:
            continue
        gaps, pending, cash = _fill_open(current, indexed, positions, pending, cash, config, blotter)
        signals.extend(_decide(
            current, indexed, regimes, returns, positions, pending, gaps, cash, peak, config,
            feature_version, strategy_version, strategy_name, setup_mode, entry_order,
        ))
        cash, peak, point = _mark(current, indexed, positions, cash, peak)
        equity_rows.append(point)
    risk = build_risk(equity_rows, list(positions.values()), returns, config)
    account = {
        "accountCode": strategy_name,
        "strategyName": strategy_name,
        "strategyVersion": strategy_version,
        "quoteAsset": config["execution"]["quoteAsset"],
        "initialCapital": money_text(initial),
        "cash": money_text(cash),
        "equity": money_text(equity_rows[-1].equity if equity_rows else initial),
        "peakEquity": money_text(peak),
        "asOfDate": calendar[-1].isoformat() if calendar else None,
        "risk": risk,
    }
    return signals, account, list(positions.values()), equity_rows, risk


def _fill_open(current: date, indexed, positions, pending, cash, config, blotter):
    execution = config["execution"]
    slippage = D(execution["slippageRate"])
    commission = D(execution["commissionRate"])
    cash_box = [cash]
    gaps: dict[str, tuple[Decimal, Decimal, float]] = {}
    for symbol in sorted(list(positions)):
        day = indexed.get(symbol, {}).get(current)
        position = positions.get(symbol)
        if day is None or position is None or day.quality == "INVALID":
            continue
        if D(day.open) <= position.stop_price:
            gaps[symbol] = (position.entry_price, position.stop_price, day.open)
            _sell(position, D(day.open), slippage, commission, cash_box, positions, current, "SELL", blotter)
    carried: list[PendingOrder] = []
    for order in sorted(pending, key=lambda item: (item.action != "SELL", item.action != "REDUCE", item.symbol)):
        day = indexed.get(order.symbol, {}).get(current)
        if day is None:
            carried.append(order)
            continue
        if order.symbol in gaps:
            continue
        if order.action == "BUY":
            _buy(order, day, current, slippage, commission, cash_box, positions, blotter)
        elif order.action == "SELL" and order.symbol in positions:
            _sell(positions[order.symbol], D(day.open), slippage, commission, cash_box, positions, current, "SELL", blotter)
        elif order.action == "REDUCE" and order.symbol in positions and order.quantity is not None:
            _reduce(positions[order.symbol], order.quantity, D(day.open), slippage, commission, cash_box, positions, current, blotter)
    return gaps, carried, cash_box[0]


def _buy(order: PendingOrder, day: SignalDay, current: date, slippage, commission, cash_box, positions, blotter):
    if order.quote is None or order.atr is None or order.symbol in positions:
        return
    fill = q(D(day.open) * (Decimal("1") + slippage))
    if fill <= 0:
        return
    unit = q(fill * (Decimal("1") + commission))
    quantity = q(order.quote / fill)
    if unit > 0 and q(quantity * unit) > cash_box[0]:
        quantity = q(cash_box[0] / unit)
    if quantity <= 0:
        return
    gross = q(quantity * fill)
    fee = q(gross * commission)
    if q(gross + fee) > cash_box[0] and quantity > Decimal("0.000000000001"):
        quantity = q(quantity - Decimal("0.000000000001"))
        gross = q(quantity * fill)
        fee = q(gross * commission)
    # order.atr is the full stop distance, Entry - 2 * ATR, not the raw ATR.
    stop = q(fill - order.atr)
    if quantity <= 0 or stop <= 0 or q(gross + fee) > cash_box[0]:
        return
    cash_box[0] = q(cash_box[0] - gross - fee)
    positions[order.symbol] = OpenPosition(
        symbol=order.symbol,
        quantity=quantity,
        entry_price=fill,
        stop_price=stop,
        highest_close=Decimal("0"),
        opened_on=current,
        updated_on=current,
        market_value=q(quantity * D(day.open)),
        atr_at_entry=order.atr,
        fee_paid=fee,
    )
    _remember(blotter, current, order.symbol, "BUY", quantity, D(day.open), fill, fee, None, None)


def _sell(position: OpenPosition, open_price: Decimal, slippage, commission, cash_box, positions, current, side, blotter):
    fill = q(open_price * (Decimal("1") - slippage))
    if fill <= 0:
        return
    gross = q(position.quantity * fill)
    fee = q(gross * commission)
    pnl = _closing_pnl(position, position.quantity, fill, fee)
    cash_box[0] = q(cash_box[0] + gross - fee)
    _remember(blotter, current, position.symbol, side, position.quantity, open_price, fill, fee, pnl, position.opened_on)
    positions.pop(position.symbol, None)


def _reduce(position: OpenPosition, quantity: Decimal, open_price: Decimal, slippage, commission, cash_box, positions, current, blotter):
    selling = min(q(quantity), position.quantity)
    if selling <= 0:
        return
    fill = q(open_price * (Decimal("1") - slippage))
    gross = q(selling * fill)
    fee = q(gross * commission)
    pnl = _closing_pnl(position, selling, fill, fee)
    cash_box[0] = q(cash_box[0] + gross - fee)
    _remember(blotter, current, position.symbol, "REDUCE", selling, open_price, fill, fee, pnl, position.opened_on)
    remaining = q(position.quantity - selling)
    if remaining <= 0:
        positions.pop(position.symbol, None)
        return
    position.fee_paid = q(position.fee_paid - _allocated_fee(position, selling))
    position.quantity = remaining
    position.market_value = q(remaining * open_price)
    position.updated_on = current


def _closing_pnl(position: OpenPosition, quantity: Decimal, fill: Decimal, fee: Decimal) -> Decimal:
    allocated = _allocated_fee(position, quantity)
    return q((fill - position.entry_price) * quantity - fee - allocated)


def _allocated_fee(position: OpenPosition, quantity: Decimal) -> Decimal:
    if position.quantity <= 0:
        return Decimal("0")
    return q(position.fee_paid * quantity / position.quantity)


def _remember(blotter, current, symbol, side, quantity, open_price, fill, fee, pnl, opened_on):
    if blotter is None:
        return
    price_gap = (fill - open_price) if side == "BUY" else (open_price - fill)
    slip = q(quantity * price_gap)
    blotter.append(Fill(current, symbol, side, quantity, open_price, fill, fee, slip, pnl, opened_on))


def _decision_order(indexed, current, positions, gaps, entry_order: str) -> list[str]:
    symbols = sorted(indexed)
    if entry_order == "symbol":
        return symbols
    if entry_order != "score":
        raise ValueError("entry_order 只能是 symbol 或 score")
    held = [symbol for symbol in symbols if symbol in positions or symbol in gaps]
    fresh = [symbol for symbol in symbols if symbol not in positions and symbol not in gaps]

    def score_key(symbol: str):
        day = indexed[symbol].get(current)
        score = day.total_score if day is not None and day.total_score is not None else float("-inf")
        return (-score, symbol)

    return held + sorted(fresh, key=score_key)


def _decide(current, indexed, regimes, returns, positions, pending, gaps, cash, peak, config,
            feature_version, strategy_version, strategy_name, setup_mode, entry_order) -> list[EmittedSignal]:
    equity = cash
    held_values: dict[str, Decimal] = {}
    for symbol, position in positions.items():
        day = indexed.get(symbol, {}).get(current)
        mark = D(day.close) if day is not None else (position.market_value / position.quantity if position.quantity else Decimal("0"))
        value = q(position.quantity * mark)
        held_values[symbol] = value
        equity += value
    equity = q(equity)
    running_peak = peak if peak > equity else equity
    drawdown = q(equity / running_peak - 1) if running_peak > 0 else Decimal("0")
    scale, pause = drawdown_policy(drawdown, config)
    window = int(config["risk"]["correlationWindow"])
    threshold_pairs: dict[frozenset[str], float | None] = {}
    symbols = _decision_order(indexed, current, positions, gaps, entry_order)
    names = sorted(indexed)
    for left_index, left in enumerate(names):
        for right in names[left_index + 1:]:
            threshold_pairs[frozenset((left, right))] = correlation(returns[left], returns[right], current, window)
    reserved_values: dict[str, Decimal] = {}
    emitted: list[EmittedSignal] = []
    regime = regimes.get(current)
    btc = indexed.get(BENCHMARK, {}).get(current)
    for symbol in symbols:
        day = indexed[symbol].get(current)
        if day is None:
            continue
        if symbol in gaps:
            entry, stop, open_price = gaps[symbol]
            emitted.append(_signal(
                symbol, current, "SELL", "VALID", day, entry, stop,
                Decimal("0"), Decimal("0"),
                {"action": "SELL", "reasons": ["GAP_STOP"], "open": open_price},
                None, feature_version, strategy_version, strategy_name,
            ))
            continue
        position = positions.get(symbol)
        if position is not None:
            emitted.append(_manage(
                symbol, day, position, regime, current, config, feature_version, strategy_version, strategy_name, pending,
            ))
            continue
        emitted.append(_enter(
            symbol, day, regime, btc, current, equity, cash, held_values, reserved_values, threshold_pairs,
            scale, pause, positions, config, feature_version, strategy_version, strategy_name, pending, setup_mode,
        ))
    return emitted


def _manage(symbol, day, position, regime, current, config, feature_version, strategy_version, strategy_name, pending):
    if day.quality == "INVALID" or regime is None:
        return _signal(
            symbol, current, "HOLD", "INVALID", day, position.entry_price, position.stop_price,
            None, None, {"action": "HOLD", "blocked": "INVALID_DATA"},
            {"quality": day.quality, "regime": None if regime is None else regime.regime},
            feature_version, strategy_version, strategy_name,
        )
    holding_days = (current - position.opened_on).days + 1
    kind, reasons = exit_reasons(day, regime, float(position.stop_price), holding_days, config)
    if kind == "SELL":
        pending.append(PendingOrder(symbol, "SELL", position.quantity, None, None))
        weight = Decimal("0")
    elif kind == "REDUCE":
        selling = q(position.quantity * D(config["risk"]["reduceFraction"]))
        pending.append(PendingOrder(symbol, "REDUCE", selling, None, None))
        weight = None
    else:
        weight = None
    if kind != "SELL":
        mark = D(day.close)
        if mark > position.highest_close:
            position.highest_close = mark
        if day.atr14 is not None and day.atr14 > 0:
            trail = q(position.highest_close - D(day.atr14) * D(config["risk"]["atrStopMultiplier"]))
            if trail > position.stop_price:
                position.stop_price = trail
        position.updated_on = current
        position.market_value = q(position.quantity * mark)
    return _signal(
        symbol, current, kind, "VALID", day, position.entry_price, position.stop_price,
        weight, None, {"action": kind, "reasons": reasons, "holdingDays": holding_days},
        None, feature_version, strategy_version, strategy_name,
    )


def _enter(symbol, day, regime, btc, current, equity, cash, held_values, reserved_values, correlations,
           scale, pause, positions, config, feature_version, strategy_version, strategy_name, pending, setup_mode):
    core_missing = day.total_score is None or day.trend_score is None or day.flow_score is None
    if day.quality == "INVALID" or regime is None or core_missing:
        return _signal(
            symbol, current, "NO_TRADE", "INVALID", day, None, None, None, None,
            {"action": "NO_TRADE", "blocked": "INVALID_DATA"},
            {"quality": day.quality, "total": day.total_score, "regime": None if regime is None else regime.regime},
            feature_version, strategy_version, strategy_name,
        )
    pullback, pullback_detail = pullback_setup(day, regime, btc, config)
    breakout, breakout_detail = breakout_setup(day, regime, config)
    if setup_mode == "PULLBACK":
        breakout = False
    elif setup_mode == "BREAKOUT":
        pullback = False
    elif setup_mode == "MARKET_FILTER":
        pullback = False
        breakout = False
    elif setup_mode != "ALL":
        raise ValueError("setup_mode 无法识别")
    setups = []
    if setup_mode == "MARKET_FILTER":
        threshold = float(config["entry"]["pullbackScore"])
        if watch_setup(day, regime, config) and day.total_score is not None and day.total_score >= threshold:
            setups.append("MARKET_FILTER_TREND")
    else:
        if pullback:
            setups.append(PULLBACK)
        if breakout:
            setups.append(BREAKOUT)
    derivatives_missing = day.funding_zscore is None or day.oi_change_7d is None
    if setups:
        if len(positions) + len(reserved_values) >= int(config["risk"]["maxHoldingAssets"]) and symbol not in reserved_values:
            return _flat_signal(symbol, day, current, "NO_TRADE", "VALID", {"setups": setups, "blocked": "MAX_HOLDINGS"},
                                feature_version, strategy_version, strategy_name)
        combined = dict(held_values)
        combined.update(reserved_values)
        weights = {name: (value / equity if equity > 0 else Decimal("0")) for name, value in combined.items()}
        limit = None if regime.exposure_limit is None else D(regime.exposure_limit)
        commission = D(config["execution"]["commissionRate"])
        reserved_cash = sum((q(value * (Decimal("1") + commission)) for value in reserved_values.values()), Decimal("0"))
        quote, risk_amount, sizing = size_entry(
            symbol, day.close, day.atr14, equity, q(cash - reserved_cash),
            weights, combined, correlations, limit, asset_class(symbol), derivatives_missing,
            scale, pause, config,
        )
        reason = {"action": "BUY", "setups": setups, "pullback": pullback_detail, "breakout": breakout_detail, "sizing": sizing}
        if quote is None:
            if watch_setup(day, regime, config):
                return _flat_signal(symbol, day, current, "WATCH", "VALID", {**reason, "action": "WATCH"},
                                    feature_version, strategy_version, strategy_name)
            return _flat_signal(symbol, day, current, "NO_TRADE", "VALID", {**reason, "action": "NO_TRADE"},
                                feature_version, strategy_version, strategy_name)
        reserved_values[symbol] = quote
        distance = q(D(day.atr14) * D(config["risk"]["atrStopMultiplier"]))
        pending.append(PendingOrder(symbol, "BUY", None, quote, distance))
        weight = q(quote / equity) if equity > 0 else Decimal("0")
        stop = q(D(day.close) - distance)
        status = "WARNING" if derivatives_missing else "VALID"
        return _signal(
            symbol, current, "BUY", status, day, q(D(day.close)), stop, weight, risk_amount, reason,
            None, feature_version, strategy_version, strategy_name,
        )
    if watch_setup(day, regime, config):
        return _flat_signal(
            symbol, day, current, "WATCH", "VALID",
            {"action": "WATCH", "pullback": pullback_detail, "breakout": breakout_detail},
            feature_version, strategy_version, strategy_name,
        )
    return _flat_signal(
        symbol, day, current, "NO_TRADE", "VALID",
        {"action": "NO_TRADE", "pullback": pullback_detail, "breakout": breakout_detail},
        feature_version, strategy_version, strategy_name,
    )


def _flat_signal(symbol, day, current, kind, status, reason, feature_version, strategy_version, strategy_name):
    return _signal(symbol, current, kind, status, day, None, None, None, None, reason, None,
                   feature_version, strategy_version, strategy_name)


def _signal(symbol, current: date, kind, status, day: SignalDay, entry, stop, weight, risk_amount, reason, invalidation,
            feature_version, strategy_version, strategy_name) -> EmittedSignal:
    return EmittedSignal(
        signal_id=f"{strategy_version}:{symbol}:{current.isoformat()}:{kind}:{strategy_name}",
        symbol=symbol,
        signal_time=f"{current.isoformat()}T23:59:59",
        trade_date=current,
        signal_type=kind,
        signal_status=status,
        score=score_decimal(day.total_score),
        entry_price=entry,
        stop_price=stop,
        suggested_position=weight,
        risk_amount=risk_amount,
        reason=reason,
        invalidation=invalidation,
        feature_version=feature_version,
        strategy_version=strategy_version,
    )


def _mark(current, indexed, positions, cash, peak):
    market = Decimal("0")
    for symbol, position in positions.items():
        day = indexed.get(symbol, {}).get(current)
        mark = D(day.close) if day is not None else (position.market_value / position.quantity)
        position.market_value = q(position.quantity * mark)
        market += position.market_value
    equity = q(cash + market)
    if equity > peak:
        peak = equity
    drawdown = q(equity / peak - 1) if peak > 0 else Decimal("0")
    exposure = q(market / equity) if equity > 0 else Decimal("0")
    return cash, peak, EquityPoint(current, cash, equity, peak, drawdown, exposure)
