"""Logica de negocio del feed semanal de noticias."""

from __future__ import annotations

from typing import List, Optional, Sequence

from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.exceptions import NoPublishedEditionError, UserPreferencesNotFoundError
from app.models.user_preferences import UserPreferences
from app.repositories import (
    news_article_repository,
    user_preferences_repository,
    weekly_edition_repository,
)
from app.schemas.article import ArticleRead
from app.schemas.edition import EditionRead
from app.schemas.news import NewsFeedResponse


def _normalize_categories(raw: Optional[Sequence[str]]) -> List[str]:
    """Limpia y deduplica las categorias preferidas conservando el orden."""
    if not raw:
        return []
    seen: set[str] = set()
    result: List[str] = []
    for value in raw:
        if not isinstance(value, str):
            continue
        category = value.strip().lower()
        if category and category not in seen:
            seen.add(category)
            result.append(category)
    return result


def _effective_limit(preferences: UserPreferences) -> int:
    """Limite de articulos: preferencia del usuario acotada por la config."""
    requested = preferences.max_articles or settings.max_articles_per_response
    return max(1, min(requested, settings.max_articles_per_response))


def get_news_feed(db: Session, user_id: int) -> NewsFeedResponse:
    """Devuelve la edicion publicada mas reciente filtrada por preferencias.

    Reglas aplicadas:
      * solo se considera la edicion con estado ``published`` mas reciente;
      * si el usuario tiene categorias preferidas, se filtra por ellas
        (una lista vacia significa "todas las categorias");
      * se filtra por el idioma preferido;
      * ``include_animated_only`` limita el resultado a noticias con video;
      * ``max_articles`` limita el numero de noticias devueltas.

    Raises:
        UserPreferencesNotFoundError: el usuario no existe.
        NoPublishedEditionError: no hay ninguna edicion publicada.
    """
    preferences = user_preferences_repository.get_by_user_id(db, user_id)
    if preferences is None:
        raise UserPreferencesNotFoundError(
            f"No existen preferencias para el usuario {user_id}."
        )

    edition = weekly_edition_repository.get_latest_published(db)
    if edition is None:
        raise NoPublishedEditionError()

    articles = news_article_repository.list_by_edition(
        db,
        edition_id=edition.id,
        categories=_normalize_categories(preferences.preferred_categories),
        language=preferences.language,
        only_with_video=preferences.include_animated_only,
        limit=_effective_limit(preferences),
    )

    return NewsFeedResponse(
        edition=EditionRead.model_validate(edition),
        articles=[ArticleRead.model_validate(article) for article in articles],
    )
