"""Schemas transversales (errores, health-check)."""

from __future__ import annotations

from pydantic import BaseModel


class ErrorResponse(BaseModel):
    """Cuerpo de las respuestas de error de la API."""

    detail: str


class HealthResponse(BaseModel):
    """Respuesta del endpoint de salud."""

    status: str
    app: str
    version: str
