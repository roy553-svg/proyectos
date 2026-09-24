"""Acceso a datos de ``WeeklyEdition``."""

from __future__ import annotations

from typing import Optional

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.enums import PublicationStatus
from app.models.weekly_edition import WeeklyEdition


def get_latest_published(db: Session) -> Optional[WeeklyEdition]:
    """Devuelve la edicion publicada mas reciente, o ``None`` si no hay ninguna.

    La consulta se apoya en el indice ``ix_weekly_editions_status_week_start``
    y no carga los articulos (el servicio los pide filtrados por separado).
    """
    stmt = (
        select(WeeklyEdition)
        .where(WeeklyEdition.status == PublicationStatus.PUBLISHED)
        .order_by(WeeklyEdition.week_start.desc(), WeeklyEdition.id.desc())
        .limit(1)
    )
    return db.execute(stmt).scalar_one_or_none()
