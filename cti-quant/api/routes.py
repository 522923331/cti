import logging

from datetime import date

from fastapi import APIRouter, Depends, HTTPException

from api.schemas import (
    CalculateDailyRequest,
    CalculateDailyResponse,
    FeatureRowResponse,
    HealthResponse,
    BacktestRequest,
    BacktestResponse,
    CompareRequest,
    PortfolioRiskResponse,
    SensitivityResponse,
    WalkForwardResponse,
    RegimeCalculateResponse,
    RegimeResponse,
    ScoreCalculateResponse,
    ScoreRequest,
    ScoreResponse,
    SignalGenerateResponse,
    SignalResponse,
)
from config.settings import settings
from data.market_loader import load_market
from data.score_loader import load_score_inputs
from data.signal_loader import load_portfolio_risk, load_signal_inputs
from engines.backtest import STRATEGIES, run_strategy, sensitivity, walk_forward
from engines.book import run_book
from engines.risk_engine import money_text
from engines.score_builder import evaluate
from engines.weights import text
from features.decimals import to_decimal_string
from features.feature_builder import DailyFeature, build_daily_features

logger = logging.getLogger(__name__)
router = APIRouter()


def get_market_loader():
    return load_market


def get_score_loader():
    return load_score_inputs


def get_signal_loader():
    return load_signal_inputs


def get_risk_loader():
    return load_portfolio_risk


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="UP",
        service=settings.app_name,
        featureVersion=settings.feature_version,
        strategyVersion=settings.strategy_version,
    )


@router.post("/api/v1/features/daily/calculate", response_model=CalculateDailyResponse)
def calculate_daily(body: CalculateDailyRequest, loader=Depends(get_market_loader)) -> CalculateDailyResponse:
    loaded = loader(settings.mysql_url, body.symbols, body.tradeDate)
    rows: list[DailyFeature] = []
    for series in loaded:
        bars = series.bars
        derivatives = series.derivatives
        if body.tradeDate is not None:
            bars = [bar for bar in bars if bar.trade_date <= body.tradeDate]
            derivatives = [point for point in derivatives if point.trade_date <= body.tradeDate]
        built = build_daily_features(
            bars,
            derivatives,
            series.symbol,
            series.status,
            settings.feature_version,
        )
        if body.tradeDate is not None:
            built = [row for row in built if row.trade_date == body.tradeDate]
        rows.extend(built)
    logger.info("daily features symbols=%s rows=%s version=%s", len(body.symbols), len(rows), settings.feature_version)
    return CalculateDailyResponse(featureVersion=settings.feature_version, rows=[_row(row) for row in rows])


def _row(feature: DailyFeature) -> FeatureRowResponse:
    return FeatureRowResponse(
        symbol=feature.symbol,
        tradeDate=feature.trade_date,
        ema20=to_decimal_string(feature.ema20),
        ema50=to_decimal_string(feature.ema50),
        ema200=to_decimal_string(feature.ema200),
        return7d=to_decimal_string(feature.return_7d),
        return14d=to_decimal_string(feature.return_14d),
        return30d=to_decimal_string(feature.return_30d),
        return90d=to_decimal_string(feature.return_90d),
        atr14=to_decimal_string(feature.atr14),
        atrPct=to_decimal_string(feature.atr_pct),
        adx14=to_decimal_string(feature.adx14),
        rsi14=to_decimal_string(feature.rsi14),
        realizedVolatility20d=to_decimal_string(feature.realized_volatility_20d),
        volumeRatio20d=to_decimal_string(feature.volume_ratio_20d),
        spotDelta=to_decimal_string(feature.spot_delta),
        spotDeltaRatio=to_decimal_string(feature.spot_delta_ratio),
        oiChange1d=to_decimal_string(feature.oi_change_1d),
        oiChange7d=to_decimal_string(feature.oi_change_7d),
        fundingRate=to_decimal_string(feature.funding_rate),
        fundingZscore=to_decimal_string(feature.funding_zscore),
        featureVersion=feature.feature_version,
        dataQualityStatus=feature.data_quality_status,
    )


@router.post("/api/v1/market/regime/calculate", response_model=RegimeCalculateResponse)
def calculate_regime(body: ScoreRequest, loader=Depends(get_score_loader)) -> RegimeCalculateResponse:
    regimes, _scores = _evaluate(body, loader)
    return RegimeCalculateResponse(
        strategyVersion=settings.strategy_version,
        featureVersion=settings.feature_version,
        rows=[_regime(row) for row in regimes],
    )


