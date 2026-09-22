"""Integraciones externas (Fase 2) y sus respaldos offline."""

from app.integrations.base import NewsProvider, TextGenerator, VideoGenerator
from app.integrations.errors import IntegrationError
from app.integrations.factory import Providers, build_providers
from app.integrations.schemas import GeneratedArticle, RawNewsItem, VideoJob

__all__ = [
    "GeneratedArticle",
    "IntegrationError",
    "NewsProvider",
    "Providers",
    "RawNewsItem",
    "TextGenerator",
    "VideoGenerator",
    "VideoJob",
    "build_providers",
]
