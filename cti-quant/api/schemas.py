from pydantic import BaseModel


class HealthResponse(BaseModel):
    status: str
    service: str
    featureVersion: str
    strategyVersion: str
