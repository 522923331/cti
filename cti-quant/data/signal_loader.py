"""Load closed daily inputs for the signal book. Forming bars are not read."""

from datetime import date, datetime

from config.settings import settings
from data.market_loader import EXCHANGE, as_float, connect, trade_date_of
from data.score_loader import load_config
from engines.intraday import last_four_hour
from engines.risk_engine import D, build_risk, log_returns, q
from engines.series import dated_normalized, rolling_quote_ratio
from engines.signal_models import EquityPoint, OpenPosition, RegimeView, SignalDay
from engines.signal_rules import ema50_slopes, prior_highs

BENCHMARK = "BTCUSDT"


def load_signal_inputs(mysql_url: str, symbols: list[str], trade_date):
    if BENCHMARK not in symbols:
        raise RuntimeError("信号计算需要 BTCUSDT")
    connection = connect(mysql_url)
    try:
        with connection.cursor() as cursor:
            cursor.execute("SET time_zone = '+00:00'")
            config = load_config(cursor)
            regimes = _regimes(cursor, trade_date)
            days = {symbol: _symbol(cursor, symbol, trade_date, config) for symbol in symbols}
            return config, days, regimes
    finally:
        connection.close()


def load_portfolio_risk(mysql_url: str, symbols: list[str], trade_date):
    connection = connect(mysql_url)
    try:
        with connection.cursor() as cursor:
            cursor.execute("SET time_zone = '+00:00'")
            config = load_config(cursor)
            equity = _equity(cursor, trade_date)
            positions = _positions(cursor)
            returns = {symbol: _closes(cursor, symbol, trade_date) for symbol in symbols}
            return build_risk(equity, positions, returns, config)
    finally:
        connection.close()


def _equity(cursor, trade_date) -> list[EquityPoint]:
    cursor.execute(
        """
        SELECT trade_date, cash_balance, equity, peak_equity, drawdown, exposure
        FROM ct_portfolio_equity
        WHERE account_code = %s AND strategy_version = %s
        ORDER BY trade_date
        """,
        (settings.strategy_name, settings.strategy_version),
    )
    rows = []
    for row in cursor.fetchall():
        current = _as_date(row["trade_date"])
        if trade_date is not None and current > trade_date:
            continue
        rows.append(EquityPoint(
            current, q(D(row["cash_balance"])), q(D(row["equity"])), q(D(row["peak_equity"])),
            q(D(row["drawdown"])), q(D(row["exposure"])),
        ))
    return rows


def _positions(cursor) -> list[OpenPosition]:
    cursor.execute(
        """
        SELECT symbol, quantity, avg_entry_price, stop_price, highest_close, opened_on, updated_on, market_value
        FROM ct_portfolio_position
        WHERE account_code = %s AND strategy_version = %s
        ORDER BY symbol
        """,
        (settings.strategy_name, settings.strategy_version),
    )
    rows = []
    for row in cursor.fetchall():
        rows.append(OpenPosition(
            symbol=row["symbol"],
            quantity=q(D(row["quantity"])),
            entry_price=q(D(row["avg_entry_price"])),
            stop_price=q(D(row["stop_price"])),
            highest_close=q(D(row["highest_close"])),
            opened_on=_as_date(row["opened_on"]),
            updated_on=_as_date(row["updated_on"]),
            market_value=q(D(row["market_value"])),
            atr_at_entry=D("0"),
        ))
    return rows


def _closes(cursor, symbol: str, trade_date) -> dict[date, float]:
    cursor.execute(
        """
        SELECT open_time, close_price
        FROM ct_kline
        WHERE exchange_code = %s AND symbol = %s AND interval_code = '1d' AND is_closed = 1
        ORDER BY open_time
        """,
        (EXCHANGE, symbol),
    )
    dates = []
    closes = []
    for row in cursor.fetchall():
        current = trade_date_of(int(row["open_time"]))
        if trade_date is not None and current > trade_date:
            continue
        dates.append(current)
        closes.append(float(row["close_price"]))
    return log_returns(dates, closes)


def _regimes(cursor, trade_date) -> dict[date, RegimeView]:
    cursor.execute(
        """
        SELECT trade_date, regime, total_exposure_limit, risk_override
        FROM ct_market_regime
        WHERE strategy_version = %s
        ORDER BY trade_date
        """,
        (settings.strategy_version,),
    )
    out: dict[date, RegimeView] = {}
    for row in cursor.fetchall():
        current = _as_date(row["trade_date"])
        if trade_date is not None and current > trade_date:
            continue
        out[current] = RegimeView(row["regime"], as_float(row["total_exposure_limit"]), row["risk_override"])
    return out


