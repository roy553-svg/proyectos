"""Schemas Pydantic de las ediciones semanales."""

from __future__ import annotations

from datetime import date
from typing import Optional

from pydantic import BaseModel, ConfigDict

from app.schemas.types import UtcDateTime


class EditionRead(BaseModel):
    """Cabecera de la edicion semanal publicada."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    week_start: date
    week_end: date
    title: str
    published_at: Optional[UtcDateTime] = None
