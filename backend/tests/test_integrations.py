"""Tests de los adaptadores externos.

Las peticiones HTTP se interceptan con ``respx``: no se abre ninguna conexion
real ni se necesita ninguna clave.
"""

from __future__ import annotations

import json

import httpx
import pytest
import respx

from app.core.config import Settings
from app.integrations.errors import IntegrationError
from app.integrations.factory import build_providers
from app.integrations.gemini_text_generator import GeminiTextGenerator
from app.integrations.local_news_provider import LocalNewsProvider
from app.integrations.newsapi_provider import NewsApiProvider
from app.integrations.offline_text_generator import OfflineTextGenerator
from app.integrations.replicate_video_generator import ReplicateVideoGenerator
from app.integrations.schemas import RawNewsItem
from app.models.enums import VideoStatus

GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta"
REPLICATE_URL = "https://api.replicate.com/v1"
NEWSAPI_URL = "https://newsapi.org/v2"


def _item() -> RawNewsItem:
    return RawNewsItem(
        title="Titular original",
        summary="Entradilla original.",
        content="Cuerpo original de la noticia. Con una segunda frase.",
        category="magic",
        language="es",
        image_url="https://cdn.example.com/imagen.jpg",
    )


# --- Fabrica -------------------------------------------------------------


def test_factory_uses_offline_adapters_without_keys() -> None:
    providers = build_providers(Settings(database_url="sqlite://"))
    assert providers.describe() == "news=local text=offline video=disabled"
    assert providers.video.enabled is False


def test_factory_uses_real_adapters_when_configured() -> None:
    providers = build_providers(
        Settings(
            database_url="sqlite://",
            newsapi_api_key="k",
            gemini_api_key="k",
            replicate_api_token="k",
            replicate_model_version="v1",
        )
    )
    assert providers.describe() == "news=newsapi text=gemini video=replicate"
    assert providers.video.enabled is True


# --- Proveedores de noticias --------------------------------------------


def test_local_provider_filters_by_category() -> None:
    items = LocalNewsProvider().fetch(limit=10, language="es", categories=["sports"])
    assert items and all(item.category == "sports" for item in items)


@respx.mock
def test_newsapi_provider_normalizes_articles() -> None:
    route = respx.get(f"{NEWSAPI_URL}/top-headlines").mock(
        return_value=httpx.Response(
            200,
            json={
                "status": "ok",
                "articles": [
                    {
                        "title": "Titular",
                        "description": "Descripcion",
                        "content": "Cuerpo",
                        "url": "https://example.com/n",
                        "urlToImage": "https://example.com/i.jpg",
                        "publishedAt": "2026-09-21T08:00:00Z",
                        "source": {"name": "Medio"},
                    },
                    {"title": "Incompleta", "description": None, "content": None},
                ],
            },
        )
    )

    provider = NewsApiProvider("clave", base_url=NEWSAPI_URL, country="es")
    items = provider.fetch(limit=5, language="es", categories=["magic"])

    assert len(items) == 1  # la entrada incompleta se descarta
    assert items[0].source_name == "Medio"
    assert items[0].category == "magic"
    assert items[0].published_at.isoformat() == "2026-09-21T08:00:00+00:00"
    # La clave viaja en la cabecera, nunca en la URL.
    request = route.calls[0].request
    assert request.headers["x-api-key"] == "clave"
    assert "clave" not in str(request.url)


@respx.mock
def test_newsapi_provider_raises_integration_error_on_http_error() -> None:
    respx.get(f"{NEWSAPI_URL}/top-headlines").mock(return_value=httpx.Response(401))

    provider = NewsApiProvider("clave", base_url=NEWSAPI_URL)
    with pytest.raises(IntegrationError) as exc_info:
        provider.fetch(limit=1, language="es")

    assert "clave" not in str(exc_info.value)  # el mensaje no filtra la clave


# --- Redactores ----------------------------------------------------------


def test_offline_generator_builds_extractive_summary() -> None:
    article = OfflineTextGenerator().rewrite(_item())
    assert article.model is None
    assert article.title == "Titular original"
    assert article.summary == "Entradilla original."


