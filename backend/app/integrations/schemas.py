"""Estructuras de datos que intercambian las integraciones externas.

Son ``dataclasses`` simples (no modelos ORM ni schemas de API) para que los
adaptadores externos no dependan ni de SQLAlchemy ni de FastAPI.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional

from app.models.enums import VideoStatus


@dataclass(slots=True)
class RawNewsItem:
    """Noticia en bruto tal y como llega de la fuente externa."""

    title: str
    summary: str
    content: str
    category: str
    language: str = "es"
    image_url: Optional[str] = None
    source_url: Optional[str] = None
    source_name: Optional[str] = None
    published_at: Optional[datetime] = None


@dataclass(slots=True)
class GeneratedArticle:
    """Noticia ya redactada al estilo de 'El Profeta'."""

    title: str
    content: str
    summary: str
    #: Modelo que la redacto; ``None`` si se uso el redactor offline.
    model: Optional[str] = None


@dataclass(slots=True)
class VideoJob:
    """Estado de un trabajo de generacion de video."""

    job_id: str
    status: VideoStatus
    video_url: Optional[str] = None
    error: Optional[str] = None
    metadata: dict = field(default_factory=dict)
