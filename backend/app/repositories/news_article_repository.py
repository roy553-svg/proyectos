"""Acceso a datos de ``NewsArticle``."""

from __future__ import annotations

from typing import Optional, Sequence

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.news_article import NewsArticle


def list_by_edition(
    db: Session,
    *,
    edition_id: int,
    categories: Optional[Sequence[str]] = None,
    language: Optional[str] = None,
    only_with_video: bool = False,
    limit: Optional[int] = None,
) -> Sequence[NewsArticle]:
    """Devuelve las noticias de una edicion aplicando los filtros indicados.

    Todos los filtros viajan a la base de datos en una unica consulta
    parametrizada (sin SQL construido con strings y sin N+1).
    """
    stmt = select(NewsArticle).where(NewsArticle.edition_id == edition_id)

    if categories:
        stmt = stmt.where(NewsArticle.category.in_(list(categories)))
    if language:
        stmt = stmt.where(NewsArticle.language == language)
    if only_with_video:
        stmt = stmt.where(NewsArticle.video_url.is_not(None))

    stmt = stmt.order_by(NewsArticle.position.asc(), NewsArticle.published_at.desc())

    if limit is not None:
        stmt = stmt.limit(limit)

    return db.execute(stmt).scalars().all()