def _symbol(cursor, symbol: str, trade_date, config: dict) -> list[SignalDay]:
    cursor.execute(
        """
        SELECT open_time, open_price, high_price, low_price, close_price, quote_volume
        FROM ct_kline
        WHERE exchange_code = %s AND symbol = %s AND interval_code = '1d' AND is_closed = 1
        ORDER BY open_time
        """,
        (EXCHANGE, symbol),
    )
    prices = {}
    for row in cursor.fetchall():
        current = trade_date_of(int(row["open_time"]))
        if trade_date is not None and current > trade_date:
            continue
        prices[current] = row
    cursor.execute(
        """
        SELECT trade_date, ema20, ema50, atr14, volume_ratio_20d, spot_delta,
               funding_zscore, oi_change_7d, return_7d, data_quality_status
        FROM ct_feature_daily
        WHERE symbol = %s AND feature_version = %s
        ORDER BY trade_date
        """,
        (symbol, settings.feature_version),
    )
    features = {}
    for row in cursor.fetchall():
        current = _as_date(row["trade_date"])
        if trade_date is not None and current > trade_date:
            continue
        features[current] = row
    cursor.execute(
        """
        SELECT trade_date, trend_score, flow_score, total_score
        FROM ct_trend_score
        WHERE symbol = %s AND strategy_version = %s
        ORDER BY trade_date
        """,
        (symbol, settings.strategy_version),
    )
    scores = {}
    for row in cursor.fetchall():
        current = _as_date(row["trade_date"])
        if trade_date is not None and current > trade_date:
            continue
        scores[current] = row
    cursor.execute(
        """
        SELECT open_time, high_price, low_price, close_price
        FROM ct_kline
        WHERE exchange_code = %s AND symbol = %s AND interval_code = '4h' AND is_closed = 1
        ORDER BY open_time
        """,
        (EXCHANGE, symbol),
    )
    four_bars = []
    for row in cursor.fetchall():
        current = trade_date_of(int(row["open_time"]))
        if trade_date is not None and current > trade_date:
            continue
        four_bars.append((int(row["open_time"]), float(row["high_price"]), float(row["low_price"]), float(row["close_price"])))
    four = last_four_hour(four_bars)
    return _assemble(prices, features, scores, four, config)


def _assemble(prices, features, scores, four, config) -> list[SignalDay]:
    dates = sorted(day for day in prices if day in features and day in scores)
    if not dates:
        return []
    ema50 = [as_float(features[day]["ema50"]) for day in dates]
    highs = [float(prices[day]["high_price"]) for day in dates]
    slopes = ema50_slopes(dates, ema50, int(config["indicators"]["emaSlopeLookback"]))
    prior = prior_highs(dates, highs, int(config["entry"]["breakoutLookback"]))
    window = int(config["indicators"]["zscoreWindow"])
    seven = rolling_quote_ratio(
        dates,
        [as_float(features[day]["spot_delta"]) for day in dates],
        [_quote(prices[day]["quote_volume"]) for day in dates],
        7,
    )
    seven_score = dated_normalized(dates, seven, window)
    rows: list[SignalDay] = []
    for index, day in enumerate(dates):
        price = prices[day]
        feature = features[day]
        score = scores[day]
        snapshot = four.get(day)
        rows.append(SignalDay(
            trade_date=day,
            open=float(price["open_price"]),
            high=float(price["high_price"]),
            low=float(price["low_price"]),
            close=float(price["close_price"]),
            ema20=as_float(feature["ema20"]),
            ema50=as_float(feature["ema50"]),
            atr14=as_float(feature["atr14"]),
            ema50_slope=slopes[index],
            volume_ratio_20d=as_float(feature["volume_ratio_20d"]),
            prior_high_20=prior[index],
            spot_delta_7d_score=seven_score[index],
            funding_zscore=as_float(feature["funding_zscore"]),
            oi_change_7d=as_float(feature["oi_change_7d"]),
            return_7d=as_float(feature["return_7d"]),
            quality=feature["data_quality_status"] or "WARNING",
            trend_score=as_float(score["trend_score"]),
            flow_score=as_float(score["flow_score"]),
            total_score=as_float(score["total_score"]),
            four_low=None if snapshot is None else snapshot[0],
            four_close=None if snapshot is None else snapshot[1],
            four_ema20=None if snapshot is None else snapshot[2],
            four_atr14=None if snapshot is None else snapshot[3],
        ))
    return rows


def _quote(value):
    number = as_float(value)
    if number is None or number < 0:
        return None
    return number


def _as_date(value) -> date:
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    raise RuntimeError("无法识别交易日")
