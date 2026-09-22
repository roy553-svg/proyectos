"""Fixtures compartidas de los tests.

Cada test se ejecuta contra una base de datos SQLite en memoria aislada, con
la dependencia ``get_db`` sobreescrita para no tocar la base de datos real.
"""

from __future__ import annotations

import os

# Debe ejecutarse ANTES de importar la configuracion: los tests nunca deben
# tocar la base de datos real ni crear ficheros .db.
os.environ["DATABASE_URL"] = "sqlite://"
os.environ["CREATE_TABLES_ON_STARTUP"] = "false"
os.environ["ENABLE_SCHEDULER"] = "false"
# Los tests que necesitan los endpoints de administracion inyectan su propia
# configuracion; por defecto quedan deshabilitados aunque el .env local tenga
# un token.
os.environ["ADMIN_API_TOKEN"] = ""
# Ninguna integracion externa activa: los tests nunca deben salir a la red.
for _external_key in ("NEWSAPI_API_KEY", "GEMINI_API_KEY", "REPLICATE_API_TOKEN"):
    os.environ[_external_key] = ""

from datetime import date, datetime, time, timedelta, timezone  # noqa: E402
from typing import Iterator  # noqa: E402

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy import create_engine  # noqa: E402
from sqlalchemy.orm import Session, sessionmaker  # noqa: E402
from sqlalchemy.pool import StaticPool  # noqa: E402

from app.core.config import Settings, get_settings  # noqa: E402
from app.db.base import Base  # noqa: E402
from app.db.session import get_db  # noqa: E402
from app.main import create_app  # noqa: E402
from app.models import (  # noqa: E402
    ArticleCategory,
    NewsArticle,
    PublicationStatus,
    UserPreferences,
    WeeklyEdition,
)

WEEK_START = date(2026, 9, 21)
WEEK_END = date(2026, 9, 27)


def _at(day: date, hour: int) -> datetime:
    return datetime.combine(day, time(hour=hour), tzinfo=timezone.utc)


@pytest.fixture(name="db_session")
def db_session_fixture() -> Iterator[Session]:
    """Sesion contra una base de datos SQLite en memoria recien creada."""
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(bind=engine)
    testing_session = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)
    session = testing_session()
    try:
        yield session
    finally:
        session.close()
        Base.metadata.drop_all(bind=engine)
        engine.dispose()


@pytest.fixture(name="client")
def client_fixture(db_session: Session) -> Iterator[TestClient]:
    """Cliente HTTP con la dependencia de base de datos sobreescrita."""
    app = create_app()

    def override_get_db() -> Iterator[Session]:
        yield db_session

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()


ADMIN_TOKEN = "token-de-pruebas"


@pytest.fixture(name="admin_settings")
def admin_settings_fixture() -> Settings:
    """Configuracion con el token de administracion habilitado."""
    return Settings(
        database_url="sqlite://",
        admin_api_token=ADMIN_TOKEN,
        create_tables_on_startup=False,
        news_items_per_edition=3,
        videos_per_edition=0,
    )


@pytest.fixture(name="admin_client")
def admin_client_fixture(
    db_session: Session, admin_settings: Settings
) -> Iterator[TestClient]:
    """Cliente con los endpoints /admin habilitados (token de prueba)."""
    app = create_app()

    def override_get_db() -> Iterator[Session]:
        yield db_session

    app.dependency_overrides[get_db] = override_get_db
    app.dependency_overrides[get_settings] = lambda: admin_settings
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()


@pytest.fixture(name="published_edition")
def published_edition_fixture(db_session: Session) -> WeeklyEdition:
    """Edicion publicada con noticias de varias categorias e idiomas."""
    edition = WeeklyEdition(
        week_start=WEEK_START,
        week_end=WEEK_END,
        title="El Profeta - Edicion Semanal",
        status=PublicationStatus.PUBLISHED,
        published_at=_at(WEEK_START, 8),
    )
    edition.articles = [
        NewsArticle(
            title="Varitas inteligentes",
            content="Contenido de tecnologia.",
            category=ArticleCategory.TECHNOLOGY.value,
            language="es",
            image_url="https://cdn.example.com/tech.jpg",
            video_url="https://cdn.example.com/tech.mp4",
            published_at=_at(WEEK_START, 8),
            position=1,
        ),
        NewsArticle(
            title="Nueva especie de bowtruckle",
            content="Contenido de ciencia sin animacion.",
            category=ArticleCategory.SCIENCE.value,
            language="es",
            image_url="https://cdn.example.com/science.jpg",
            video_url=None,
            published_at=_at(WEEK_START, 9),
            position=2,
        ),
        NewsArticle(
            title="Las Arpias ganan la liga",
            content="Contenido deportivo.",
            category=ArticleCategory.SPORTS.value,
            language="es",
            image_url="https://cdn.example.com/sports.jpg",
            video_url=None,
            published_at=_at(WEEK_START, 10),
            position=3,
        ),
        NewsArticle(
            title="Smart wands",
            content="Articulo en ingles.",
            category=ArticleCategory.TECHNOLOGY.value,
            language="en",
            image_url="https://cdn.example.com/tech-en.jpg",
            video_url=None,
            published_at=_at(WEEK_START, 11),
            position=4,
        ),
    ]
    db_session.add(edition)
    db_session.commit()
    db_session.refresh(edition)
    return edition


@pytest.fixture(name="draft_edition")
def draft_edition_fixture(db_session: Session) -> WeeklyEdition:
    """Edicion en borrador que nunca debe aparecer en la API."""
    start = WEEK_START + timedelta(days=7)
    edition = WeeklyEdition(
        week_start=start,
        week_end=start + timedelta(days=6),
        title="Borrador",
        status=PublicationStatus.DRAFT,
    )
    edition.articles = [
        NewsArticle(
            title="Noticia en borrador",
            content="No debe publicarse.",
            category=ArticleCategory.ECONOMY.value,
            language="es",
            published_at=_at(start, 8),
            position=1,
        )
    ]
    db_session.add(edition)
    db_session.commit()
    db_session.refresh(edition)
    return edition


@pytest.fixture(name="make_preferences")
def make_preferences_fixture(db_session: Session):
    """Fabrica de preferencias de usuario para los tests."""

    def _make(
        user_id: int,
        *,
        categories: list[str] | None = None,
        language: str = "es",
        include_animated_only: bool = False,
        max_articles: int = 20,
    ) -> UserPreferences:
        preferences = UserPreferences(
            user_id=user_id,
            preferred_categories=categories if categories is not None else [],
            language=language,
            include_animated_only=include_animated_only,
            max_articles=max_articles,
            extra_preferences={},
        )
        db_session.add(preferences)
        db_session.commit()
        db_session.refresh(preferences)
        return preferences

    return _make
