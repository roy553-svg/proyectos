"""Proveedor de noticias offline (respaldo y desarrollo).

Lee un fichero JSON incluido en el repositorio: no hace ninguna llamada de red
y por eso es el proveedor que se usa cuando no hay ``NEWSAPI_API_KEY`` y en los
tests.
"""

from __future__ import annotations

import json
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import List, Optional, Sequence

from app.integrations.schemas import RawNewsItem

DATA_FILE = Path(__file__).with_name("data") / "sample_news.json"


class LocalNewsProvider:
    """Devuelve noticias de ejemplo empaquetadas con la aplicacion."""

    name = "local"

    def __init__(self, data_file: Path | None = None) -> None:
        self._data_file = data_file or DATA_FILE

    def fetch(
        self,
        *,
        limit: int,
        language: str = "es",
        categories: Optional[Sequence[str]] = None,
    ) -> Sequence[RawNewsItem]:
        """Devuelve hasta ``limit`` noticias del fichero de ejemplos."""
        raw = json.loads(self._data_file.read_text(encoding="utf-8"))
        wanted = {c.lower() for c in categories} if categories else None
        now = datetime.now(timezone.utc)

        items: List[RawNewsItem] = []
        for index, entry in enumerate(raw):
            if wanted and entry["category"].lower() not in wanted:
                continue
            items.append(
                RawNewsItem(
                    title=entry["title"],
                    summary=entry["summary"],
                    content=entry["content"],
                    category=entry["category"],
                    language=language,
                    image_url=entry.get("image_url"),
                    source_url=entry.get("source_url"),
                    source_name=entry.get("source_name"),
                    published_at=now - timedelta(hours=index),
                )
            )
            if len(items) >= limit:
                break
        return items
