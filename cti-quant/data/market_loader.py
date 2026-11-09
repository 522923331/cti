"""Read closed daily bars for the feature job. Forming candles are dropped here."""

from datetime import date, datetime, timezone
from urllib.parse import urlparse

import pymysql
from pymysql.cursors import DictCursor

from features.feature_builder import DailyBar, DerivativePoint

EXCHANGE = "BINANCE"


def connect(mysql_url: str):
    parsed = urlparse(mysql_url.replace("mysql+pymysql://", "mysql://", 1).replace("mysql+mysqldb://", "mysql://", 1))
    connection = pymysql.connect(
        host=parsed.hostname or "127.0.0.1",
        port=parsed.port or 3306,
        user=parsed.username,
        password=parsed.password or "",
        database=(parsed.path or "/cti").lstrip("/"),
        charset="utf8mb4",
        cursorclass=DictCursor,
    )
    return connection


def trade_date_of(epoch_millis: int) -> date:
    return datetime.fromtimestamp(epoch_millis / 1000, tz=timezone.utc).date()


def as_float(value) -> float | None:
    if value is None:
        return None
    return float(value)


def keep_closed(rows: list[dict]) -> list[dict]:
    return [row for row in rows if row.get("is_closed") in (1, True)]


class LoadedSeries:
    def __init__(self, symbol: str, status: str, bars: list[DailyBar], derivatives: list[DerivativePoint]):
        self.symbol = symbol
        self.status = status
        self.bars = bars
        self.derivatives = derivatives


def load_market(mysql_url: str, symbols: list[str], trade_date: date | None) -> list[LoadedSeries]:
    connection = connect(mysql_url)
    try:
        with connection.cursor() as cursor:
            cursor.execute("SET time_zone = '+00:00'")
            return [load_symbol(cursor, symbol, trade_date) for symbol in symbols]
    finally:
        connection.close()


def load_symbol(cursor, symbol: str, trade_date: date | None) -> LoadedSeries:
    cursor.execute(
        """
        SELECT open_time, high_price, low_price, close_price, quote_volume,
               taker_buy_quote_volume, is_closed
        FROM ct_kline
        WHERE exchange_code = %s AND symbol = %s AND interval_code = '1d' AND is_closed = 1
        ORDER BY open_time
        """,
        (EXCHANGE, symbol),
    )
    bars: list[DailyBar] = []
    for row in keep_closed(list(cursor.fetchall())):
        current = trade_date_of(int(row["open_time"]))
        if trade_date is not None and current > trade_date:
            continue
        quote = as_float(row["quote_volume"])
        taker = as_float(row["taker_buy_quote_volume"])
        bars.append(DailyBar(
            trade_date=current,
            open_time=int(row["open_time"]),
            high=float(row["high_price"]),
            low=float(row["low_price"]),
            close=float(row["close_price"]),
            quote_volume=None if quote is None or quote < 0 else quote,
            taker_buy_quote=None if taker is None or taker < 0 else taker,
        ))
    cursor.execute(
        """
        SELECT snapshot_time, open_interest_value, funding_rate
        FROM ct_derivatives_snapshot
        WHERE symbol = %s
        ORDER BY snapshot_time
        """,
        (symbol,),
    )
    derivatives: list[DerivativePoint] = []
    for row in cursor.fetchall():
        current = trade_date_of(int(row["snapshot_time"]))
        if trade_date is not None and current > trade_date:
            continue
        derivatives.append(DerivativePoint(
            trade_date=current,
            open_interest_value=as_float(row["open_interest_value"]),
            funding_rate=as_float(row["funding_rate"]),
        ))
    cursor.execute(
        """
        SELECT status FROM ct_data_quality_report
        WHERE exchange_code = %s AND symbol = %s AND interval_code = '1d'
        ORDER BY check_date DESC
        LIMIT 1
        """,
        (EXCHANGE, symbol),
    )
    quality = cursor.fetchone()
    status = quality["status"] if quality else "WARNING"
    return LoadedSeries(symbol, status, bars, derivatives)
