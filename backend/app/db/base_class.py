"""Clase base declarativa de SQLAlchemy 2.x."""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import DateTime, MetaData
from sqlalchemy.orm import DeclarativeBase

# Convencion de nombres explicita: imprescindible para que Alembic genere
# migraciones deterministas y para poder renombrar constraints en PostgreSQL.
NAMING_CONVENTION = {
    "ix": "ix_%(column_0_label)s",
    "uq": "uq_%(table_name)s_%(column_0_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}


class Base(DeclarativeBase):
    """Base comun de todos los modelos ORM."""

    metadata = MetaData(naming_convention=NAMING_CONVENTION)

    # Usamos siempre DateTime con zona horaria para que el comportamiento sea
    # el mismo en SQLite (que la ignora) y en PostgreSQL (timestamptz).
    type_annotation_map = {
        datetime: DateTime(timezone=True),
    }
