"""Closed inputs for the V1.1 score. Forming bars and unknown news direction stay out."""

from datetime import datetime, timezone
from decimal import Decimal

from config.settings import settings
from data.market_loader import EXCHANGE, as_float, connect, trade_date_of
from indicators.derivatives import funding_z20

_NEWS_SCORE = {"POSITIVE": "75", "NEGATIVE": "25", "NEUTRAL": "50"}


def load_intelligence_rows(mysql_url: str):
    connection = connect(mysql_url)
    try:
        with connection.cursor() as cursor:
            cursor.execute("SET time_zone = '+00:00'")
            assets = _assets(cursor)
            symbols = [asset["symbol"] for asset in assets]
            closes = _closes(cursor, symbols)
            regimes = _regimes(cursor)
            confirmation = _four_hour_confirmation(cursor, symbols)
            funding = _funding_z(cursor, symbols, closes)
            news = _news(cursor, assets)
            rows = []
            for symbol, asset in ((item["symbol"], item["asset_code"]) for item in assets):
                for trade_date, feature in _features(cursor, symbol).items():
                    rows.append({
                        "symbol": symbol,
                        "asset": asset,
                        "tradeDate": trade_date.isoformat(),
                        "close": closes.get((symbol, trade_date)),
                        "ema20": feature["ema20"],
                        "ema50": feature["ema50"],
                        "ema200": feature["ema200"],
                        "return30d": feature["return30d"],
                        "volumeRatio20d": feature["volumeRatio20d"],
                        "realizedVolatility20d": feature["realizedVolatility20d"],
                        "quality": feature["quality"],
                        "regime": regimes.get(trade_date),
                        "fundingZ20": funding.get((symbol, trade_date)),
                        "newsSentiment": news.get((asset, trade_date)),
                        "onchainContext": None,
                        "h4Confirmed": confirmation.get((symbol, trade_date), False),
                    })
            return rows
    finally:
        connection.close()


def _assets(cursor):
    cursor.execute("SELECT asset_code, symbol FROM ct_asset WHERE enabled = 1 ORDER BY symbol")
    return list(cursor.fetchall())


def _features(cursor, symbol):
    cursor.execute(
        """
        SELECT trade_date, ema20, ema50, ema200, return_30d, volume_ratio_20d,
               realized_volatility_20d, data_quality_status
        FROM ct_feature_daily
        WHERE symbol = %s AND feature_version = %s
        ORDER BY trade_date
        """,
        (symbol, settings.feature_version),
    )
    return {
        row["trade_date"]: {
            "ema20": _text(row["ema20"]),
            "ema50": _text(row["ema50"]),
            "ema200": _text(row["ema200"]),
            "return30d": _text(row["return_30d"]),
            "volumeRatio20d": _text(row["volume_ratio_20d"]),
            "realizedVolatility20d": _text(row["realized_volatility_20d"]),
            "quality": row["data_quality_status"],
        }
        for row in cursor.fetchall()
    }


def _closes(cursor, symbols):
    if not symbols:
        return {}
    marks = ",".join(["%s"] * len(symbols))
    cursor.execute(
        f"""
        SELECT symbol, open_time, close_price
        FROM ct_kline
        WHERE exchange_code = %s AND interval_code = '1d' AND is_closed = 1 AND symbol IN ({marks})
        """,
        (EXCHANGE, *symbols),
    )
    return {
        (row["symbol"], trade_date_of(int(row["open_time"]))): _text(row["close_price"])
        for row in cursor.fetchall()
    }


def _regimes(cursor):
    cursor.execute(
        """
        SELECT trade_date, regime
        FROM ct_market_regime
        WHERE strategy_version = %s
        """,
        (settings.strategy_version,),
    )
    return {row["trade_date"]: row["regime"] for row in cursor.fetchall()}


def _four_hour_confirmation(cursor, symbols):
    if not symbols:
        return {}
    marks = ",".join(["%s"] * len(symbols))
    cursor.execute(
        f"""
        SELECT symbol, open_time, close_price
        FROM ct_kline
        WHERE exchange_code = %s AND interval_code = '4h' AND is_closed = 1 AND symbol IN ({marks})
        ORDER BY symbol, open_time
        """,
        (EXCHANGE, *symbols),
    )
    grouped = {}
    for row in cursor.fetchall():
        grouped.setdefault(row["symbol"], []).append(row)
    confirmed = {}
    period = 20
    multiplier = Decimal(2) / Decimal(period + 1)
    for symbol, bars in grouped.items():
        ema = None
        count = 0
        total = Decimal(0)
        for bar in bars:
            close = Decimal(str(bar["close_price"]))
            count += 1
            if count < period:
                total += close
                continue
            if count == period:
                total += close
                ema = total / Decimal(period)
            else:
                ema = close * multiplier + ema * (Decimal(1) - multiplier)
            confirmed[(symbol, trade_date_of(int(bar["open_time"])))] = close > ema
    return confirmed


def _funding_z(cursor, symbols, closes):
    if not symbols:
        return {}
    marks = ",".join(["%s"] * len(symbols))
    cursor.execute(
        f"""
        SELECT symbol, event_time_ms, metric_value
        FROM ct_derivatives_metric
        WHERE metric_code = 'FUNDING_RATE' AND interval_code = '8h' AND data_status = 'CLOSED'
          AND symbol IN ({marks})
        ORDER BY symbol, event_time_ms
        """,
        tuple(symbols),
    )
    rates = {}
    for row in cursor.fetchall():
        value = as_float(row["metric_value"])
        if value is not None:
            rates.setdefault(row["symbol"], []).append((int(row["event_time_ms"]), value))
    found = {}
    for (symbol, trade_date) in closes:
        series = [value for time_ms, value in rates.get(symbol, []) if time_ms <= _day_end_ms(trade_date)]
        score = funding_z20(series)
        if score and score[-1] is not None:
            found[(symbol, trade_date)] = format(Decimal(str(score[-1])), "f")
    return found


def _news(cursor, assets):
    cursor.execute(
        """
        SELECT rela.asset_code, event.published_at_ms, event.sentiment
        FROM ct_news_event event
        JOIN ct_news_asset_rela rela ON rela.event_id = event.event_id
        WHERE event.sentiment IN ('POSITIVE', 'NEGATIVE', 'NEUTRAL') AND event.published_at_ms IS NOT NULL
        """
    )
    grouped = {}
    market = {}
    for row in cursor.fetchall():
        day = trade_date_of(int(row["published_at_ms"]))
        score = Decimal(_NEWS_SCORE[row["sentiment"]])
        if row["asset_code"] == "MARKET_WIDE":
            market.setdefault(day, []).append(score)
        else:
            grouped.setdefault((row["asset_code"], day), []).append(score)
    result = {}
    for asset in {item["asset_code"] for item in assets}:
        days = {day for code, day in grouped if code == asset}
        days.update(market)
        for day in days:
            values = list(grouped.get((asset, day), []))
            values.extend(market.get(day, []))
            if values:
                result[(asset, day)] = format(sum(values, Decimal(0)) / Decimal(len(values)), "f")
    return result


def _day_end_ms(day):
    return int(datetime(day.year, day.month, day.day, tzinfo=timezone.utc).timestamp() * 1000) + 86_400_000 - 1


def _text(value):
    if value is None:
        return None
    return format(Decimal(str(value)), "f")
