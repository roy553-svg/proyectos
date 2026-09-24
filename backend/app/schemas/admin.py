"""Schemas de los endpoints de administracion (Fase 2)."""

from __future__ import annotations

from datetime import date
from typing import List, Optional

from pydantic import BaseModel, ConfigDict, Field

from app.models.enums import PipelineStatus
from app.schemas.types import UtcDateTime


class PipelineRunRead(BaseModel):
    """Resultado de una ejecucion del pipeline semanal."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    status: PipelineStatus
    trigger: str
    edition_id: Optional[int] = None
    items_fetched: int
    articles_created: int
    articles_ai_written: int
    videos_requested: int
    started_at: UtcDateTime
    finished_at: Optional[UtcDateTime] = None
    detail: Optional[str] = None


class PipelineRunRequest(BaseModel):
    """Parametros opcionales para lanzar el pipeline a mano."""

    week_start: Optional[date] = Field(
        default=None,
        description="Lunes de la semana a generar. Por defecto, la semana actual.",
    )
    publish: bool = Field(
        default=True,
        description="Si es false la edicion se deja en borrador.",
    )
    force: bool = Field(
        default=False,
        description="Regenera la semana aunque ya tenga una edicion publicada.",
    )


class VideoRefreshRead(BaseModel):
    """Resumen de una pasada de actualizacion de animaciones."""

    checked: int
    ready: int
    failed: int
    still_pending: int
    errors: List[str] = Field(default_factory=list)


class IntegrationsStatusRead(BaseModel):
    """Adaptadores activos y estado del scheduler."""

    news_provider: str
    text_generator: str
    video_generator: str
    video_generation_enabled: bool
    scheduler_enabled: bool
    scheduler_running: bool
    next_weekly_run: Optional[UtcDateTime] = None
