"""Puertos (interfaces) de las integraciones externas.

El pipeline depende solo de estos ``Protocol``; los adaptadores concretos
(NewsAPI, Gemini, Replicate) y los de respaldo offline son intercambiables, lo
que permite ejecutar y testear todo sin red ni claves reales.
"""

from __future__ import annotations

from typing import Optional, Protocol, Sequence, runtime_checkable

from app.integrations.schemas import GeneratedArticle, RawNewsItem, VideoJob


@runtime_checkable
class NewsProvider(Protocol):
    """Fuente de noticias en bruto."""

    name: str

    def fetch(
        self,
        *,
        limit: int,
        language: str,
        categories: Optional[Sequence[str]] = None,
    ) -> Sequence[RawNewsItem]:
        """Devuelve como maximo ``limit`` noticias recientes."""
        ...


@runtime_checkable
class TextGenerator(Protocol):
    """Redactor de noticias (IA o respaldo offline)."""

    name: str

    def rewrite(self, item: RawNewsItem) -> GeneratedArticle:
        """Reescribe/resume una noticia con el tono de 'El Profeta'."""
        ...


@runtime_checkable
class VideoGenerator(Protocol):
    """Generador de animaciones a partir de la imagen de la noticia."""

    name: str
    enabled: bool

    def submit(self, *, image_url: str, prompt: str) -> VideoJob:
        """Encola la generacion de un video y devuelve el trabajo creado."""
        ...

    def poll(self, job_id: str) -> VideoJob:
        """Consulta el estado de un trabajo ya encolado."""
        ...
