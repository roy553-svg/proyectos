"""Tests de los endpoints de administracion (Fase 2)."""

from __future__ import annotations

from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.models import NewsArticle, PublicationStatus, WeeklyEdition
from tests.conftest import ADMIN_TOKEN

AUTH = {"X-Admin-Token": ADMIN_TOKEN}


def test_admin_requires_token(admin_client: TestClient) -> None:
    """Sin cabecera de token la respuesta es 401."""
    assert admin_client.post("/api/v1/admin/pipeline/run").status_code == 401
    assert admin_client.get("/api/v1/admin/pipeline/runs").status_code == 401


def test_admin_rejects_wrong_token(admin_client: TestClient) -> None:
    response = admin_client.get(
        "/api/v1/admin/pipeline/runs", headers={"X-Admin-Token": "incorrecto"}
    )
    assert response.status_code == 401


def test_admin_disabled_without_configured_token(client: TestClient) -> None:
    """Si ADMIN_API_TOKEN no esta configurado los endpoints devuelven 503."""
    response = client.get(
        "/api/v1/admin/pipeline/runs", headers={"X-Admin-Token": "lo-que-sea"}
    )
    assert response.status_code == 503


def test_run_pipeline_creates_published_edition(
    admin_client: TestClient, db_session: Session
) -> None:
    """El pipeline completo funciona offline (sin Gemini, Replicate ni NewsAPI)."""
    response = admin_client.post("/api/v1/admin/pipeline/run", headers=AUTH, json={})

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "success"
    assert body["trigger"] == "manual"
    assert body["items_fetched"] == 3
    assert body["articles_created"] == 3
    # Sin GEMINI_API_KEY el redactor offline no cuenta como "escrito por IA".
    assert body["articles_ai_written"] == 0
    assert body["videos_requested"] == 0

    edition = db_session.get(WeeklyEdition, body["edition_id"])
    assert edition.status is PublicationStatus.PUBLISHED
    assert db_session.query(NewsArticle).count() == 3
    assert all(article.summary for article in db_session.query(NewsArticle))


def test_run_pipeline_as_draft_and_list_runs(admin_client: TestClient) -> None:
    created = admin_client.post(
        "/api/v1/admin/pipeline/run",
        headers=AUTH,
        json={"publish": False, "week_start": "2026-09-21"},
    )
    assert created.status_code == 200

    runs = admin_client.get("/api/v1/admin/pipeline/runs", headers=AUTH)
    assert runs.status_code == 200
    payload = runs.json()
    assert len(payload) == 1
    assert payload[0]["id"] == created.json()["id"]


def test_generated_edition_is_served_by_the_public_endpoint(
    admin_client: TestClient, make_preferences
) -> None:
    """La edicion generada por el pipeline se sirve en GET /api/v1/news."""
    make_preferences(42)
    admin_client.post("/api/v1/admin/pipeline/run", headers=AUTH, json={})

    response = admin_client.get("/api/v1/news", params={"user_id": 42})

    assert response.status_code == 200
    body = response.json()
    assert len(body["articles"]) == 3
    assert all(article["video_url"] is None for article in body["articles"])
    assert all(article["video_status"] == "not_requested" for article in body["articles"])


def test_videos_refresh_without_provider_is_a_noop(admin_client: TestClient) -> None:
    response = admin_client.post("/api/v1/admin/videos/refresh", headers=AUTH)

    assert response.status_code == 200
    assert response.json() == {
        "checked": 0,
        "ready": 0,
        "failed": 0,
        "still_pending": 0,
        "errors": [],
    }


def test_integrations_status_reports_offline_adapters(admin_client: TestClient) -> None:
    response = admin_client.get("/api/v1/admin/integrations", headers=AUTH)

    assert response.status_code == 200
    body = response.json()
    assert body == {
        "news_provider": "local",
        "text_generator": "offline",
        "video_generator": "disabled",
        "video_generation_enabled": False,
        "scheduler_enabled": False,
        "scheduler_running": False,
        "next_weekly_run": None,
    }
