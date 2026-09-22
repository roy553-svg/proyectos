"""Tests funcionales de GET /api/v1/news."""

from __future__ import annotations

from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.models import ArticleCategory, PublicationStatus, WeeklyEdition


def test_returns_published_edition_and_articles(
    client: TestClient, published_edition: WeeklyEdition, make_preferences
) -> None:
    """Un usuario sin filtro de categorias recibe la edicion publicada."""
    make_preferences(1)

    response = client.get("/api/v1/news", params={"user_id": 1})

    assert response.status_code == 200
    payload = response.json()
    assert payload["edition"]["id"] == published_edition.id
    assert payload["edition"]["week_start"] == "2026-09-21"
    assert payload["edition"]["week_end"] == "2026-09-27"
    # Solo los articulos en el idioma preferido (es): el articulo en ingles no.
    titles = [article["title"] for article in payload["articles"]]
    assert titles == [
        "Varitas inteligentes",
        "Nueva especie de bowtruckle",
        "Las Arpias ganan la liga",
    ]
    assert [article["position"] for article in payload["articles"]] == [1, 2, 3]


def test_preferred_categories_filter_articles(
    client: TestClient, published_edition: WeeklyEdition, make_preferences
) -> None:
    """Las categorias preferidas filtran el resultado."""
    make_preferences(
        2, categories=[ArticleCategory.SCIENCE.value, ArticleCategory.SPORTS.value]
    )

    response = client.get("/api/v1/news", params={"user_id": 2})

    assert response.status_code == 200
    categories = {article["category"] for article in response.json()["articles"]}
    assert categories == {"science", "sports"}


def test_language_preference_filters_articles(
    client: TestClient, published_edition: WeeklyEdition, make_preferences
) -> None:
    """El idioma preferido tambien filtra."""
    make_preferences(3, language="en")

    response = client.get("/api/v1/news", params={"user_id": 3})

    assert response.status_code == 200
    articles = response.json()["articles"]
    assert [article["title"] for article in articles] == ["Smart wands"]


def test_max_articles_preference_limits_results(
    client: TestClient, published_edition: WeeklyEdition, make_preferences
) -> None:
    """``max_articles`` limita el numero de noticias devueltas."""
    make_preferences(4, max_articles=2)

    response = client.get("/api/v1/news", params={"user_id": 4})

    assert response.status_code == 200
    assert len(response.json()["articles"]) == 2


def test_include_animated_only_filters_articles(
    client: TestClient, published_edition: WeeklyEdition, make_preferences
) -> None:
    """Con ``include_animated_only`` solo llegan noticias con video."""
    make_preferences(5, include_animated_only=True)

    response = client.get("/api/v1/news", params={"user_id": 5})

    assert response.status_code == 200
    articles = response.json()["articles"]
    assert len(articles) == 1
    assert articles[0]["video_url"] == "https://cdn.example.com/tech.mp4"


def test_video_url_can_be_null(
    client: TestClient, published_edition: WeeklyEdition, make_preferences
) -> None:
    """``video_url`` es opcional porque la Fase 2 aun no existe."""
    make_preferences(6, categories=[ArticleCategory.SCIENCE.value])

    response = client.get("/api/v1/news", params={"user_id": 6})

    assert response.status_code == 200
    article = response.json()["articles"][0]
    assert article["video_url"] is None
    assert article["image_url"] is not None


def test_draft_editions_are_never_returned(
    client: TestClient,
    published_edition: WeeklyEdition,
    draft_edition: WeeklyEdition,
    make_preferences,
) -> None:
    """Una edicion en borrador mas reciente no sustituye a la publicada."""
    make_preferences(7)

    response = client.get("/api/v1/news", params={"user_id": 7})

    assert response.status_code == 200
    assert response.json()["edition"]["id"] == published_edition.id
    titles = [article["title"] for article in response.json()["articles"]]
    assert "Noticia en borrador" not in titles


def test_unknown_user_returns_404(
    client: TestClient, published_edition: WeeklyEdition
) -> None:
    """Un usuario inexistente produce 404."""
    response = client.get("/api/v1/news", params={"user_id": 9999})

    assert response.status_code == 404
    assert "detail" in response.json()


def test_no_published_edition_returns_404(
    client: TestClient, draft_edition: WeeklyEdition, make_preferences
) -> None:
    """Sin ediciones publicadas el endpoint responde 404."""
    make_preferences(8)

    response = client.get("/api/v1/news", params={"user_id": 8})

    assert response.status_code == 404


def test_latest_published_edition_wins(
    client: TestClient,
    db_session: Session,
    published_edition: WeeklyEdition,
    make_preferences,
) -> None:
    """Si hay varias ediciones publicadas se devuelve la mas reciente."""
    from datetime import timedelta

    newer = WeeklyEdition(
        week_start=published_edition.week_start + timedelta(days=7),
        week_end=published_edition.week_end + timedelta(days=7),
        title="El Profeta - Edicion siguiente",
        status=PublicationStatus.PUBLISHED,
        published_at=published_edition.published_at,
    )
    db_session.add(newer)
    db_session.commit()
    make_preferences(9)

    response = client.get("/api/v1/news", params={"user_id": 9})

    assert response.status_code == 200
    assert response.json()["edition"]["id"] == newer.id
    assert response.json()["articles"] == []


def test_invalid_user_id_is_rejected(client: TestClient) -> None:
    """La validacion de parametros devuelve 422."""
    assert client.get("/api/v1/news", params={"user_id": "abc"}).status_code == 422
    assert client.get("/api/v1/news", params={"user_id": 0}).status_code == 422
    assert client.get("/api/v1/news").status_code == 422


def test_datetimes_are_serialized_in_utc(
    client: TestClient, published_edition: WeeklyEdition, make_preferences
) -> None:
    """Las fechas se devuelven en UTC con sufijo Z."""
    make_preferences(10)

    payload = client.get("/api/v1/news", params={"user_id": 10}).json()

    assert payload["edition"]["published_at"].endswith("Z")
    assert all(article["published_at"].endswith("Z") for article in payload["articles"])
