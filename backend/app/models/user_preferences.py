"""Modelo ``UserPreferences``: preferencias de lectura de un usuario."""

from __future__ import annotations

from datetime import datetime
from typing import Any, Dict, List

from sqlalchemy import CheckConstraint, JSON, String
from sqlalchemy.orm import Mapped, mapped_column

from app.models.mixins import TimestampMixin, utcnow

from app.db.base_class import Base


class UserPreferences(TimestampMixin, Base):
    """Preferencias con las que se filtra la edicion semanal.

    En la Fase 1 no hay autenticacion: ``user_id`` es el identificador que
    envia el cliente movil. Los campos JSON (``preferred_categories`` y
    ``extra_preferences``) permiten extender las preferencias en fases
    posteriores sin migrar el esquema.

    Nota: los campos JSON no rastrean mutaciones in-place; para actualizarlos
    hay que reasignar la lista/diccionario completo.
    """

    __tablename__ = "user_preferences"
    __table_args__ = (
        CheckConstraint("max_articles >= 1", name="max_articles_positive"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)

    user_id: Mapped[int] = mapped_column(
        unique=True,
        index=True,
        nullable=False,
        doc="Identificador del usuario proporcionado por el cliente.",
    )

    preferred_categories: Mapped[List[str]] = mapped_column(
        JSON,
        default=list,
        nullable=False,
        doc="Categorias preferidas. Lista vacia = sin filtro por categoria.",
    )
    language: Mapped[str] = mapped_column(
        String(10), default="es", nullable=False, doc="Idioma preferido (ISO-639-1)."
    )

    # --- Preferencias de contenido ---------------------------------------
    include_animated_only: Mapped[bool] = mapped_column(
        default=False,
        nullable=False,
        doc="Si es True solo se devuelven noticias con animacion (video_url).",
    )
    max_articles: Mapped[int] = mapped_column(
        default=20, nullable=False, doc="Numero maximo de noticias por edicion."
    )
    extra_preferences: Mapped[Dict[str, Any]] = mapped_column(
        JSON,
        default=dict,
        nullable=False,
        doc="Ajustes adicionales sin esquema fijo (punto de extension).",
    )

    updated_at: Mapped[datetime] = mapped_column(
        default=utcnow,
        onupdate=utcnow,
        nullable=False,
        doc="Ultima actualizacion de las preferencias (UTC).",
    )

    def __repr__(self) -> str:  # pragma: no cover - ayuda en depuracion
        return f"<UserPreferences user_id={self.user_id} language={self.language!r}>"
