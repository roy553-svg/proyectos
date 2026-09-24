"""Tests del pipeline semanal automatico (Fase 2)."""

from __future__ import annotations

from datetime import date, timedelta

import pytest
from sqlalchemy.orm import Session

from app.core.config import Settings
from app.integrations.factory import Providers
from app.models import (
    NewsArticle,
    PipelineStatus,
    PublicationStatus,
    VideoStatus,
    WeeklyEdition,
)
from app.services import pipeline_service
from app.services.pipeline_service import PipelineOptions, monday_of
from tests.fakes import FakeNewsProvider, FakeTextGenerator, FakeVideoGenerator

WEEK = date(2026, 9, 21)  # lunes


@pytest.fixture(name="config")
def config_fixture() -> Settings:
    """Configuracion de test: 4 noticias por edicion y 2 videos."""
    return Settings(
        database_url="sqlite://",
        news_items_per_edition=4,
        videos_per_edition=2,
        news_language="es",
    )


def _providers(**kwargs) -> Providers:
    return Providers(
        news=kwargs.get("news") or FakeNewsProvider(),
        text=kwargs.get("text") or FakeTextGenerator(),
        video=kwargs.get("video") or FakeVideoGenerator(),
    )


def test_pipeline_creates_and_publishes_edition(db_session: Session, config: Settings) -> None:
    providers = _providers()

    run = pipeline_service.run_weekly_pipeline(
        db_session,
        providers=providers,
        options=PipelineOptions(week_start=WEEK),
        config=config,
    )

    assert run.status is PipelineStatus.SUCCESS
    assert run.items_fetched == 4
    assert run.articles_created == 4
    assert run.articles_ai_written == 4

    edition = db_session.get(WeeklyEdition, run.edition_id)
    assert edition is not None
    assert edition.status is PublicationStatus.PUBLISHED
    assert edition.published_at is not None
    assert edition.week_start == WEEK
    assert edition.week_end == WEEK + timedelta(days=6)
    assert [article.position for article in edition.articles] == [1, 2, 3, 4]
    assert all(article.title.startswith("[Profeta]") for article in edition.articles)
    assert all(article.ai_model == "fake-model" for article in edition.articles)


def test_pipeline_requests_videos_for_first_articles(
    db_session: Session, config: Settings
) -> None:
    video = FakeVideoGenerator()
    run = pipeline_service.run_weekly_pipeline(
        db_session,
        providers=_providers(video=video),
        options=PipelineOptions(week_start=WEEK),
        config=config,
    )

    assert run.videos_requested == config.videos_per_edition
    assert len(video.submitted) == config.videos_per_edition

    articles = (
        db_session.query(NewsArticle).order_by(NewsArticle.position).all()
    )
    assert articles[0].video_status is VideoStatus.PENDING
    assert articles[0].video_job_id == "job-1"
    # El video todavia no esta listo: video_url sigue siendo nulo.
    assert articles[0].video_url is None
    assert articles[-1].video_status is VideoStatus.NOT_REQUESTED


def test_pipeline_without_video_provider_publishes_without_animation(
    db_session: Session, config: Settings
) -> None:
    run = pipeline_service.run_weekly_pipeline(
        db_session,
        providers=_providers(video=FakeVideoGenerator(enabled=False)),
        options=PipelineOptions(week_start=WEEK),
        config=config,
    )

    assert run.status is PipelineStatus.SUCCESS
    assert run.videos_requested == 0
    assert all(article.video_url is None for article in db_session.query(NewsArticle))


def test_pipeline_falls_back_to_offline_writer_when_ai_fails(
    db_session: Session, config: Settings
) -> None:
    """Si la IA falla en una noticia se usa el redactor offline para esa noticia."""
    run = pipeline_service.run_weekly_pipeline(
        db_session,
        providers=_providers(text=FakeTextGenerator(fail_on=[1])),
        options=PipelineOptions(week_start=WEEK),
        config=config,
    )

    assert run.status is PipelineStatus.PARTIAL
    assert run.articles_created == 4
    assert run.articles_ai_written == 3
    assert "IA no disponible" in (run.detail or "")

    fallback = db_session.query(NewsArticle).filter_by(position=2).one()
    assert fallback.ai_model is None
    assert fallback.title == "Noticia original 1"


