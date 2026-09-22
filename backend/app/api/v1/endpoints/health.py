"""Endpoint de salud del servicio."""

from __future__ import annotations

from fastapi import APIRouter, status

from app.core.config import settings
from app.schemas.common import HealthResponse

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse, status_code=status.HTTP_200_OK)
def health() -> HealthResponse:
    """Comprueba que la aplicacion responde."""
    return HealthResponse(
        status="ok", app=settings.app_name, version=settings.app_version
    )
