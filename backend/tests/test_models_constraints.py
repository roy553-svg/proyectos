"""Restricciones de base de datos relevantes para la Fase 1."""

from __future__ import annotations

from datetime import date, datetime, timezone

import pytest
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models import NewsArticle, PublicationStatus, WeeklyEdition


def _edition(**overrides) -> WeeklyEdition:
    data = {
        "week_start": date(2026, 9, 21),
        "week_end": date(2026, 9, 27),
        "title": "Edicion",
        "status": PublicationStatus.PUBLISHED,
        "published_at": datetime(2026, 9, 21, 8, tzinfo=timezone.utc),
    }
    data.update(overrides)
    return WeeklyEdition(**data)


def test_only_one_published_edition_per_week(db_session: Session) -> None:
    db_session.add(_edition())
    db_session.commit()

    db_session.add(_edition(title="Duplicada"))
    with pytest.raises(IntegrityError):
        db_session.commit()
    db_session.rollback()


def test_draft_editions_may_share_the_same_week(db_session: Session) -> None:
    db_session.add(_edition())
    db_session.add(_edition(title="Borrador A", status=PublicationStatus.DRAFT, published_at=None))
    db_session.add(_edition(title="Borrador B", status=PublicationStatus.DRAFT, published_at=None))
    db_session.commit()

    assert db_session.query(WeeklyEdition).count() == 3


def test_article_position_is_unique_per_edition(db_session: Session) -> None:
    edition = _edition()
    db_session.add(edition)
    db_session.commit()

    common = {
        "edition_id": edition.id,
        "content": "Contenido",
        "category": "magic",
        "published_at": datetime(2026, 9, 21, 8, tzinfo=timezone.utc),
        "position": 1,
    }
    db_session.add(NewsArticle(title="A", **common))
    db_session.commit()

    db_session.add(NewsArticle(title="B", **common))
    with pytest.raises(IntegrityError):
        db_session.commit()
    db_session.rollback()


def test_deleting_edition_cascades_to_articles(db_session: Session) -> None:
    edition = _edition()
    edition.articles = [
        NewsArticle(
            title="A",
            content="Contenido",
            category="magic",
            published_at=datetime(2026, 9, 21, 8, tzinfo=timezone.utc),
            position=1,
        )
    ]
    db_session.add(edition)
    db_session.commit()

    db_session.delete(edition)
    db_session.commit()

    assert db_session.query(NewsArticle).count() == 0
