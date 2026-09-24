"""La aplicacion arranca y expone su documentacion."""

from __future__ import annotations

from fastapi.testclient import TestClient


def test_health_endpoint(client: TestClient) -> None:
    response = client.get("/api/v1/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_openapi_schema_includes_news_endpoint(client: TestClient) -> None:
    response = client.get("/openapi.json")
    assert response.status_code == 200
    assert "/api/v1/news" in response.json()["paths"]


def test_docs_are_available(client: TestClient) -> None:
    assert client.get("/docs").status_code == 200
