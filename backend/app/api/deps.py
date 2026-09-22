"""Dependencias compartidas por los endpoints."""

from __future__ import annotations

from typing import Annotated

from fastapi import Depends
from sqlalchemy.orm import Session

from app.db.session import get_db

#: Sesion de base de datos inyectada por peticion.
DbSession = Annotated[Session, Depends(get_db)]

__all__ = ["DbSession", "get_db"]
