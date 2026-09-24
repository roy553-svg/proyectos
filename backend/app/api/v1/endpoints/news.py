"""Endpoints de noticias."""

from __future__ import annotations

from fastapi import APIRouter, Query, status

from app.api.deps import DbSession
from app.schemas.common import ErrorResponse
from app.schemas.news import NewsFeedResponse
from app.services import news_service

router = APIRouter(tags=["news"])


@router.get(
    "/news",
    response_model=NewsFeedResponse,
    status_code=status.HTTP_200_OK,
    summary="Edicion semanal publicada filtrada por las preferencias del usuario",
    responses={
        status.HTTP_404_NOT_FOUND: {
            "model": ErrorResponse,
            "description": "Usuario inexistente o sin edicion publicada.",
        }
    },
)
def read_news(
    db: DbSession,
    user_id: int = Query(
        ...,
        ge=1,
        description="Identificador del usuario cuyas preferencias se aplican.",
        examples=[1],
    ),
) -> NewsFeedResponse:
    """Devuelve la ultima edicion publicada con las noticias del usuario."""
    return news_service.get_news_feed(db, user_id=user_id)
