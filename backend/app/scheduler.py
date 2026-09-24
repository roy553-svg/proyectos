"""Automatizacion semanal con APScheduler (Fase 2).

Dos trabajos:
  * ``weekly_pipeline``: genera y publica la edicion de la semana.
  * ``video_refresh``: consulta las animaciones pendientes cada pocos minutos.

El scheduler solo arranca si ``ENABLE_SCHEDULER=true``; en desarrollo y en los
tests permanece apagado y el pipeline se lanza a mano desde /api/v1/admin.
"""

from __future__ import annotations

import logging
from typing import Optional

from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.interval import IntervalTrigger

from app.core.config import Settings, settings as default_settings
from app.db.session import SessionLocal
from app.services import pipeline_service, video_service
from app.services.pipeline_service import PipelineOptions

logger = logging.getLogger(__name__)

WEEKLY_PIPELINE_JOB_ID = "weekly_pipeline"
VIDEO_REFRESH_JOB_ID = "video_refresh"

_scheduler: Optional[BackgroundScheduler] = None


def weekly_pipeline_job() -> None:
    """Ejecuta el pipeline semanal con su propia sesion de base de datos."""
    with SessionLocal() as db:
        run = pipeline_service.run_weekly_pipeline(
            db, options=PipelineOptions(trigger="scheduler")
        )
        logger.info("Pipeline programado terminado con estado %s", run.status.value)


def video_refresh_job() -> None:
    """Actualiza el estado de las animaciones pendientes."""
    with SessionLocal() as db:
        video_service.refresh_pending_videos(db)


def build_scheduler(config: Optional[Settings] = None) -> BackgroundScheduler:
    """Crea el scheduler con sus dos trabajos (sin arrancarlo)."""
    config = config or default_settings
    scheduler = BackgroundScheduler(timezone=config.scheduler_timezone)

    scheduler.add_job(
        weekly_pipeline_job,
        trigger=CronTrigger(
            day_of_week=config.weekly_pipeline_day_of_week,
            hour=config.weekly_pipeline_hour,
            minute=config.weekly_pipeline_minute,
            timezone=config.scheduler_timezone,
        ),
        id=WEEKLY_PIPELINE_JOB_ID,
        name="Generar y publicar la edicion semanal",
        replace_existing=True,
        # Si el proceso estuvo caido no se acumulan ejecuciones atrasadas.
        coalesce=True,
        max_instances=1,
        misfire_grace_time=3600,
    )

    scheduler.add_job(
        video_refresh_job,
        trigger=IntervalTrigger(
            minutes=config.video_poll_interval_minutes,
            timezone=config.scheduler_timezone,
        ),
        id=VIDEO_REFRESH_JOB_ID,
        name="Actualizar animaciones pendientes",
        replace_existing=True,
        coalesce=True,
        max_instances=1,
    )

    return scheduler


def start_scheduler(config: Optional[Settings] = None) -> Optional[BackgroundScheduler]:
    """Arranca el scheduler si esta habilitado. Idempotente."""
    global _scheduler
    config = config or default_settings

    if not config.enable_scheduler:
        logger.info("Scheduler desactivado (ENABLE_SCHEDULER=false).")
        return None
    if _scheduler is not None and _scheduler.running:
        return _scheduler

    _scheduler = build_scheduler(config)
    _scheduler.start()
    logger.info(
        "Scheduler arrancado: pipeline %s %02d:%02d (%s), videos cada %s min",
        config.weekly_pipeline_day_of_week,
        config.weekly_pipeline_hour,
        config.weekly_pipeline_minute,
        config.scheduler_timezone,
        config.video_poll_interval_minutes,
    )
    return _scheduler


def shutdown_scheduler() -> None:
    """Detiene el scheduler si estaba arrancado."""
    global _scheduler
    if _scheduler is not None and _scheduler.running:
        _scheduler.shutdown(wait=False)
        logger.info("Scheduler detenido.")
    _scheduler = None


def get_scheduler() -> Optional[BackgroundScheduler]:
    """Devuelve el scheduler activo (o ``None`` si no se arranco)."""
    return _scheduler
