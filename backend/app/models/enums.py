"""Enumeraciones compartidas por los modelos y los schemas."""

from __future__ import annotations

from enum import Enum


class PublicationStatus(str, Enum):
    """Estado de publicacion de una edicion semanal."""

    DRAFT = "draft"
    PUBLISHED = "published"
    ARCHIVED = "archived"


class ArticleCategory(str, Enum):
    """Categorias conocidas.

    La columna ``NewsArticle.category`` se almacena como texto (no como enum de
    base de datos) para que la Fase 2 pueda incorporar categorias nuevas sin
    necesidad de una migracion. Esta enumeracion documenta y normaliza los
    valores que usamos hoy (seed, tests y cliente movil).
    """

    TECHNOLOGY = "technology"
    SCIENCE = "science"
    SPORTS = "sports"
    CULTURE = "culture"
    POLITICS = "politics"
    ECONOMY = "economy"
    MAGIC = "magic"


class VideoStatus(str, Enum):
    """Estado de la animacion generada para una noticia (Fase 2)."""

    NOT_REQUESTED = "not_requested"
    PENDING = "pending"
    PROCESSING = "processing"
    READY = "ready"
    FAILED = "failed"


class PipelineStatus(str, Enum):
    """Resultado de una ejecucion del pipeline semanal (Fase 2)."""

    RUNNING = "running"
    SUCCESS = "success"
    PARTIAL = "partial"
    FAILED = "failed"
    SKIPPED = "skipped"