def test_offline_generator_truncates_long_summaries() -> None:
    long_item = RawNewsItem(
        title="T",
        summary="palabra " * 100,
        content="c",
        category="magic",
    )
    summary = OfflineTextGenerator().rewrite(long_item).summary
    assert len(summary) <= 220


@respx.mock
def test_gemini_generator_parses_json_response() -> None:
    generated = {
        "title": "Titular reescrito",
        "content": "Cuerpo reescrito por la IA.",
        "summary": "Resumen de la IA.",
    }
    route = respx.post(f"{GEMINI_URL}/models/gemini-2.5-flash:generateContent").mock(
        return_value=httpx.Response(
            200,
            json={"candidates": [{"content": {"parts": [{"text": json.dumps(generated)}]}}]},
        )
    )

    generator = GeminiTextGenerator(
        "clave", base_url=GEMINI_URL, model="gemini-2.5-flash"
    )
    article = generator.rewrite(_item())

    assert article.title == "Titular reescrito"
    assert article.summary == "Resumen de la IA."
    assert article.model == "gemini-2.5-flash"

    request = route.calls[0].request
    assert request.headers["x-goog-api-key"] == "clave"
    assert "clave" not in str(request.url)
    body = json.loads(request.content)
    assert body["generationConfig"]["responseMimeType"] == "application/json"
    assert "Cuerpo original" in body["contents"][0]["parts"][0]["text"]


@respx.mock
def test_gemini_generator_rejects_malformed_response() -> None:
    respx.post(f"{GEMINI_URL}/models/gemini-2.5-flash:generateContent").mock(
        return_value=httpx.Response(200, json={"candidates": []})
    )

    generator = GeminiTextGenerator("clave", base_url=GEMINI_URL, model="gemini-2.5-flash")
    with pytest.raises(IntegrationError):
        generator.rewrite(_item())


# --- Generacion de video -------------------------------------------------


@respx.mock
def test_replicate_submit_returns_pending_job() -> None:
    route = respx.post(f"{REPLICATE_URL}/predictions").mock(
        return_value=httpx.Response(201, json={"id": "abc123", "status": "starting"})
    )

    generator = ReplicateVideoGenerator(
        "token", base_url=REPLICATE_URL, model_version="version-1"
    )
    job = generator.submit(image_url="https://cdn.example.com/i.jpg", prompt="titular")

    assert job.job_id == "abc123"
    assert job.status is VideoStatus.PENDING
    assert job.video_url is None

    body = json.loads(route.calls[0].request.content)
    assert body["version"] == "version-1"
    assert body["input"]["input_image"] == "https://cdn.example.com/i.jpg"
    assert route.calls[0].request.headers["authorization"] == "Token token"


@respx.mock
def test_replicate_poll_returns_video_url_when_ready() -> None:
    respx.get(f"{REPLICATE_URL}/predictions/abc123").mock(
        return_value=httpx.Response(
            200,
            json={
                "id": "abc123",
                "status": "succeeded",
                "output": ["https://replicate.delivery/salida.mp4"],
            },
        )
    )

    generator = ReplicateVideoGenerator(
        "token", base_url=REPLICATE_URL, model_version="version-1"
    )
    job = generator.poll("abc123")

    assert job.status is VideoStatus.READY
    assert job.video_url == "https://replicate.delivery/salida.mp4"


@respx.mock
def test_replicate_succeeded_without_output_is_failed() -> None:
    respx.get(f"{REPLICATE_URL}/predictions/abc123").mock(
        return_value=httpx.Response(200, json={"id": "abc123", "status": "succeeded", "output": None})
    )

    generator = ReplicateVideoGenerator(
        "token", base_url=REPLICATE_URL, model_version="version-1"
    )
    assert generator.poll("abc123").status is VideoStatus.FAILED


@respx.mock
def test_replicate_failed_prediction_is_reported() -> None:
    respx.get(f"{REPLICATE_URL}/predictions/abc123").mock(
        return_value=httpx.Response(
            200, json={"id": "abc123", "status": "failed", "error": "modelo caido"}
        )
    )

    generator = ReplicateVideoGenerator(
        "token", base_url=REPLICATE_URL, model_version="version-1"
    )
    job = generator.poll("abc123")

    assert job.status is VideoStatus.FAILED
    assert job.error == "modelo caido"
