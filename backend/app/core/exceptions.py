"""Excepciones de dominio.

Los servicios no conocen FastAPI: lanzan estas excepciones y la capa de API
las traduce a respuestas HTTP (ver ``app.api.errors``).
"""

from __future__ import annotations

from http import HTTPStatus


class AppError(Exception):
    """Error de dominio con una representacion HTTP asociada."""

    status_code: int = HTTPStatus.INTERNAL_SERVER_ERROR
    detail: str = "Error interno del servidor."

    def __init__(self, detail: str | None = None) -> None:
        self.detail = detail or self.detail
        super().__init__(self.detail)


class UserPreferencesNotFoundError(AppError):
    """El ``user_id`` recibido no tiene preferencias registradas."""

    status_code = HTTPStatus.NOT_FOUND
    detail = "No existen preferencias para el usuario indicado."


class NoPublishedEditionError(AppError):
    """Todavia no hay ninguna edicion semanal publicada."""

    status_code = HTTPStatus.NOT_FOUND
    detail = "No hay ninguna edicion semanal publicada."
