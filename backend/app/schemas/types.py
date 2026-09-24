"""Tipos reutilizables de los schemas."""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Annotated, Any

from pydantic import BeforeValidator


def _as_utc(value: Any) -> Any:
    """Interpreta como UTC los datetime sin zona horaria.

    SQLite no almacena la zona horaria, de modo que los valores vuelven
    "naive"; la API siempre debe responder en UTC ("...Z") para que el cliente
    movil no tenga que adivinar el huso.
    """
    if isinstance(value, datetime) and value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value


#: ``datetime`` normalizado a UTC en las respuestas de la API.
UtcDateTime = Annotated[datetime, BeforeValidator(_as_utc)]
