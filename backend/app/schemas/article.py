"""Schemas Pydantic de las noticias."""

from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, ConfigDict, Field

from app.models.enums import VideoStatus
from app.schemas.types import UtcDateTime


class ArticleRead(BaseModel):
    """Noticia tal y como la consume el cliente movil."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    title: str
    content: str
    summary: Optional[str] = Field(
        default=None, description="Resumen corto para la portada (Fase 2)."
    )
    category: str
    language: str = Field(description="Codigo ISO-639-1 del idioma del articulo.")
    image_url: Optional[str] = None
    video_url: Optional[str] = Field(
        default=None,
        description="Animacion generada; nulo mientras el video no este listo.",
    )
    source_url: Optional[str] = None
    source_name: Optional[str] = None
    video_status: VideoStatus = Field(
        default=VideoStatus.NOT_REQUESTED,
        description="Estado de la animacion: permite al cliente mostrar un aviso.",
    )
    published_at: UtcDateTime
    position: int
