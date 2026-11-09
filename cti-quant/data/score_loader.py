"""Load closed daily features and the enabled strategy. Forming bars are not read."""

import json
from datetime import date, datetime

from config.settings import settings
from data.market_loader import EXCHANGE, as_float, connect, trade_date_of
from engines.models import DayInput


def load_score_inputs(mysql_url: str, symbols: list[str], trade_date):
    connection = connect(mysql_url)
    try:
        with connection.cursor() as cursor:
            cursor.execute("SET time_zone = '+00:00'")
            config = load_config(cursor)
            histories = {symbol: load_symbol(cursor, symbol, trade_date) for symbol in symbols}
            return config, histories
    finally:
        connection.close()


def load_config(cursor) -> dict:
    cursor.execute(
        """
        SELECT strategy_version, config_json
        FROM ct_strategy_config
        WHERE enabled = 1 AND strategy_name = %s
        ORDER BY id DESC
        LIMIT 1
        """,
        (settings.strategy_name,),
    )
    row = cursor.fetchone()
    if row is None:
        raise RuntimeError("没有启用的策略配置")
    if row["strategy_version"] != settings.strategy_version:
        raise RuntimeError("启用的策略版本和运行版本不一致")
    payload = row["config_json"]
    config = json.loads(payload) if isinstance(payload, str) else payload
    if config.get("strategyVersion") != settings.strategy_version:
        raise RuntimeError("策略 JSON 里的版本和运行版本不一致")
    return config


def load_symbol(cursor, symbol: str, trade_date) -> list[DayInput]:
    cursor.execute(
        """
        SELECT open_time, high_price, low_price, close_price, quote_volume
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
        quote = as_float(row["quote_volume"])
        prices[current] = {
            "high": float(row["high_price"]),
            "low": float(row["low_price"]),
            "close": float(row["close_price"]),
            "quote": None if quote is None or quote < 0 else quote,
        }
    cursor.execute(
        """
        SELECT trade_date, ema20, ema50, ema200, adx14,
               return_7d, return_14d, return_30d, return_90d,
               realized_volatility_20d, volume_ratio_20d,
               spot_delta, spot_delta_ratio, oi_change_7d, funding_zscore,
               data_quality_status
        FROM ct_feature_daily
        WHERE symbol = %s AND feature_version = %s
        ORDER BY trade_date
        """,
        (symbol, settings.feature_version),
    )
    bars: list[DayInput] = []
    for row in cursor.fetchall():
        current = row["trade_date"]
        if isinstance(current, datetime):
            current = current.date()
        if not isinstance(current, date):
            raise RuntimeError(f"{symbol} 的特征日期无法识别")
        if trade_date is not None and current > trade_date:
            continue
        price = prices.get(current)
        if price is None:
            continue
        bars.append(DayInput(
            trade_date=current,
            high=price["high"],
            low=price["low"],
            close=price["close"],
            quote_volume=price["quote"],
            ema20=as_float(row["ema20"]),
            ema50=as_float(row["ema50"]),
            ema200=as_float(row["ema200"]),
            adx14=as_float(row["adx14"]),
            return_7d=as_float(row["return_7d"]),
            return_14d=as_float(row["return_14d"]),
            return_30d=as_float(row["return_30d"]),
            return_90d=as_float(row["return_90d"]),
            realized_volatility_20d=as_float(row["realized_volatility_20d"]),
            volume_ratio_20d=as_float(row["volume_ratio_20d"]),
            spot_delta=as_float(row["spot_delta"]),
            spot_delta_ratio=as_float(row["spot_delta_ratio"]),
            oi_change_7d=as_float(row["oi_change_7d"]),
            funding_zscore=as_float(row["funding_zscore"]),
            data_quality_status=row["data_quality_status"],
        ))
    return bars
