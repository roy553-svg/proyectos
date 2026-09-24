"""Seleccion de adaptadores segun la configuracion.

Regla unica: si la clave del servicio esta configurada se usa el adaptador
real; si no, el de respaldo offline. De este modo el proyecto se ejecuta
completo sin claves y anadir una credencial no exige tocar codigo.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass

from app.core.config import Settings, settings as default_settings
from app.integrations.base import NewsProvider, TextGenerator, VideoGenerator
from app.integrations.disabled_video_generator import DisabledVideoGenerator
from app.integrations.gemini_text_generator import GeminiTextGenerator
from app.integrations.local_news_provider import LocalNewsProvider
from app.integrations.newsapi_provider import NewsApiProvider
from app.integrations.offline_text_generator import OfflineTextGenerator
from app.integrations.replicate_video_generator import ReplicateVideoGenerator

logger = logging.getLogger(__name__)


@dataclass(slots=True)
class Providers:
    """Conjunto de adaptadores que necesita el pipeline."""

    news: NewsProvider
    text: TextGenerator
    video: VideoGenerator

    def describe(self) -> str:
        """Resumen legible de los adaptadores activos (para logs y auditoria)."""
        return f"news={self.news.name} text={self.text.name} video={self.video.name}"


def build_news_provider(config: Settings | None = None) -> NewsProvider:
    """NewsAPI si hay clave; si no, el proveedor local de ejemplos."""
    config = config or default_settings
    if config.newsapi_api_key:
        return NewsApiProvider(
            config.newsapi_api_key,
            base_url=config.newsapi_base_url,
            country=config.news_country,
        )
    logger.info("NEWSAPI_API_KEY no configurada: se usan noticias de ejemplo locales.")
    return LocalNewsProvider()


def build_text_generator(config: Settings | None = None) -> TextGenerator:
    """Gemini si hay clave; si no, el redactor offline."""
    config = config or default_settings
    if config.gemini_api_key:
        return GeminiTextGenerator(
            config.gemini_api_key,
            base_url=config.gemini_base_url,
            model=config.gemini_model,
            timeout=config.gemini_timeout_seconds,
        )
    logger.info("GEMINI_API_KEY no configurada: se usa el redactor offline.")
    return OfflineTextGenerator()


def build_video_generator(config: Settings | None = None) -> VideoGenerator:
    """Replicate si hay token y version de modelo; si no, generacion desactivada."""
    config = config or default_settings
    if config.replicate_api_token and config.replicate_model_version:
        return ReplicateVideoGenerator(
            config.replicate_api_token,
            base_url=config.replicate_base_url,
            model_version=config.replicate_model_version,
            timeout=config.replicate_timeout_seconds,
        )
    logger.info(
        "Replicate no configurado: las noticias se publicaran sin animacion."
    )
    return DisabledVideoGenerator()


def build_providers(config: Settings | None = None) -> Providers:
    """Construye los tres adaptadores de una vez."""
    config = config or default_settings
    return Providers(
        news=build_news_provider(config),
        text=build_text_generator(config),
        video=build_video_generator(config),
    )
