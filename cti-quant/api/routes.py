from fastapi import APIRouter

from api.schemas import HealthResponse
from config.settings import settings

router = APIRouter()


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="UP",
        service=settings.app_name,
        featureVersion=settings.feature_version,
        strategyVersion=settings.strategy_version,
    )
