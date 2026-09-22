"""Motor, fabrica de sesiones y dependencia de FastAPI para la base de datos."""

from __future__ import annotations

import sqlite3
from typing import Any, Dict, Generator

from sqlalchemy import create_engine, event
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session, sessionmaker

from app.core.config import settings


@event.listens_for(Engine, "connect")
def _enable_sqlite_foreign_keys(dbapi_connection: Any, _: Any) -> None:
    """Activa las claves foraneas en SQLite.

    SQLite las ignora por defecto, de modo que sin este PRAGMA los
    ``ON DELETE CASCADE`` de los modelos no se aplicarian y quedarian noticias
    huerfanas. PostgreSQL ya las aplica siempre.
    """
    if isinstance(dbapi_connection, sqlite3.Connection):
        cursor = dbapi_connection.cursor()
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()


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
