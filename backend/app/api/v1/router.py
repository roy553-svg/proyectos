"""Router agregador de la version v1 de la API."""

from __future__ import annotations

from fastapi import APIRouter

from app.api.v1.endpoints import health, news

api_router = APIRouter()
api_router.include_router(health.router)
api_router.include_router(news.router)
