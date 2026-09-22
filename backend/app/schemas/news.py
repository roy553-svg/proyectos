"""Schema de respuesta del endpoint GET /api/v1/news."""

from __future__ import annotations

from typing import List

from pydantic import BaseModel, Field

from app.schemas.article import ArticleRead
from app.schemas.edition import EditionRead


class NewsFeedResponse(BaseModel):
    """Edicion semanal publicada junto a las noticias filtradas del usuario."""

    edition: EditionRead
    articles: List[ArticleRead] = Field(default_factory=list)
