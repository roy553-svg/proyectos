"""Mixins reutilizables para los modelos ORM."""

from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy.orm import Mapped, mapped_column


def utcnow() -> datetime:
    """Instante actual en UTC con zona horaria explicita."""
    return datetime.now(timezone.utc)


class TimestampMixin:
    """Anade ``created_at`` gestionado en Python (portable entre motores)."""

    created_at: Mapped[datetime] = mapped_column(
        default=utcnow,
        nullable=False,
        doc="Fecha de creacion del registro (UTC).",
    )
