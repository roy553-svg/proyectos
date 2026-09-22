"""Punto de entrada de la aplicacion FastAPI.

Aqui solo se ensambla la aplicacion: configuracion, CORS, manejadores de
errores y routers. La logica vive en ``app.services`` y ``app.repositories``.
"""

from __future__ import annotations

from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.errors import register_exception_handlers
from app.api.v1.router import api_router
from app.core.config import settings
from app.scheduler import shutdown_scheduler, start_scheduler


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    """Prepara la base de datos y la automatizacion semanal.

    En produccion se recomienda ``CREATE_TABLES_ON_STARTUP=false`` y aplicar
    las migraciones con ``alembic upgrade head``. El scheduler (Fase 2) solo
    arranca si ``ENABLE_SCHEDULER=true``.
    """
    if settings.create_tables_on_startup:
        from app.db.init_db import create_tables

        create_tables()

    start_scheduler(settings)
    try:
        yield
    finally:
        shutdown_scheduler()


def create_app() -> FastAPI:
    """Construye y configura la instancia de FastAPI (application factory)."""
    app = FastAPI(
        title=settings.app_name,
        version=settings.app_version,
        description=(
            "Backend de 'El Profeta': genera cada semana una edicion con "
            "noticias redactadas por IA y animaciones, y la entrega filtrada "
            "por las preferencias del usuario."
        ),
        docs_url="/docs",
        redoc_url="/redoc",
        openapi_url="/openapi.json",
        lifespan=lifespan,
    )

    # CORS explicito: lista blanca de origenes, nunca "*" en produccion.
    cors_origins = settings.cors_origins
    if cors_origins:
        app.add_middleware(
            CORSMiddleware,
            allow_origins=cors_origins,
            allow_credentials=False,
            allow_methods=["GET", "POST"],
            allow_headers=["*"],
        )

    register_exception_handlers(app)
    app.include_router(api_router, prefix=settings.api_v1_prefix)
    return app


app = create_app()
