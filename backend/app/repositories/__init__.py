"""Acceso a datos: consultas SQLAlchemy reutilizables por los servicios."""

from app.repositories import (
    news_article_repository,
    user_preferences_repository,
    weekly_edition_repository,
)

__all__ = [
    "news_article_repository",
    "user_preferences_repository",
    "weekly_edition_repository",
]
