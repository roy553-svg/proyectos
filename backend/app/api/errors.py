"""Traduccion de errores de dominio a respuestas HTTP."""

from __future__ import annotations

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.core.exceptions import AppError


async def app_error_handler(_: Request, exc: AppError) -> JSONResponse:
    """Convierte una ``AppError`` en una respuesta JSON homogenea."""
    return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})


def register_exception_handlers(app: FastAPI) -> None:
    """Registra los manejadores de excepciones de la aplicacion."""
    app.add_exception_handler(AppError, app_error_handler)  # type: ignore[arg-type]
