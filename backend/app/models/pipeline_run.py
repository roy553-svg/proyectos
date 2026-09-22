"""Modelo ``PipelineRun``: registro de cada ejecucion del pipeline semanal."""

from __future__ import annotations

from datetime import datetime
from typing import Optional

from sqlalchemy import Enum as SAEnum, ForeignKey, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base_class import Base
from app.models.enums import PipelineStatus
from app.models.mixins import TimestampMixin


class PipelineRun(Base, TimestampMixin):
    """Traza de una ejecucion del pipeline automatico (Fase 2).

    Permite auditar que hizo el scheduler cada semana sin depender de los logs
    del proceso: cuantas noticias se descargaron, cuantas se redactaron con IA,
    cuantas animaciones se pidieron y por que fallo una ejecucion.
    """

    __tablename__ = "pipeline_runs"

    id: Mapped[int] = mapped_column(primary_key=True)

    status: Mapped[PipelineStatus] = mapped_column(
        SAEnum(
            PipelineStatus,
            name="pipeline_status",
            native_enum=False,
            length=20,
            values_callable=lambda enum_cls: [member.value for member in enum_cls],
        ),
        default=PipelineStatus.RUNNING,
        nullable=False,
        index=True,
    )
    trigger: Mapped[str] = mapped_column(
        String(20),
        default="manual",
        nullable=False,
        doc="Quien lanzo la ejecucion: 'scheduler' o 'manual'.",
    )

    edition_id: Mapped[Optional[int]] = mapped_column(
        ForeignKey("weekly_editions.id", ondelete="SET NULL"),
        default=None,
        nullable=True,
        index=True,
    )

    items_fetched: Mapped[int] = mapped_column(default=0, nullable=False)
    articles_created: Mapped[int] = mapped_column(default=0, nullable=False)
    articles_ai_written: Mapped[int] = mapped_column(default=0, nullable=False)
    videos_requested: Mapped[int] = mapped_column(default=0, nullable=False)

    started_at: Mapped[datetime] = mapped_column(nullable=False)
    finished_at: Mapped[Optional[datetime]] = mapped_column(default=None, nullable=True)

    detail: Mapped[Optional[str]] = mapped_column(
        Text,
        default=None,
        nullable=True,
        doc="Mensaje de resultado o error (sin datos sensibles).",
    )

    def __repr__(self) -> str:  # pragma: no cover - ayuda en depuracion
        return f"<PipelineRun id={self.id} status={self.status.value if self.status else None!r}>"
