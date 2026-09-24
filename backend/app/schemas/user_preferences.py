"""Schemas Pydantic de las preferencias de usuario."""

from __future__ import annotations

from typing import Any, Dict, List

from pydantic import BaseModel, ConfigDict, Field

from app.schemas.types import UtcDateTime


class UserPreferencesRead(BaseModel):
    """Preferencias de un usuario (solo lectura en la Fase 1)."""

    model_config = ConfigDict(from_attributes=True)

    user_id: int
    preferred_categories: List[str] = Field(default_factory=list)
    language: str
    include_animated_only: bool
    max_articles: int
    extra_preferences: Dict[str, Any] = Field(default_factory=dict)
    updated_at: UtcDateTime
