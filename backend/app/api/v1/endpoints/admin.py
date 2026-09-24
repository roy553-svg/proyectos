"""Endpoints de administracion del pipeline (Fase 2).

Todos exigen la cabecera ``X-Admin-Token``; si ``ADMIN_API_TOKEN`` no esta
configurado devuelven 503 en lugar de quedar abiertos.
"""

from __future__ import annotations

from typing import List

from fastapi import APIRouter, Query, status
from sqlalchemy import select

from app.api.deps import AdminGuard, AppSettings, DbSession
from app.integrations.factory import build_providers, build_video_generator
from app.models.pipeline_run import PipelineRun
from app.schemas.admin import (
    IntegrationsStatusRead,
    PipelineRunRead,
    PipelineRunRequest,
    VideoRefreshRead,
)
from app.schemas.common import ErrorResponse
from app.scheduler import WEEKLY_PIPELINE_JOB_ID, get_scheduler
from app.services import pipeline_service, video_service
from app.services.pipeline_service import PipelineOptions

router = APIRouter(
    prefix="/admin",
    tags=["admin"],
    dependencies=[AdminGuard],
    responses={
        status.HTTP_401_UNAUTHORIZED: {"model": ErrorResponse},
        status.HTTP_503_SERVICE_UNAVAILABLE: {"model": ErrorResponse},
    },
)


@router.post(
    "/pipeline/run",
    response_model=PipelineRunRead,
    status_code=status.HTTP_200_OK,
    summary="Ejecuta el pipeline semanal (descarga, redaccion IA, video, publicacion)",
)
def run_pipeline(
    db: DbSession,
    settings: AppSettings,
    payload: PipelineRunRequest | None = None,
) -> PipelineRunRead:
    """Lanza el pipeline de forma sincrona y devuelve el registro de la ejecucion.

    El resultado nunca es un 500 por un fallo externo: el estado de la
    ejecucion (``success``, ``partial``, ``skipped`` o ``failed``) viaja en el
    cuerpo de la respuesta.
    """
    request = payload or PipelineRunRequest()
    run = pipeline_service.run_weekly_pipeline(
        db,
        providers=build_providers(settings),
        options=PipelineOptions(
            week_start=request.week_start,
            trigger="manual",
            publish=request.publish,
            force=request.force,
        ),
        config=settings,
    )
    return PipelineRunRead.model_validate(run)


@router.get(
    "/pipeline/runs",
    response_model=List[PipelineRunRead],
    summary="Historial de ejecuciones del pipeline",
)
def list_pipeline_runs(
    db: DbSession,
    limit: int = Query(default=20, ge=1, le=100),
) -> List[PipelineRunRead]:
    """Devuelve las ultimas ejecuciones, de la mas reciente a la mas antigua."""
    stmt = select(PipelineRun).order_by(PipelineRun.id.desc()).limit(limit)
    runs = db.execute(stmt).scalars().all()
    return [PipelineRunRead.model_validate(run) for run in runs]


@router.post(
    "/videos/refresh",
    response_model=VideoRefreshRead,
    summary="Consulta el estado de las animaciones pendientes",
)
def refresh_videos(
    db: DbSession,
    settings: AppSettings,
    limit: int = Query(default=50, ge=1, le=200),
) -> VideoRefreshRead:
    """Actualiza ``video_url`` de las noticias cuyo video ya esta listo."""
    result = video_service.refresh_pending_videos(
        db,
        generator=build_video_generator(settings),
        limit=limit,
        config=settings,
    )
    return VideoRefreshRead(
        checked=result.checked,
        ready=result.ready,
        failed=result.failed,
        still_pending=result.still_pending,
        errors=result.errors,
    )


@router.get(
    "/integrations",
    response_model=IntegrationsStatusRead,
    summary="Adaptadores activos y estado del scheduler",
)
def integrations_status(settings: AppSettings) -> IntegrationsStatusRead:
    """Indica que adaptador se esta usando en cada punto de integracion.

    No devuelve ninguna clave: solo el nombre del adaptador elegido.
    """
    providers = build_providers(settings)
    scheduler = get_scheduler()
    job = scheduler.get_job(WEEKLY_PIPELINE_JOB_ID) if scheduler else None

    return IntegrationsStatusRead(
        news_provider=providers.news.name,
        text_generator=providers.text.name,
        video_generator=providers.video.name,
        video_generation_enabled=providers.video.enabled,
        scheduler_enabled=settings.enable_scheduler,
        scheduler_running=bool(scheduler and scheduler.running),
        next_weekly_run=getattr(job, "next_run_time", None),
    )
