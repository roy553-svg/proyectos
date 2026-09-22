"""Tests del seguimiento de animaciones (Fase 2)."""

from __future__ import annotations

from datetime import date

import pytest
from sqlalchemy.orm import Session

from app.core.config import Settings
from app.integrations.factory import Providers
from app.models import NewsArticle, VideoStatus
from app.services import pipeline_service, video_service
from app.services.pipeline_service import PipelineOptions
from tests.fakes import FakeNewsProvider, FakeTextGenerator, FakeVideoGenerator

WEEK = date(2026, 9, 21)


@pytest.fixture(name="config")
def config_fixture() -> Settings:
    return Settings(
        database_url="sqlite://", news_items_per_edition=4, videos_per_edition=2
    )


@pytest.fixture(name="video")
def video_fixture() -> FakeVideoGenerator:
    return FakeVideoGenerator()


@pytest.fixture(name="edition_with_videos")
def edition_with_videos_fixture(
    db_session: Session, config: Settings, video: FakeVideoGenerator
) -> FakeVideoGenerator:
    """Genera una edicion con dos animaciones encoladas."""
    pipeline_service.run_weekly_pipeline(
        db_session,
        providers=Providers(
            news=FakeNewsProvider(), text=FakeTextGenerator(), video=video
        ),
        options=PipelineOptions(week_start=WEEK),
        config=config,
    )
    return video


def test_pending_articles_are_listed(
    db_session: Session, edition_with_videos: FakeVideoGenerator
) -> None:
    pending = video_service.list_pending_articles(db_session)
    assert [article.video_job_id for article in pending] == ["job-1", "job-2"]


def test_ready_video_updates_article_url(
    db_session: Session, edition_with_videos: FakeVideoGenerator, config: Settings
) -> None:
    edition_with_videos.complete("job-1", "https://cdn.example.com/listo.mp4")

    result = video_service.refresh_pending_videos(
        db_session, generator=edition_with_videos, config=config
    )

    assert result.checked == 2
    assert result.ready == 1
    assert result.still_pending == 1

    article = db_session.query(NewsArticle).filter_by(video_job_id="job-1").one()
    assert article.video_status is VideoStatus.READY
    assert article.video_url == "https://cdn.example.com/listo.mp4"


def test_failed_video_is_marked_and_not_retried(
    db_session: Session, edition_with_videos: FakeVideoGenerator, config: Settings
) -> None:
    edition_with_videos.fail("job-2")

    result = video_service.refresh_pending_videos(
        db_session, generator=edition_with_videos, config=config
    )

    assert result.failed == 1
    article = db_session.query(NewsArticle).filter_by(video_job_id="job-2").one()
    assert article.video_status is VideoStatus.FAILED
    assert article.video_url is None
    # Un trabajo fallido ya no se vuelve a consultar.
    assert "job-2" not in [a.video_job_id for a in video_service.list_pending_articles(db_session)]


def test_provider_error_is_collected_without_losing_state(
    db_session: Session, edition_with_videos: FakeVideoGenerator, config: Settings
) -> None:
    """Un fallo de red deja la noticia como estaba y se reintenta despues."""
    edition_with_videos.jobs.pop("job-1")  # provoca IntegrationError en poll

    result = video_service.refresh_pending_videos(
        db_session, generator=edition_with_videos, config=config
    )

    assert result.errors and "job-1" in result.errors[0]
    article = db_session.query(NewsArticle).filter_by(video_job_id="job-1").one()
    assert article.video_status is VideoStatus.PENDING


def test_disabled_generator_does_nothing(
    db_session: Session, edition_with_videos: FakeVideoGenerator, config: Settings
) -> None:
    result = video_service.refresh_pending_videos(
        db_session, generator=FakeVideoGenerator(enabled=False), config=config
    )

    assert result.checked == 0
    assert result.ready == 0
