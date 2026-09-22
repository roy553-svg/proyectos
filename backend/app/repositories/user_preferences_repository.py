"""Acceso a datos de ``UserPreferences``."""

from __future__ import annotations

from typing import Optional

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.user_preferences import UserPreferences


def get_by_user_id(db: Session, user_id: int) -> Optional[UserPreferences]:
    """Devuelve las preferencias del usuario o ``None`` si no existen."""
    stmt = select(UserPreferences).where(UserPreferences.user_id == user_id)
    return db.execute(stmt).scalar_one_or_none()
