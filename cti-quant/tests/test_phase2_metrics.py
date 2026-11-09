from features.news import accept_classification
from indicators.onchain import exchange_netflow


def test_exchange_netflow_stays_empty_when_a_side_unit_or_provider_cannot_be_combined():
    assert exchange_netflow(1, 2, "BTC", "BTC", "A", "A") == -1
    assert exchange_netflow(0, 2, "BTC", "BTC", "A", "A") == -2
    assert exchange_netflow(None, 2, "BTC", "BTC", "A", "A") is None
    assert exchange_netflow(1, None, "BTC", "BTC", "A", "A") is None
    assert exchange_netflow(1, 2, "BTC", "ETH", "A", "A") is None
    assert exchange_netflow(1, 2, None, "BTC", "A", "A") is None
    assert exchange_netflow(1, 2, "BTC", "BTC", "A", "B") is None
    assert exchange_netflow(-1, 2, "BTC", "BTC", "A", "A") is None


def test_illegal_news_classification_is_rejected():
    good = {
        "category": "EXCHANGE",
        "sentiment": "UNCERTAIN",
        "impact_level": "LOW",
        "confidence": 0.4,
        "related_assets": ["MARKET_WIDE"],
        "summary": "title only",
        "evidence": ["title"],
    }
    accepted = accept_classification(good)
    assert accepted is not None
    assert accepted.category == "EXCHANGE"
    assert accepted.sentiment != "BUY"

    bought = dict(good, sentiment="BUY")
    assert accept_classification(bought) is None
    assert accept_classification(dict(good, confidence=1.2)) is None
    assert accept_classification(dict(good, signal="BUY")) is None
    assert accept_classification("{") is None
