from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_is_up():
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert body["service"] == "cti-quant"
    assert body["featureVersion"] == "features-1.0.0"
    assert body["strategyVersion"] == "1.0.0"
