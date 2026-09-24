"""Adaptador de NewsAPI (https://newsapi.org).

La clave se lee de la configuracion y viaja en la cabecera ``X-Api-Key``;
nunca se registra en los logs ni se incluye en las excepciones.
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Sequence

import httpx

from app.integrations.errors import IntegrationError
from app.integrations.schemas import RawNewsItem

logger = logging.getLogger(__name__)

#: Categorias de NewsAPI equivalentes a las nuestras.
CATEGORY_MAP: Dict[str, str] = {
    "technology": "technology",
    "science": "science",
    "sports": "sports",
    "culture": "entertainment",
    "politics": "general",
    "economy": "business",
    "magic": "general",
}


def _parse_datetime(value: Optional[str]) -> Optional[datetime]:
    """Convierte la fecha ISO-8601 de NewsAPI en un ``datetime`` UTC."""
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
    return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)


class NewsApiProvider:
    """Descarga titulares de NewsAPI y los normaliza a ``RawNewsItem``."""

    name = "newsapi"

    def __init__(
        self,
        api_key: str,
        *,
        base_url: str,
        country: str = "es",
        timeout: float = 20.0,
        client: httpx.Client | None = None,
    ) -> None:
        self._api_key = api_key
        self._base_url = base_url.rstrip("/")
        self._country = country
        self._timeout = timeout
        self._client = client

    def fetch(
        self,
        *,
        limit: int,
        language: str = "es",
        categories: Optional[Sequence[str]] = None,
    ) -> Sequence[RawNewsItem]:
        """Devuelve hasta ``limit`` titulares, repartidos entre las categorias."""
        wanted = list(categories) if categories else ["general"]
        per_category = max(1, limit // len(wanted))

        items: List[RawNewsItem] = []
        for category in wanted:
            if len(items) >= limit:
                break
            payload = self._get(
                "/top-headlines",
                params={
                    "country": self._country,
                    "language": language,
                    "category": CATEGORY_MAP.get(category.lower(), "general"),
                    "pageSize": min(per_category, limit - len(items)),
                },
            )
            items.extend(self._to_items(payload, category=category, language=language))

        return items[:limit]

    # -- Internos ---------------------------------------------------------

    def _get(self, path: str, *, params: Dict[str, Any]) -> Dict[str, Any]:
        """Realiza una peticion GET autenticada y devuelve el JSON."""
        client = self._client or httpx.Client(timeout=self._timeout)
        try:
            response = client.get(
                f"{self._base_url}{path}",
                params=params,
                headers={"X-Api-Key": self._api_key},
            )
            response.raise_for_status()
            return response.json()
        except httpx.HTTPError as exc:
            # No se incluye la excepcion original completa para no arrastrar
            # la cabecera con la clave a los logs.
            raise IntegrationError(f"NewsAPI no respondio correctamente: {type(exc).__name__}") from None
        finally:
            if self._client is None:
                client.close()

    @staticmethod
    def _to_items(
        payload: Dict[str, Any], *, category: str, language: str
    ) -> List[RawNewsItem]:
        """Normaliza la respuesta de NewsAPI descartando entradas incompletas."""
        items: List[RawNewsItem] = []
        for article in payload.get("articles", []):
            title = (article.get("title") or "").strip()
            body = (article.get("content") or article.get("description") or "").strip()
            if not title or not body:
                continue
            items.append(
                RawNewsItem(
                    title=title,
                    summary=(article.get("description") or "").strip() or title,
                    content=body,
                    category=category,
                    language=language,
                    image_url=article.get("urlToImage"),
                    source_url=article.get("url"),
                    source_name=(article.get("source") or {}).get("name"),
                    published_at=_parse_datetime(article.get("publishedAt")),
                )
            )
        return items
