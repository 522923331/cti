from decimal import Decimal

from strategies.base import BaseStrategy, closed_bars, decimal_text, signal


def build(code, parameters):
    factories = {
        "EMA_TREND": EmaTrend,
        "DONCHIAN_BREAKOUT": DonchianBreakout,
        "MOMENTUM": Momentum,
        "RELATIVE_STRENGTH": RelativeStrength,
        "MEAN_REVERSION": MeanReversion,
    }
    if code not in factories:
        raise KeyError(code)
    return factories[code](dict(parameters))


class EmaTrend(BaseStrategy):

    def __init__(self, parameters):
        self.parameters = parameters

    def strategy_code(self):
        return "EMA_TREND"

    def strategy_version(self):
        return "1.0.0"

    def get_parameters(self):
        return dict(self.parameters)

    def calculate_features(self, data):
        bars = closed_bars(data["bars"])
        return {
            "emaFast": _ema([bar["close"] for bar in bars], int(self.parameters["emaFast"])),
            "emaMid": _ema([bar["close"] for bar in bars], int(self.parameters["emaMid"])),
            "emaSlow": _ema([bar["close"] for bar in bars], int(self.parameters["emaSlow"])),
        }

    def generate_signal(self, data):
        bars = closed_bars(data["bars"])
        symbol = bars[-1]["symbol"] if bars else data.get("symbol")
        features = self.calculate_features({"bars": bars})
        if not bars or features["emaSlow"][-1] is None:
            return signal(self, symbol, "NO_TRADE", None, ["warmup"], None)
        fast, mid, slow = features["emaFast"][-1], features["emaMid"][-1], features["emaSlow"][-1]
        close = Decimal(bars[-1]["close"])
        stacked = fast > mid > slow and close > fast
        adx = bars[-1].get("adx")
        minimum = self.parameters.get("adxMinimum")
        if not stacked or adx is None or minimum is None or Decimal(str(adx)) < Decimal(str(minimum)):
            return signal(self, symbol, "NO_TRADE", None, ["ema_stack_or_adx"], bars[-1].get("signalTime"))
        return signal(self, symbol, "BUY", None, ["ema20_stack", "adx_confirmed"], bars[-1].get("signalTime"))

    def calculate_exit(self, data, position):
        current = self.generate_signal(data)
        if current["signal"] != "BUY":
            current["signal"] = "EXIT"
        return current


class DonchianBreakout(BaseStrategy):

    def __init__(self, parameters):
        self.parameters = parameters

    def strategy_code(self):
        return "DONCHIAN_BREAKOUT"

    def strategy_version(self):
        return "n" + str(self.parameters["lookback"])

    def get_parameters(self):
        return dict(self.parameters)

    def calculate_features(self, data):
        bars = closed_bars(data["bars"])
        lookback = int(self.parameters["lookback"])
        prior_high = []
        for index in range(len(bars)):
            window = bars[index - lookback:index]
            prior_high.append(None if len(window) < lookback else max(Decimal(bar["high"]) for bar in window))
        return {"priorHigh": prior_high}

    def generate_signal(self, data):
        bars = closed_bars(data["bars"])
        symbol = bars[-1]["symbol"] if bars else None
        features = self.calculate_features({"bars": bars})
        if not bars or features["priorHigh"][-1] is None:
            return signal(self, symbol, "NO_TRADE", None, ["warmup"], None)
        close = Decimal(bars[-1]["close"])
        prior = features["priorHigh"][-1]
        if close > prior:
            return signal(self, symbol, "BUY", None, [
                "close " + decimal_text(close) + " > prior_high " + decimal_text(prior),
                "window_excludes_current_bar",
            ], bars[-1].get("signalTime"))
        return signal(self, symbol, "NO_TRADE", None, ["inside_channel"], bars[-1].get("signalTime"))

    def calculate_exit(self, data, position):
        current = self.generate_signal(data)
        if current["signal"] != "BUY":
            current["signal"] = "EXIT"
            current["explanation"] = ["close_not_above_prior_high", "window_excludes_current_bar"]
        return current


class Momentum(BaseStrategy):

    def __init__(self, parameters):
        self.parameters = parameters

    def strategy_code(self):
        return "MOMENTUM"

    def strategy_version(self):
        return "1.0.0"

    def get_parameters(self):
        return dict(self.parameters)

    def calculate_features(self, data):
        closes = [Decimal(bar["close"]) for bar in closed_bars(data["bars"])]
        values = {}
        for period in self.parameters["periods"]:
            values[period] = None if len(closes) <= period or closes[-1 - period] <= 0 else closes[-1] / closes[-1 - period] - 1
        return values

    def generate_signal(self, data):
        bars = closed_bars(data["bars"])
        symbol = bars[-1]["symbol"] if bars else None
        features = self.calculate_features({"bars": bars})
        if any(value is None for value in features.values()):
            return signal(self, symbol, "NO_TRADE", None, ["warmup"], None)
        lead = features[self.parameters["leadPeriod"]]
        name = "BUY" if lead > 0 else "NO_TRADE"
        return signal(self, symbol, name, lead, ["lead_momentum"], bars[-1].get("signalTime"))

    def calculate_exit(self, data, position):
        current = self.generate_signal(data)
        if current["signal"] != "BUY":
            current["signal"] = "EXIT"
        return current


