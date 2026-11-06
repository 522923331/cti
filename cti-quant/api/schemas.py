from datetime import date

from pydantic import BaseModel, Field, field_validator


class HealthResponse(BaseModel):
    status: str
    service: str
    featureVersion: str
    strategyVersion: str


class CalculateDailyRequest(BaseModel):
    tradeDate: date | None = None
    symbols: list[str] = Field(min_length=1)

    @field_validator("symbols")
    @classmethod
    def uppercase(cls, symbols: list[str]) -> list[str]:
        cleaned: list[str] = []
        for symbol in symbols:
            text = symbol.strip().upper()
            if not text:
                raise ValueError("symbol 不能为空")
            cleaned.append(text)
        return cleaned


class FeatureRowResponse(BaseModel):
    symbol: str
    tradeDate: date
    ema20: str | None = None
    ema50: str | None = None
    ema200: str | None = None
    return7d: str | None = None
    return14d: str | None = None
    return30d: str | None = None
    return90d: str | None = None
    atr14: str | None = None
    atrPct: str | None = None
    adx14: str | None = None
    rsi14: str | None = None
    realizedVolatility20d: str | None = None
    volumeRatio20d: str | None = None
    spotDelta: str | None = None
    spotDeltaRatio: str | None = None
    oiChange1d: str | None = None
    oiChange7d: str | None = None
    fundingRate: str | None = None
    fundingZscore: str | None = None
    featureVersion: str
    dataQualityStatus: str


class CalculateDailyResponse(BaseModel):
    featureVersion: str
    rows: list[FeatureRowResponse]


class ScoreRequest(BaseModel):
    tradeDate: date | None = None
    symbols: list[str] = Field(min_length=1)

    @field_validator("symbols")
    @classmethod
    def uppercase(cls, symbols: list[str]) -> list[str]:
        cleaned: list[str] = []
        for symbol in symbols:
            text = symbol.strip().upper()
            if not text:
                raise ValueError("symbol 不能为空")
            cleaned.append(text)
        return cleaned


class RegimeResponse(BaseModel):
    tradeDate: date
    regime: str
    rawRegime: str
    confirmCount: int
    marketScore: str | None = None
    btcTrendScore: str | None = None
    marketBreadth: str | None = None
    btcVolatility: str | None = None
    totalExposureLimit: str
    riskOverride: str | None = None
    reason: dict
    strategyVersion: str


class ScoreResponse(BaseModel):
    symbol: str
    tradeDate: date
    marketScore: str | None = None
    trendScore: str | None = None
    flowScore: str | None = None
    derivativesScore: str | None = None
    sentimentScore: str | None = None
    totalScore: str | None = None
    signalLevel: str | None = None
    reason: dict
    risk: dict
    featureVersion: str
    strategyVersion: str


class RegimeCalculateResponse(BaseModel):
    strategyVersion: str
    featureVersion: str
    rows: list[RegimeResponse]


class ScoreCalculateResponse(BaseModel):
    strategyVersion: str
    featureVersion: str
    rows: list[ScoreResponse]


class SignalResponse(BaseModel):
    signalId: str
    symbol: str
    signalTime: str
    tradeDate: date
    signalType: str
    signalStatus: str
    score: str | None = None
    entryPrice: str | None = None
    stopPrice: str | None = None
    suggestedPosition: str | None = None
    riskAmount: str | None = None
    signalReason: dict
    invalidationReason: dict | None = None
    featureVersion: str
    strategyVersion: str


class SignalGenerateResponse(BaseModel):
    strategyVersion: str
    featureVersion: str
    signals: list[SignalResponse]
    account: dict
    positions: list[dict]
    equity: list[dict]


class PortfolioRiskResponse(BaseModel):
    strategyVersion: str
    risk: dict


class BacktestRequest(BaseModel):
    strategy: str
    startDate: date | None = None
    endDate: date | None = None
    costScenario: str = "BASE"
    symbols: list[str] = Field(min_length=1)

    @field_validator("symbols")
    @classmethod
    def uppercase(cls, symbols: list[str]) -> list[str]:
        return _symbols(symbols)

    @field_validator("costScenario")
    @classmethod
    def scenario(cls, value: str) -> str:
        text = value.strip().upper()
        if text not in {"LOW", "BASE", "HIGH"}:
            raise ValueError("成本情景只能是 LOW、BASE 或 HIGH")
        return text


class CompareRequest(BaseModel):
    startDate: date | None = None
    endDate: date | None = None
    costScenario: str = "BASE"
    strategies: list[str] = Field(min_length=1)
    symbols: list[str] = Field(min_length=1)

    @field_validator("symbols")
    @classmethod
    def uppercase(cls, symbols: list[str]) -> list[str]:
        return _symbols(symbols)

    @field_validator("costScenario")
    @classmethod
    def scenario(cls, value: str) -> str:
        text = value.strip().upper()
        if text not in {"LOW", "BASE", "HIGH"}:
            raise ValueError("成本情景只能是 LOW、BASE 或 HIGH")
        return text


class BacktestResponse(BaseModel):
    strategyVersion: str
    featureVersion: str
    strategy: str
    startDate: date | None = None
    endDate: date | None = None
    costScenario: str
    metrics: dict
    report: dict
    equity: list[dict]
    trades: list[dict]


class WalkForwardResponse(BaseModel):
    strategyVersion: str
    featureVersion: str
    status: str
    startDate: date | None = None
    endDate: date | None = None
    sample: str
    reason: str | None = None
    folds: list[dict]
    metrics: dict
    acceptance: dict


class SensitivityResponse(BaseModel):
    strategyVersion: str
    featureVersion: str
    startDate: date | None = None
    endDate: date | None = None
    groups: list[dict]
    note: str


def _symbols(symbols: list[str]) -> list[str]:
    cleaned: list[str] = []
    for symbol in symbols:
        text = symbol.strip().upper()
        if not text:
            raise ValueError("symbol 不能为空")
        cleaned.append(text)
    return cleaned
