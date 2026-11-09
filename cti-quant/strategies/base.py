from abc import ABC, abstractmethod
from decimal import Decimal


class BaseStrategy(ABC):

    @abstractmethod
    def strategy_code(self) -> str:
        pass

    @abstractmethod
    def strategy_version(self) -> str:
        pass

    @abstractmethod
    def get_parameters(self) -> dict:
        pass

    @abstractmethod
    def calculate_features(self, data):
        pass

    @abstractmethod
    def generate_signal(self, data):
        pass

    @abstractmethod
    def calculate_exit(self, data, position):
        pass


def closed_bars(bars):
    return [bar for bar in bars if bar.get("closed", True)]


def decimal_text(value):
    text = format(Decimal(str(value)), "f")
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text


def signal(strategy, symbol, name, score, explanation, signal_time=None):
    return {
        "strategyCode": strategy.strategy_code(),
        "strategyVersion": strategy.strategy_version(),
        "symbol": symbol,
        "signal": name,
        "score": None if score is None else decimal_text(score),
        "targetWeight": None,
        "stopPrice": None,
        "signalTime": signal_time,
        "explanation": explanation,
    }
