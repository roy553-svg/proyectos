"""Modelo ``NewsArticle``: una noticia dentro de una edicion semanal."""

from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING, Optional

from sqlalchemy import (
    CheckConstraint,
    ForeignKey,
    Index,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base_class import Base
from app.models.mixins import TimestampMixin

if TYPE_CHECKING:  # pragma: no cover - solo para type checkers
    from app.models.weekly_edition import WeeklyEdition


class NewsArticle(TimestampMixin, Base):
    """Noticia publicada dentro de una ``WeeklyEdition``.

    ``video_url`` es opcional a proposito: la generacion de animaciones
    pertenece a la Fase 2 y hasta entonces las noticias se muestran con su
    imagen estatica.
    """

    __tablename__ = "news_articles"
    __table_args__ = (
        # El orden de aparicion dentro de una edicion es unico.
        UniqueConstraint("edition_id", "position", name="uq_article_position_per_edition"),
        CheckConstraint("position >= 1", name="position_positive"),
        # Indice que cubre la consulta del endpoint: filtrar por edicion,
        # categoria e idioma y ordenar por posicion.
        Index("ix_news_articles_edition_category", "edition_id", "category"),
        Index("ix_news_articles_edition_position", "edition_id", "position"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)

    edition_id: Mapped[int] = mapped_column(
        ForeignKey("weekly_editions.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    title: Mapped[str] = mapped_column(String(300), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)

    category: Mapped[str] = mapped_column(
        String(60),
        nullable=False,
        index=True,
        doc="Valor libre; ver app.models.enums.ArticleCategory.",
    )
    language: Mapped[str] = mapped_column(
        String(10),
        default="es",
        nullable=False,
        index=True,
        doc="Codigo ISO-639-1 del idioma del articulo.",
    )

    image_url: Mapped[Optional[str]] = mapped_column(
        String(1000), default=None, nullable=True, doc="Imagen original de la noticia."
    )
    video_url: Mapped[Optional[str]] = mapped_column(
        String(1000),
        default=None,
        nullable=True,
        doc="Animacion generada. Nulo hasta la Fase 2.",
    )
    source_url: Mapped[Optional[str]] = mapped_column(
        String(1000), default=None, nullable=True, doc="Fuente original de la noticia."
    )

    published_at: Mapped[datetime] = mapped_column(nullable=False, index=True)
    position: Mapped[int] = mapped_column(
        default=1, nullable=False, doc="Orden de aparicion dentro de la edicion."
    )

    edition: Mapped["WeeklyEdition"] = relationship(back_populates="articles")

    @property
    def has_animation(self) -> bool:
        """True si la noticia ya tiene animacion generada (Fase 2)."""
        return bool(self.video_url)

    def __repr__(self) -> str:  # pragma: no cover - ayuda en depuracion
        return f"<NewsArticle id={self.id} edition_id={self.edition_id} title={self.title!r}>"
