"""Punto unico de importacion de metadatos.

Importar este modulo garantiza que todos los modelos estan registrados en
``Base.metadata`` (necesario para ``create_all`` y para el autogenerate de
Alembic).
"""

from __future__ import annotations

from app.db.base_class import Base
from app.models.news_article import NewsArticle
from app.models.pipeline_run import PipelineRun
from app.models.user_preferences import UserPreferences
from app.models.weekly_edition import WeeklyEdition

__all__ = [
    "Base",
    "NewsArticle",
    "PipelineRun",
    "UserPreferences",
    "WeeklyEdition",
]
