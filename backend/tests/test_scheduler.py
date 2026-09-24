"""Tests de la automatizacion con APScheduler (Fase 2)."""

from __future__ import annotations

from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.interval import IntervalTrigger

from app.core.config import Settings
from app.scheduler import (
    VIDEO_REFRESH_JOB_ID,
    WEEKLY_PIPELINE_JOB_ID,
    build_scheduler,
    get_scheduler,
    shutdown_scheduler,
    start_scheduler,
)


def _config(**kwargs) -> Settings:
    return Settings(database_url="sqlite://", **kwargs)


def test_scheduler_registers_both_jobs() -> None:
    scheduler = build_scheduler(
        _config(
            weekly_pipeline_day_of_week="mon",
            weekly_pipeline_hour=6,
            weekly_pipeline_minute=30,
            video_poll_interval_minutes=10,
        )
    )

    weekly = scheduler.get_job(WEEKLY_PIPELINE_JOB_ID)
    videos = scheduler.get_job(VIDEO_REFRESH_JOB_ID)

    assert isinstance(weekly.trigger, CronTrigger)
    assert isinstance(videos.trigger, IntervalTrigger)
    assert "day_of_week='mon'" in str(weekly.trigger)
    assert "hour='6'" in str(weekly.trigger)
    assert "minute='30'" in str(weekly.trigger)
    assert videos.trigger.interval.total_seconds() == 600
    # Nunca se solapan dos ejecuciones del mismo trabajo.
    assert weekly.max_instances == 1
    assert weekly.coalesce is True


def test_scheduler_does_not_start_when_disabled() -> None:
    assert start_scheduler(_config(enable_scheduler=False)) is None
    assert get_scheduler() is None


def test_scheduler_starts_and_stops_when_enabled() -> None:
    scheduler = start_scheduler(_config(enable_scheduler=True))
    try:
        assert scheduler is not None
        assert scheduler.running
        # Arrancarlo de nuevo no crea un segundo scheduler.
        assert start_scheduler(_config(enable_scheduler=True)) is scheduler
    finally:
        shutdown_scheduler()

    assert get_scheduler() is None
