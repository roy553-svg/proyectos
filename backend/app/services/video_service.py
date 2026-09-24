"""Seguimiento de las animaciones pendientes (Fase 2).

Las animaciones se generan de forma asincrona: aqui se consulta el estado de
los trabajos ya encolados y se actualiza ``NewsArticle.video_url`` cuando el
fichero esta listo.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import List, Optional

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import Settings, settings as default_settings
from app.integrations.base import VideoGenerator
from app.integrations.errors import IntegrationError
from app.integrations.factory import build_video_generator
from app.models.enums import VideoStatus
from app.models.news_article import NewsArticle

logger = logging.getLogger(__name__)

PENDING_STATES = (VideoStatus.PENDING, VideoStatus.PROCESSING)


@dataclass(slots=True)
class VideoRefreshResult:
    """Resumen de una pasada de actualizacion de videos."""

    checked: int = 0
    ready: int = 0
    failed: int = 0
    still_pending: int = 0
    errors: List[str] = None  # type: ignore[assignment]

    def __post_init__(self) -> None:
        if self.errors is None:
            self.errors = []


def list_pending_articles(db: Session, limit: int = 50) -> List[NewsArticle]:
    """Noticias con una animacion encolada todavia sin resolver."""
    stmt = (
        select(NewsArticle)
        .where(
            NewsArticle.video_status.in_(PENDING_STATES),
            NewsArticle.video_job_id.is_not(None),
        )
        .order_by(NewsArticle.updated_at.asc())
        .limit(limit)
    )
    return list(db.execute(stmt).scalars().all())


def refresh_pending_videos(
    db: Session,
    *,
    generator: Optional[VideoGenerator] = None,
    limit: int = 50,
    config: Optional[Settings] = None,
) -> VideoRefreshResult:
    """Consulta el proveedor y actualiza las noticias cuyo video ya esta listo."""
    config = config or default_settings
    generator = generator or build_video_generator(config)
    result = VideoRefreshResult()

    if not generator.enabled:
        logger.debug("Generacion de video desactivada: no hay nada que consultar.")
        return result

    for article in list_pending_articles(db, limit=limit):
        result.checked += 1
        try:
            job = generator.poll(str(article.video_job_id))
        except IntegrationError as exc:
            result.errors.append(f"Articulo {article.id}: {exc}")
            continue

        article.video_status = job.status
        if job.status is VideoStatus.READY and job.video_url:
            article.video_url = job.video_url
            result.ready += 1
        elif job.status is VideoStatus.FAILED:
            result.failed += 1
        else:
            result.still_pending += 1

    db.commit()
    logger.info(
        "Videos revisados: %s (listos=%s fallidos=%s pendientes=%s)",
        result.checked,
        result.ready,
        result.failed,
        result.still_pending,
    )
    return result
