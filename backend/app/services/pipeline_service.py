"""Pipeline semanal automatico (Fase 2).

Orquesta el ciclo completo: descargar noticias, reescribirlas con IA, crear la
edicion semanal, pedir las animaciones y publicar. Cada ejecucion queda
registrada en ``PipelineRun``.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from typing import List, Optional, Sequence

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import Settings, settings as default_settings
from app.integrations.errors import IntegrationError
from app.integrations.factory import Providers, build_providers
from app.integrations.offline_text_generator import OfflineTextGenerator
from app.integrations.schemas import GeneratedArticle, RawNewsItem
from app.models.enums import PipelineStatus, PublicationStatus, VideoStatus
from app.models.news_article import NewsArticle
from app.models.pipeline_run import PipelineRun
from app.models.weekly_edition import WeeklyEdition
from app.repositories import weekly_edition_repository

logger = logging.getLogger(__name__)

EDITION_TITLE = "El Profeta - Edicion Semanal"


@dataclass(slots=True)
class PipelineOptions:
    """Parametros de una ejecucion del pipeline."""

    week_start: Optional[date] = None
    trigger: str = "manual"
    #: Si es False la edicion se crea en borrador y se publica manualmente.
    publish: bool = True
    #: Regenera la semana aunque ya tenga una edicion publicada (la archiva).
    force: bool = False


def monday_of(reference: Optional[date] = None) -> date:
    """Lunes de la semana de ``reference`` (hoy por defecto)."""
    day = reference or datetime.now(timezone.utc).date()
    return day - timedelta(days=day.weekday())


def _now() -> datetime:
    return datetime.now(timezone.utc)


def run_weekly_pipeline(
    db: Session,
    *,
    providers: Optional[Providers] = None,
    options: Optional[PipelineOptions] = None,
    config: Optional[Settings] = None,
) -> PipelineRun:
    """Ejecuta el pipeline semanal completo y devuelve su registro.

    Nunca propaga errores de los servicios externos: si algo falla la ejecucion
    se marca como ``failed`` y el detalle queda en ``PipelineRun.detail``, de
    modo que el scheduler no muera por un fallo puntual de red.
    """
    config = config or default_settings
    providers = providers or build_providers(config)
    options = options or PipelineOptions()

    week_start = options.week_start or monday_of()
    week_end = week_start + timedelta(days=6)

    run = PipelineRun(
        status=PipelineStatus.RUNNING,
        trigger=options.trigger,
        started_at=_now(),
    )
    db.add(run)
    db.commit()
    db.refresh(run)

    logger.info(
        "Pipeline semanal iniciado (semana %s, %s)", week_start, providers.describe()
    )

    try:
        existing = _existing_edition(db, week_start)
        if existing is not None and existing.status is PublicationStatus.PUBLISHED:
            if not options.force:
                return _finish(
                    db,
                    run,
                    status=PipelineStatus.SKIPPED,
                    edition_id=existing.id,
                    detail=f"La semana {week_start} ya tiene una edicion publicada.",
                )
            # Con force se archiva la anterior: asi no choca con el indice
            # unico parcial de ediciones publicadas por semana.
            existing.status = PublicationStatus.ARCHIVED
            db.commit()
        elif existing is not None:
            # Habia un borrador de la misma semana: se regenera desde cero.
            db.delete(existing)
            db.commit()

        items = list(
            providers.news.fetch(
                limit=config.news_items_per_edition,
                language=config.news_language,
            )
        )
        run.items_fetched = len(items)
        if not items:
            return _finish(
                db,
                run,
                status=PipelineStatus.FAILED,
                detail="La fuente de noticias no devolvio resultados.",
            )

        articles, ai_written, text_errors = _write_articles(items, providers, config)

        edition = WeeklyEdition(
            week_start=week_start,
            week_end=week_end,
            title=f"{EDITION_TITLE} ({week_start:%d/%m} - {week_end:%d/%m})",
            status=PublicationStatus.DRAFT,
        )
        edition.articles = articles
        db.add(edition)
        db.commit()
        db.refresh(edition)

        videos_requested, video_errors = _request_videos(db, edition, providers, config)

        if options.publish:
            edition.status = PublicationStatus.PUBLISHED
            edition.published_at = _now()
            db.commit()

        errors = text_errors + video_errors
        status = PipelineStatus.SUCCESS if not errors else PipelineStatus.PARTIAL
        detail = "; ".join(errors[:5]) if errors else None

        run.articles_created = len(articles)
        run.articles_ai_written = ai_written
        run.videos_requested = videos_requested
        return _finish(db, run, status=status, edition_id=edition.id, detail=detail)

    except Exception as exc:  # pragma: no cover - red de seguridad del scheduler
        db.rollback()
        logger.exception("El pipeline semanal fallo")
        return _finish(
            db,
            run,
            status=PipelineStatus.FAILED,
            detail=f"{type(exc).__name__}: {exc}",
        )


# --- Pasos internos -------------------------------------------------------


def _existing_edition(db: Session, week_start: date) -> Optional[WeeklyEdition]:
    """Edicion (publicada o borrador) que ya cubre esa semana, si existe."""
    stmt = (
        select(WeeklyEdition)
        .where(WeeklyEdition.week_start == week_start)
        .order_by(WeeklyEdition.status, WeeklyEdition.id.desc())
    )
    return db.execute(stmt).scalars().first()


def _write_articles(
    items: Sequence[RawNewsItem],
    providers: Providers,
    config: Settings,
) -> tuple[List[NewsArticle], int, List[str]]:
    """Reescribe cada noticia y la convierte en ``NewsArticle``.

    Si la IA falla en una noticia concreta se usa el redactor offline para esa
    noticia: una edicion incompleta es peor que una edicion sin IA.
    """
    fallback = OfflineTextGenerator()
    articles: List[NewsArticle] = []
    errors: List[str] = []
    ai_written = 0

    for position, item in enumerate(items, start=1):
        try:
            generated: GeneratedArticle = providers.text.rewrite(item)
            if generated.model:
                ai_written += 1
        except IntegrationError as exc:
            errors.append(f"IA no disponible para '{item.title[:40]}': {exc}")
            generated = fallback.rewrite(item)

        articles.append(
            NewsArticle(
                title=generated.title,
                content=generated.content,
                summary=generated.summary,
                category=item.category,
                language=item.language or config.news_language,
                image_url=item.image_url,
                video_url=None,
                source_url=item.source_url,
                source_name=item.source_name,
                ai_model=generated.model,
                published_at=item.published_at or _now(),
                position=position,
            )
        )

    return articles, ai_written, errors


def _request_videos(
    db: Session,
    edition: WeeklyEdition,
    providers: Providers,
    config: Settings,
) -> tuple[int, List[str]]:
    """Encola las animaciones de las primeras noticias con imagen."""
    if not providers.video.enabled or config.videos_per_edition <= 0:
        return 0, []

    candidates = [
        article
        for article in sorted(edition.articles, key=lambda a: a.position)
        if article.image_url
    ][: config.videos_per_edition]

    requested = 0
    errors: List[str] = []
    for article in candidates:
        try:
            job = providers.video.submit(
                image_url=article.image_url or "",
                prompt=article.summary or article.title,
            )
        except IntegrationError as exc:
            article.video_status = VideoStatus.FAILED
            errors.append(f"Video no encolado para '{article.title[:40]}': {exc}")
            continue

        article.video_job_id = job.job_id
        article.video_status = job.status
        article.video_url = job.video_url
        requested += 1

    db.commit()
    return requested, errors


def _finish(
    db: Session,
    run: PipelineRun,
    *,
    status: PipelineStatus,
    edition_id: Optional[int] = None,
    detail: Optional[str] = None,
) -> PipelineRun:
    """Cierra el registro de la ejecucion."""
    run.status = status
    run.edition_id = edition_id
    run.detail = detail
    run.finished_at = _now()
    db.commit()
    db.refresh(run)
    logger.info("Pipeline semanal finalizado: %s (%s)", status.value, detail or "sin incidencias")
    return run