def test_pipeline_reports_partial_when_video_submit_fails(
    db_session: Session, config: Settings
) -> None:
    run = pipeline_service.run_weekly_pipeline(
        db_session,
        providers=_providers(video=FakeVideoGenerator(fail_submit=True)),
        options=PipelineOptions(week_start=WEEK),
        config=config,
    )

    assert run.status is PipelineStatus.PARTIAL
    assert run.videos_requested == 0
    assert db_session.query(NewsArticle).filter_by(position=1).one().video_status is (
        VideoStatus.FAILED
    )


def test_pipeline_skips_week_already_published(db_session: Session, config: Settings) -> None:
    pipeline_service.run_weekly_pipeline(
        db_session,
        providers=_providers(),
        options=PipelineOptions(week_start=WEEK),
        config=config,
    )

    second = pipeline_service.run_weekly_pipeline(
        db_session,
        providers=_providers(),
        options=PipelineOptions(week_start=WEEK),
        config=config,
    )

    assert second.status is PipelineStatus.SKIPPED
    assert db_session.query(WeeklyEdition).count() == 1


def test_pipeline_force_archives_previous_edition(db_session: Session, config: Settings) -> None:
    first = pipeline_service.run_weekly_pipeline(
        db_session,
        providers=_providers(),
        options=PipelineOptions(week_start=WEEK),
        config=config,
    )

    second = pipeline_service.run_weekly_pipeline(
        db_session,
        providers=_providers(),
        options=PipelineOptions(week_start=WEEK, force=True),
        config=config,
    )

    assert second.status is PipelineStatus.SUCCESS
    assert second.edition_id != first.edition_id
    previous = db_session.get(WeeklyEdition, first.edition_id)
    assert previous.status is PublicationStatus.ARCHIVED
    published = (
        db_session.query(WeeklyEdition)
        .filter_by(status=PublicationStatus.PUBLISHED)
        .all()
    )
    assert len(published) == 1


def test_pipeline_can_leave_edition_as_draft(db_session: Session, config: Settings) -> None:
    run = pipeline_service.run_weekly_pipeline(
        db_session,
        providers=_providers(),
        options=PipelineOptions(week_start=WEEK, publish=False),
        config=config,
    )

    edition = db_session.get(WeeklyEdition, run.edition_id)
    assert edition.status is PublicationStatus.DRAFT
    assert edition.published_at is None


def test_pipeline_regenerates_existing_draft(db_session: Session, config: Settings) -> None:
    """Un borrador de la misma semana se sustituye (y sus noticias no quedan huerfanas)."""
    pipeline_service.run_weekly_pipeline(
        db_session,
        providers=_providers(),
        options=PipelineOptions(week_start=WEEK, publish=False),
        config=config,
    )

    second = pipeline_service.run_weekly_pipeline(
        db_session,
        providers=_providers(),
        options=PipelineOptions(week_start=WEEK),
        config=config,
    )

    assert second.status is PipelineStatus.SUCCESS
    assert db_session.query(WeeklyEdition).count() == 1
    edition = db_session.get(WeeklyEdition, second.edition_id)
    assert edition.status is PublicationStatus.PUBLISHED
    # Las noticias del borrador se borraron en cascada: no hay duplicados.
    assert db_session.query(NewsArticle).count() == 4


def test_pipeline_fails_when_source_is_empty(db_session: Session, config: Settings) -> None:
    run = pipeline_service.run_weekly_pipeline(
        db_session,
        providers=_providers(news=FakeNewsProvider(items=[])),
        options=PipelineOptions(week_start=WEEK),
        config=config,
    )

    assert run.status is PipelineStatus.FAILED
    assert run.edition_id is None
    assert db_session.query(WeeklyEdition).count() == 0


def test_monday_of_returns_start_of_week() -> None:
    assert monday_of(date(2026, 9, 24)) == date(2026, 9, 21)
    assert monday_of(date(2026, 9, 21)) == date(2026, 9, 21)
    assert monday_of(date(2026, 9, 27)) == date(2026, 9, 21)