class RelativeStrength(BaseStrategy):

    def __init__(self, parameters):
        self.parameters = parameters

    def strategy_code(self):
        return "RELATIVE_STRENGTH"

    def strategy_version(self):
        return "1.0.0"

    def get_parameters(self):
        return dict(self.parameters)

    def calculate_features(self, data):
        lookback = int(self.parameters["lookback"])
        scored = []
        btc = _return(_closes(data, "BTCUSDT"), lookback)
        for asset in data["assets"]:
            change = _return([Decimal(bar["close"]) for bar in asset["bars"]], lookback)
            scored.append(None if change is None or btc is None else change - btc)
        return {"relativeStrength": scored}

    def generate_signal(self, data):
        features = self.calculate_features(data)
        ranked = []
        for asset, value in zip(data["assets"], features["relativeStrength"], strict=True):
            ranked.append((asset["symbol"], value))
        eligible = []
        for asset, (symbol, value) in zip(data["assets"], ranked, strict=True):
            if value is None or not _uptrend(asset, self.parameters):
                continue
            eligible.append((symbol, value))
        eligible.sort(key=lambda item: (-item[1], item[0]))
        selected = {symbol for symbol, _value in eligible[:int(self.parameters["holdings"])]}
        rows = []
        for symbol, value in ranked:
            rows.append(signal(
                self, symbol, "BUY" if symbol in selected else "NO_TRADE", value,
                ["relative_strength_rank"], None,
            ))
        return rows

    def calculate_exit(self, data, position):
        rows = self.generate_signal(data)
        for row in rows:
            if row["symbol"] == position.get("symbol") and row["signal"] != "BUY":
                row["signal"] = "EXIT"
        return rows


class MeanReversion(BaseStrategy):

    def __init__(self, parameters):
        self.parameters = parameters

    def strategy_code(self):
        return "MEAN_REVERSION"

    def strategy_version(self):
        return "1.0.0"

    def get_parameters(self):
        return dict(self.parameters)

    def calculate_features(self, data):
        closes = [Decimal(bar["close"]) for bar in closed_bars(data["bars"])]
        window = int(self.parameters["window"])
        if len(closes) < window:
            return {"z": None}
        sample = closes[-window:]
        mean = sum(sample) / Decimal(window)
        if window < 2:
            return {"z": None}
        variance = sum((value - mean) ** 2 for value in sample) / Decimal(window - 1)
        if variance == 0:
            return {"z": None}
        return {"z": (closes[-1] - mean) / variance.sqrt()}

    def generate_signal(self, data):
        bars = closed_bars(data["bars"])
        symbol = bars[-1]["symbol"] if bars else None
        if data.get("regime") != "SIDEWAYS":
            return signal(self, symbol, "NO_TRADE", None, ["not_sideways"], None)
        if self.parameters.get("entryZ") is None:
            return signal(self, symbol, "NO_TRADE", None, ["parameter_incomplete"], None)
        z_score = self.calculate_features({"bars": bars})["z"]
        if z_score is None:
            return signal(self, symbol, "NO_TRADE", None, ["z_undefined"], None)
        name = "BUY" if z_score <= Decimal(str(self.parameters["entryZ"])) * Decimal("-1") else "NO_TRADE"
        return signal(self, symbol, name, z_score, ["z_score"], bars[-1].get("signalTime"))

    def calculate_exit(self, data, position):
        if data.get("regime") != "SIDEWAYS":
            return signal(self, position.get("symbol"), "EXIT", None, ["not_sideways"], None)
        z_score = self.calculate_features(data)["z"]
        if z_score is not None and self.parameters.get("exitZ") is not None and z_score >= Decimal(str(self.parameters["exitZ"])):
            return signal(self, position.get("symbol"), "EXIT", z_score, ["z_recovered"], None)
        return signal(self, position.get("symbol"), "HOLD", z_score, ["hold"], None)


def _ema(values, period):
    alpha = Decimal(2) / Decimal(period + 1)
    out = []
    window = []
    previous = None
    for value in values:
        price = Decimal(value)
        if previous is None:
            window.append(price)
            if len(window) == period:
                previous = sum(window) / Decimal(period)
                out.append(previous)
            else:
                out.append(None)
            continue
        previous = price * alpha + previous * (Decimal(1) - alpha)
        out.append(previous)
    return out


def _closes(data, symbol):
    for asset in data["assets"]:
        if asset["symbol"] == symbol:
            return [Decimal(bar["close"]) for bar in asset["bars"]]
    return []


def _uptrend(asset, parameters):
    if not parameters.get("requireUptrend"):
        return True
    closes = [bar["close"] for bar in asset["bars"]]
    average = _ema(closes, int(parameters["emaPeriod"]))
    return average[-1] is not None and Decimal(closes[-1]) > average[-1]


def _return(closes, lookback):
    if len(closes) <= lookback or closes[-1 - lookback] <= 0:
        return None
    return closes[-1] / closes[-1 - lookback] - 1
