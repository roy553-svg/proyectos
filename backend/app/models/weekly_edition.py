"""Modelo ``WeeklyEdition``: una edicion semanal del periodico."""

from __future__ import annotations

from datetime import date, datetime
from typing import TYPE_CHECKING, List, Optional

from sqlalchemy import (
    CheckConstraint,
    Enum as SAEnum,
    Index,
    String,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base_class import Base
from app.models.enums import PublicationStatus
from app.models.mixins import TimestampMixin

if TYPE_CHECKING:  # pragma: no cover - solo para type checkers
    from app.models.news_article import NewsArticle


class WeeklyEdition(TimestampMixin, Base):
    """Edicion semanal que agrupa las noticias que consume la app movil."""

    __tablename__ = "weekly_editions"
    __table_args__ = (
        CheckConstraint("week_end >= week_start", name="week_end_after_start"),
        # Indice compuesto para la consulta principal: la ultima edicion
        # publicada se busca por estado y se ordena por semana.
        Index("ix_weekly_editions_status_week_start", "status", "week_start"),
        # Puede haber varios borradores para la misma semana, pero solo una
        # edicion PUBLICADA. Indice unico parcial soportado por SQLite y
        # PostgreSQL.
        Index(
            "uq_weekly_editions_published_week",
            "week_start",
            "week_end",
            unique=True,
            sqlite_where=text("status = 'published'"),
            postgresql_where=text("status = 'published'"),
        ),
    )

    id: Mapped[int] = mapped_column(primary_key=True)

    week_start: Mapped[date] = mapped_column(
        nullable=False, index=True, doc="Primer dia (inclusive) de la semana."
    )
    week_end: Mapped[date] = mapped_column(
        nullable=False, doc="Ultimo dia (inclusive) de la semana."
    )
    title: Mapped[str] = mapped_column(String(200), nullable=False)

    status: Mapped[PublicationStatus] = mapped_column(
        SAEnum(
            PublicationStatus,
            name="publication_status",
            native_enum=False,
            length=20,
            values_callable=lambda enum_cls: [member.value for member in enum_cls],
        ),
        default=PublicationStatus.DRAFT,
        nullable=False,
        doc="Solo las ediciones 'published' se exponen en la API publica.",
    )

    published_at: Mapped[Optional[datetime]] = mapped_column(
        default=None,
        nullable=True,
        doc="Momento en que la edicion paso a estado 'published'.",
    )

    articles: Mapped[List["NewsArticle"]] = relationship(
        back_populates="edition",
        cascade="all, delete-orphan",
        passive_deletes=True,
        order_by="NewsArticle.position",
        doc=(
            "Carga perezosa por defecto: el servicio de noticias consulta los "
            "articulos con sus filtros en una sola query para evitar N+1."
        ),
    )

    def __repr__(self) -> str:  # pragma: no cover - ayuda en depuracion
        return (
            f"<WeeklyEdition id={self.id} {self.week_start}..{self.week_end} "
            f"status={self.status.value if self.status else None!r}>"
        )
