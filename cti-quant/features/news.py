"""Title-only news classification contract. Illegal payloads are dropped."""

import json

from pydantic import BaseModel, ConfigDict, Field, ValidationError


class NewsClassification(BaseModel):
    model_config = ConfigDict(extra="forbid")

    category: str
    sentiment: str
    impact_level: str
    confidence: float = Field(ge=0, le=1)
    related_assets: list[str] = Field(min_length=1)
    summary: str = Field(min_length=1)
    evidence: list[str]

    def model_post_init(self, context):
        categories = {
            "MACRO", "REGULATION", "ETF", "EXCHANGE", "PROJECT",
            "STABLECOIN", "SECURITY", "TOKEN_UNLOCK", "OTHER",
        }
        sentiments = {"POSITIVE", "NEGATIVE", "NEUTRAL", "UNCERTAIN"}
        impacts = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}
        if self.category not in categories or self.sentiment not in sentiments or self.impact_level not in impacts:
            raise ValueError("classification is outside the allowed labels")


def accept_classification(payload):
    try:
        raw = json.loads(payload) if isinstance(payload, str) else payload
        return NewsClassification.model_validate(raw)
    except (ValidationError, ValueError, TypeError, json.JSONDecodeError):
        return None