@router.post("/api/v1/trend/score/calculate", response_model=ScoreCalculateResponse)
def calculate_scores(body: ScoreRequest, loader=Depends(get_score_loader)) -> ScoreCalculateResponse:
    _regimes, scores = _evaluate(body, loader)
    return ScoreCalculateResponse(
        strategyVersion=settings.strategy_version,
        featureVersion=settings.feature_version,
        rows=[_score(row) for row in scores],
    )


@router.post("/api/v1/signals/generate", response_model=SignalGenerateResponse)
def generate_signals(body: ScoreRequest, loader=Depends(get_signal_loader)) -> SignalGenerateResponse:
    config, days, regimes = loader(settings.mysql_url, body.symbols, body.tradeDate)
    signals, account, positions, equity, _risk = run_book(
        days, regimes, config, settings.feature_version, settings.strategy_version, settings.strategy_name,
    )
    if body.tradeDate is not None:
        signals = [row for row in signals if row.trade_date == body.tradeDate]
    logger.info("signals symbols=%s rows=%s", len(body.symbols), len(signals))
    return SignalGenerateResponse(
        strategyVersion=settings.strategy_version,
        featureVersion=settings.feature_version,
        signals=[_signal(row) for row in signals],
        account=account,
        positions=[_position(row) for row in positions],
        equity=[_equity(row) for row in equity],
    )


@router.post("/api/v1/backtest/run", response_model=BacktestResponse)
def run_backtest(body: BacktestRequest, loader=Depends(get_signal_loader)) -> BacktestResponse:
    return _backtest(body.strategy, body.startDate, body.endDate, body.costScenario, body.symbols, loader)


@router.post("/api/v1/backtest/compare", response_model=list[BacktestResponse])
def compare_backtests(body: CompareRequest, loader=Depends(get_signal_loader)) -> list[BacktestResponse]:
    unknown = [name for name in body.strategies if name not in STRATEGIES]
    if unknown:
        raise HTTPException(status_code=422, detail="策略无法识别")
    config, days, regimes = loader(settings.mysql_url, body.symbols, body.endDate)
    return [
        _response(name, body.startDate, body.endDate, body.costScenario, config, days, regimes)
        for name in body.strategies
    ]


@router.post("/api/v1/backtest/walk-forward", response_model=WalkForwardResponse)
def run_walk_forward(body: BacktestRequest, loader=Depends(get_signal_loader)) -> WalkForwardResponse:
    config, days, regimes = loader(settings.mysql_url, body.symbols, body.endDate)
    result = walk_forward(
        days, regimes, config, body.startDate, body.endDate, body.costScenario,
        settings.feature_version, settings.strategy_version,
    )
    return WalkForwardResponse(
        strategyVersion=settings.strategy_version,
        featureVersion=settings.feature_version,
        status=result["status"],
        startDate=date.fromisoformat(result["startDate"]) if result["startDate"] else None,
        endDate=date.fromisoformat(result["endDate"]) if result["endDate"] else None,
        sample=result["sample"],
        reason=result["reason"],
        folds=result["folds"],
        metrics=result["metrics"],
        acceptance=result["acceptance"],
    )


@router.post("/api/v1/backtest/sensitivity", response_model=SensitivityResponse)
def run_sensitivity(body: BacktestRequest, loader=Depends(get_signal_loader)) -> SensitivityResponse:
    config, days, regimes = loader(settings.mysql_url, body.symbols, body.endDate)
    result = sensitivity(days, regimes, config, body.startDate, body.endDate, settings.feature_version, settings.strategy_version)
    return SensitivityResponse(
        strategyVersion=settings.strategy_version,
        featureVersion=settings.feature_version,
        startDate=date.fromisoformat(result["startDate"]) if result["startDate"] else None,
        endDate=date.fromisoformat(result["endDate"]) if result["endDate"] else None,
        groups=result["groups"],
        note=result["note"],
    )


def _backtest(strategy, start, end, scenario, symbols, loader) -> BacktestResponse:
    if strategy not in STRATEGIES:
        raise HTTPException(status_code=422, detail="策略无法识别")
    config, days, regimes = loader(settings.mysql_url, symbols, end)
    return _response(strategy, start, end, scenario, config, days, regimes)


def _response(strategy, start, end, scenario, config, days, regimes) -> BacktestResponse:
    equity, fills, _signals, metrics, report = run_strategy(
        days, regimes, config, strategy, start, end, scenario, settings.feature_version, settings.strategy_version,
    )
    logger.info("backtest strategy=%s equity=%s trades=%s", strategy, len(equity), len(fills))
    return BacktestResponse(
        strategyVersion=settings.strategy_version,
        featureVersion=settings.feature_version,
        strategy=strategy,
        startDate=equity[0].trade_date if equity else start,
        endDate=equity[-1].trade_date if equity else end,
        costScenario=scenario,
        metrics=metrics,
        report=report,
        equity=[_equity(row) for row in equity],
        trades=[_fill(row) for row in fills],
    )


