"""Motor, fabrica de sesiones y dependencia de FastAPI para la base de datos."""

from __future__ import annotations

from typing import Any, Dict, Generator

from sqlalchemy import create_engine
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session, sessionmaker

from app.core.config import settings


def _engine_kwargs() -> Dict[str, Any]:
    """Opciones del engine dependientes del motor configurado.

    SQLite necesita ``check_same_thread=False`` con FastAPI; PostgreSQL se
    beneficia de ``pool_pre_ping``. Los modelos no cambian en ningun caso.
    """
    if settings.is_sqlite:
        return {"connect_args": {"check_same_thread": False}}
    return {"pool_pre_ping": True, "pool_size": 5, "max_overflow": 10}


engine: Engine = create_engine(
    settings.database_url,
    echo=settings.sql_echo,
    future=True,
    **_engine_kwargs(),
)

SessionLocal = sessionmaker(
    bind=engine,
    autocommit=False,
    autoflush=False,
    expire_on_commit=False,
    class_=Session,
)


def get_db() -> Generator[Session, None, None]:
    """Dependencia de FastAPI que abre y cierra una sesion por peticion."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
