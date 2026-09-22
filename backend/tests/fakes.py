"""Dobles de prueba de las integraciones externas.

Implementan los mismos ``Protocol`` que los adaptadores reales, de modo que el
pipeline se puede testear completo sin red ni claves.
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Dict, List, Optional, Sequence

from app.integrations.errors import IntegrationError
from app.integrations.schemas import GeneratedArticle, RawNewsItem, VideoJob
from app.models.enums import VideoStatus


def sample_items(count: int = 4) -> List[RawNewsItem]:
    """Noticias en bruto deterministas para los tests."""
    categories = ["technology", "science", "sports", "culture", "magic", "economy"]
    now = datetime(2026, 9, 21, 8, tzinfo=timezone.utc)
    return [
        RawNewsItem(
            title=f"Noticia original {index}",
            summary=f"Entradilla original {index}.",
            content=f"Cuerpo original de la noticia {index}. Segunda frase.",
            category=categories[index % len(categories)],
            language="es",
            image_url=f"https://cdn.example.com/{index}.jpg",
            source_url=f"https://example.com/{index}",
            source_name="Fuente de prueba",
            published_at=now + timedelta(hours=index),
        )
        for index in range(count)
    ]


class FakeNewsProvider:
    """Devuelve una lista fija de noticias."""

    name = "fake-news"

    def __init__(self, items: Optional[Sequence[RawNewsItem]] = None) -> None:
        self.items = list(items) if items is not None else sample_items()
        self.calls = 0

    def fetch(self, *, limit, language="es", categories=None):
        self.calls += 1
        return self.items[:limit]


class FakeTextGenerator:
    """Simula la redaccion con IA; puede fallar en las noticias indicadas."""

    name = "fake-ai"

    def __init__(self, *, model: str = "fake-model", fail_on: Sequence[int] = ()) -> None:
        self.model = model
        self.fail_on = set(fail_on)
        self.calls = 0

    def rewrite(self, item: RawNewsItem) -> GeneratedArticle:
        index = self.calls
        self.calls += 1
        if index in self.fail_on:
            raise IntegrationError("fallo simulado de la IA")
        return GeneratedArticle(
            title=f"[Profeta] {item.title}",
            content=f"Version redactada de: {item.content}",
            summary=f"Resumen IA: {item.summary}",
            model=self.model,
        )


class FakeVideoGenerator:
    """Encola trabajos de video en memoria y permite completarlos a voluntad."""

    name = "fake-video"

    def __init__(self, *, enabled: bool = True, fail_submit: bool = False) -> None:
        self.enabled = enabled
        self.fail_submit = fail_submit
        self.jobs: Dict[str, VideoJob] = {}
        self.submitted: List[str] = []

    def submit(self, *, image_url: str, prompt: str) -> VideoJob:
        if self.fail_submit:
            raise IntegrationError("fallo simulado al encolar el video")
        job_id = f"job-{len(self.jobs) + 1}"
        job = VideoJob(job_id=job_id, status=VideoStatus.PENDING)
        self.jobs[job_id] = job
        self.submitted.append(image_url)
        return job

    def poll(self, job_id: str) -> VideoJob:
        if job_id not in self.jobs:
            raise IntegrationError(f"trabajo desconocido: {job_id}")
        return self.jobs[job_id]

    # -- Utilidades de test ----------------------------------------------

    def complete(self, job_id: str, url: str = "https://cdn.example.com/video.mp4") -> None:
        """Marca un trabajo como terminado correctamente."""
        self.jobs[job_id] = VideoJob(
            job_id=job_id, status=VideoStatus.READY, video_url=url
        )

    def fail(self, job_id: str, error: str = "modelo caido") -> None:
        """Marca un trabajo como fallido."""
        self.jobs[job_id] = VideoJob(
            job_id=job_id, status=VideoStatus.FAILED, error=error
        )
