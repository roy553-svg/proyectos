"""Dependencias compartidas por los endpoints."""

from __future__ import annotations

import hmac
from http import HTTPStatus
from typing import Annotated, Optional

from fastapi import Depends, Header, HTTPException
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.db.session import get_db

#: Sesion de base de datos inyectada por peticion.
DbSession = Annotated[Session, Depends(get_db)]

#: Configuracion de la aplicacion.
AppSettings = Annotated[Settings, Depends(get_settings)]


def require_admin_token(
    settings: AppSettings,
    x_admin_token: Annotated[Optional[str], Header(alias="X-Admin-Token")] = None,
) -> None:
    """Protege los endpoints de administracion con un token del entorno.

    Si ``ADMIN_API_TOKEN`` no esta configurado los endpoints se consideran
    deshabilitados (503): preferimos no exponerlos antes que exponerlos
    abiertos. La comparacion es en tiempo constante.
    """
    expected = settings.admin_api_token
    if not expected:
        raise HTTPException(
            status_code=HTTPStatus.SERVICE_UNAVAILABLE,
            detail="Los endpoints de administracion estan deshabilitados.",
        )
    if not x_admin_token or not hmac.compare_digest(x_admin_token, expected):
        raise HTTPException(
            status_code=HTTPStatus.UNAUTHORIZED,
            detail="Token de administracion invalido.",
            headers={"WWW-Authenticate": "X-Admin-Token"},
        )


#: Dependencia lista para usar en los routers de administracion.
AdminGuard = Depends(require_admin_token)

__all__ = ["AdminGuard", "AppSettings", "DbSession", "get_db", "require_admin_token"]
