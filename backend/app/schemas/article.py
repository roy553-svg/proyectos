"""Schemas Pydantic de las noticias."""

from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, ConfigDict, Field

from app.schemas.types import UtcDateTime


class ArticleRead(BaseModel):
    """Noticia tal y como la consume el cliente movil."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    title: str
    content: str
    category: str
    language: str = Field(description="Codigo ISO-639-1 del idioma del articulo.")
    image_url: Optional[str] = None
    video_url: Optional[str] = Field(
        default=None,
        description="Animacion generada. Nulo mientras la Fase 2 no exista.",
    )
    source_url: Optional[str] = None
    published_at: UtcDateTime
    position: int
