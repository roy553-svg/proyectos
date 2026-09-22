"""Datos de demostracion para la Fase 1.

Crea (de forma idempotente) preferencias de usuario, una edicion semanal
publicada, una edicion en borrador y varias noticias de distintas categorias.
No depende de ninguna API externa.

Uso:
    python -m scripts.seed
    python -m scripts.seed --reset   # borra los datos anteriores
"""

from __future__ import annotations

import argparse
from datetime import date, datetime, time, timedelta, timezone
from typing import List

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.init_db import create_tables
from app.db.session import SessionLocal
from app.models import (
    ArticleCategory,
    NewsArticle,
    PublicationStatus,
    UserPreferences,
    WeeklyEdition,
)


def _monday_of_current_week(today: date | None = None) -> date:
    """Devuelve el lunes de la semana de ``today``."""
    reference = today or date.today()
    return reference - timedelta(days=reference.weekday())


def _at(day: date, hour: int) -> datetime:
    """Combina fecha y hora en un datetime UTC."""
    return datetime.combine(day, time(hour=hour), tzinfo=timezone.utc)


def reset_data(db: Session) -> None:
    """Elimina los datos de demostracion existentes."""
    db.query(NewsArticle).delete()
    db.query(WeeklyEdition).delete()
    db.query(UserPreferences).delete()
    db.commit()


def seed_user_preferences(db: Session) -> List[UserPreferences]:
    """Crea las preferencias de demostracion si no existen."""
    wanted = [
        UserPreferences(
            user_id=1,
            preferred_categories=[
                ArticleCategory.TECHNOLOGY.value,
                ArticleCategory.SCIENCE.value,
                ArticleCategory.MAGIC.value,
            ],
            language="es",
            include_animated_only=False,
            max_articles=20,
            extra_preferences={"theme": "prophet-dark"},
        ),
        UserPreferences(
            user_id=2,
            preferred_categories=[ArticleCategory.SPORTS.value],
            language="es",
            include_animated_only=False,
            max_articles=5,
            extra_preferences={},
        ),
        UserPreferences(
            user_id=3,
            preferred_categories=[],  # sin filtro: recibe todas las categorias
            language="es",
            include_animated_only=True,  # solo noticias ya animadas
            max_articles=10,
            extra_preferences={},
        ),
    ]

    created: List[UserPreferences] = []
    for preferences in wanted:
        existing = db.execute(
            select(UserPreferences).where(UserPreferences.user_id == preferences.user_id)
        ).scalar_one_or_none()
        if existing is None:
            db.add(preferences)
            created.append(preferences)
    db.commit()
    return created


def seed_editions(db: Session) -> WeeklyEdition:
    """Crea una edicion publicada (con noticias) y otra en borrador."""
    week_start = _monday_of_current_week()
    week_end = week_start + timedelta(days=6)

    published = db.execute(
        select(WeeklyEdition).where(
            WeeklyEdition.week_start == week_start,
            WeeklyEdition.status == PublicationStatus.PUBLISHED,
        )
    ).scalar_one_or_none()

    if published is not None:
        return published

    published = WeeklyEdition(
        week_start=week_start,
        week_end=week_end,
        title="El Profeta - Edicion Semanal",
        status=PublicationStatus.PUBLISHED,
        published_at=_at(week_start, 8),
    )
    published.articles = _demo_articles(week_start)
    db.add(published)

    next_start = week_start + timedelta(days=7)
    draft = WeeklyEdition(
        week_start=next_start,
        week_end=next_start + timedelta(days=6),
        title="El Profeta - Proxima Edicion (borrador)",
        status=PublicationStatus.DRAFT,
    )
    draft.articles = [
        NewsArticle(
            title="Borrador: los duendes de Gringotts revisan sus tipos de interes",
            content=(
                "Esta noticia pertenece a una edicion en borrador y no debe "
                "aparecer nunca en la respuesta publica del endpoint."
            ),
            category=ArticleCategory.ECONOMY.value,
            language="es",
            image_url="https://cdn.example.com/profeta/borrador.jpg",
            video_url=None,
            source_url=None,
            published_at=_at(next_start, 8),
            position=1,
        )
    ]
    db.add(draft)

    db.commit()
    db.refresh(published)
    return published