def _fill(row) -> dict:
    holding = None if row.opened_on is None else (row.trade_date - row.opened_on).days
    return {
        "tradeDate": row.trade_date.isoformat(),
        "symbol": row.symbol,
        "side": row.side,
        "quantity": money_text(row.quantity),
        "openPrice": money_text(row.open_price),
        "fillPrice": money_text(row.fill_price),
        "fee": money_text(row.fee),
        "slippage": money_text(row.slippage),
        "pnl": money_text(row.pnl),
        "openedOn": None if row.opened_on is None else row.opened_on.isoformat(),
        "holdingDays": holding,
    }


@router.post("/api/v1/portfolio/risk/calculate", response_model=PortfolioRiskResponse)
def calculate_portfolio_risk(body: ScoreRequest, loader=Depends(get_risk_loader)) -> PortfolioRiskResponse:
    risk = loader(settings.mysql_url, body.symbols, body.tradeDate)
    return PortfolioRiskResponse(strategyVersion=settings.strategy_version, risk=risk)


def _signal(row) -> SignalResponse:
    return SignalResponse(
        signalId=row.signal_id,
        symbol=row.symbol,
        signalTime=row.signal_time,
        tradeDate=row.trade_date,
        signalType=row.signal_type,
        signalStatus=row.signal_status,
        score=text(row.score),
        entryPrice=money_text(row.entry_price),
        stopPrice=money_text(row.stop_price),
        suggestedPosition=money_text(row.suggested_position),
        riskAmount=money_text(row.risk_amount),
        signalReason=row.reason,
        invalidationReason=row.invalidation,
        featureVersion=row.feature_version,
        strategyVersion=row.strategy_version,
    )


def _position(row) -> dict:
    return {
        "symbol": row.symbol,
        "quantity": money_text(row.quantity),
        "avgEntryPrice": money_text(row.entry_price),
        "stopPrice": money_text(row.stop_price),
        "highestClose": money_text(row.highest_close),
        "openedOn": row.opened_on.isoformat(),
        "updatedOn": row.updated_on.isoformat(),
        "marketValue": money_text(row.market_value),
    }


def _equity(row) -> dict:
    return {
        "tradeDate": row.trade_date.isoformat(),
        "cash": money_text(row.cash),
        "equity": money_text(row.equity),
        "peakEquity": money_text(row.peak_equity),
        "drawdown": money_text(row.drawdown),
        "exposure": money_text(row.exposure),
    }


def _evaluate(body: ScoreRequest, loader):
    config, histories = loader(settings.mysql_url, body.symbols, body.tradeDate)
    if body.tradeDate is not None:
        histories = {
            symbol: [bar for bar in bars if bar.trade_date <= body.tradeDate]
            for symbol, bars in histories.items()
        }
    regimes, scores = evaluate(histories, config, settings.feature_version, settings.strategy_version)
    if body.tradeDate is not None:
        regimes = [row for row in regimes if row.trade_date == body.tradeDate]
        scores = [row for row in scores if row.trade_date == body.tradeDate]
    logger.info("scores symbols=%s regimes=%s rows=%s", len(body.symbols), len(regimes), len(scores))
    return regimes, scores


def _regime(row) -> RegimeResponse:
    return RegimeResponse(
        tradeDate=row.trade_date,
        regime=row.regime,
        rawRegime=row.raw_regime,
        confirmCount=row.confirm_count,
        marketScore=text(row.market_score),
        btcTrendScore=text(row.btc_trend_score),
        marketBreadth=text(row.market_breadth),
        btcVolatility=text(row.btc_volatility),
        totalExposureLimit=text(row.total_exposure_limit),
        riskOverride=row.risk_override,
        reason=row.reason,
        strategyVersion=settings.strategy_version,
    )


def _score(row) -> ScoreResponse:
    return ScoreResponse(
        symbol=row.symbol,
        tradeDate=row.trade_date,
        marketScore=text(row.market_score),
        trendScore=text(row.trend_score),
        flowScore=text(row.flow_score),
        derivativesScore=text(row.derivatives_score),
        sentimentScore=text(row.sentiment_score),
        totalScore=text(row.total_score),
        signalLevel=row.signal_level,
        reason=row.reason,
        risk=row.risk,
        featureVersion=row.feature_version,
        strategyVersion=row.strategy_version,
    )