def _demo_articles(week_start: date) -> List[NewsArticle]:
    """Noticias de demostracion con categorias e idiomas variados."""
    return [
        NewsArticle(
            title="Las varitas inteligentes llegan al Callejon Diagon",
            content=(
                "Un taller de Ollivander presenta un prototipo de varita capaz "
                "de registrar los hechizos lanzados y sugerir correcciones de "
                "pronunciacion a los magos aprendices."
            ),
            category=ArticleCategory.TECHNOLOGY.value,
            language="es",
            image_url="https://cdn.example.com/profeta/varitas.jpg",
            # Animacion ya generada: sirve para probar include_animated_only.
            video_url="https://cdn.example.com/profeta/varitas.mp4",
            source_url="https://example.com/noticias/varitas",
            published_at=_at(week_start, 8),
            position=1,
        ),
        NewsArticle(
            title="Descubren una nueva especie de bowtruckle en el bosque de Dean",
            content=(
                "El equipo de magizoologia describe un ejemplar capaz de "
                "camuflarse entre ramas heladas, lo que abre nuevas preguntas "
                "sobre su adaptacion al invierno."
            ),
            category=ArticleCategory.SCIENCE.value,
            language="es",
            image_url="https://cdn.example.com/profeta/bowtruckle.jpg",
            video_url=None,  # pendiente de la Fase 2
            source_url=None,
            published_at=_at(week_start, 9),
            position=2,
        ),
        NewsArticle(
            title="Las Arpias de Holyhead ganan la liga de quidditch",
            content=(
                "Una remontada en los ultimos diez minutos dio a las Arpias el "
                "titulo tras capturar la snitch dorada en pleno temporal."
            ),
            category=ArticleCategory.SPORTS.value,
            language="es",
            image_url="https://cdn.example.com/profeta/quidditch.jpg",
            video_url="https://cdn.example.com/profeta/quidditch.mp4",
            source_url="https://example.com/noticias/quidditch",
            published_at=_at(week_start, 10),
            position=3,
        ),
        NewsArticle(
            title="El Ministerio revisa la ley de uso de translador",
            content=(
                "La nueva propuesta endurece los permisos para trasladores "
                "internacionales y crea un registro publico de rutas activas."
            ),
            category=ArticleCategory.POLITICS.value,
            language="es",
            image_url="https://cdn.example.com/profeta/ministerio.jpg",
            video_url=None,
            source_url=None,
            published_at=_at(week_start, 11),
            position=4,
        ),
        NewsArticle(
            title="Exposicion de retratos animados en el Museo de Hogsmeade",
            content=(
                "La muestra reune sesenta retratos magicos restaurados y una "
                "sala dedicada a los cuadros que cambian de marco por la noche."
            ),
            category=ArticleCategory.CULTURE.value,
            language="es",
            image_url="https://cdn.example.com/profeta/retratos.jpg",
            video_url=None,
            source_url=None,
            published_at=_at(week_start, 12),
            position=5,
        ),
        NewsArticle(
            title="Nuevo encantamiento para conservar pociones sin frio",
            content=(
                "El gremio de pocionistas valida un encantamiento que mantiene "
                "estables las pociones curativas durante seis meses."
            ),
            category=ArticleCategory.MAGIC.value,
            language="es",
            image_url="https://cdn.example.com/profeta/pociones.jpg",
            video_url=None,
            source_url=None,
            published_at=_at(week_start, 13),
            position=6,
        ),
        NewsArticle(
            title="Smart wands arrive at Diagon Alley",
            content=(
                "An English edition article used to verify that the language "
                "preference actually filters the feed."
            ),
            category=ArticleCategory.TECHNOLOGY.value,
            language="en",
            image_url="https://cdn.example.com/profeta/wands-en.jpg",
            video_url=None,
            source_url=None,
            published_at=_at(week_start, 14),
            position=7,
        ),
    ]


def main() -> None:
    """Ejecuta el seed completo."""
    parser = argparse.ArgumentParser(description="Carga datos de demostracion.")
    parser.add_argument(
        "--reset",
        action="store_true",
        help="Elimina los datos existentes antes de insertar los de demostracion.",
    )
    args = parser.parse_args()

    create_tables()

    with SessionLocal() as db:
        if args.reset:
            reset_data(db)
        seed_user_preferences(db)
        edition = seed_editions(db)
        total = db.query(NewsArticle).count()

    print("Seed completado.")
    print(f"  Edicion publicada: id={edition.id} ({edition.week_start} -> {edition.week_end})")
    print(f"  Noticias en base de datos: {total}")
    print("  Usuarios de prueba: 1 (tech/science/magic), 2 (sports), 3 (solo animadas)")
    print("  Prueba: curl 'http://127.0.0.1:8000/api/v1/news?user_id=1'")


if __name__ == "__main__":
    main()
