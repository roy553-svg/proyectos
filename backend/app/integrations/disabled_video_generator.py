"""Generador de video desactivado (respaldo).

Se usa cuando no hay ``REPLICATE_API_TOKEN``/``REPLICATE_MODEL_VERSION``: el
pipeline sigue funcionando y las noticias se publican con ``video_url`` nulo,
exactamente como en la Fase 1.
"""

from __future__ import annotations

from app.integrations.schemas import VideoJob


class DisabledVideoGenerator:
    """No genera animaciones; el pipeline lo detecta con ``enabled = False``."""

    name = "disabled"
    enabled = False

    def submit(self, *, image_url: str, prompt: str) -> VideoJob:
        """Nunca debe invocarse: el pipeline comprueba ``enabled`` antes."""
        raise RuntimeError(
            "La generacion de video esta desactivada: configura REPLICATE_API_TOKEN "
            "y REPLICATE_MODEL_VERSION."
        )

    def poll(self, job_id: str) -> VideoJob:
        """Nunca debe invocarse: no hay trabajos que consultar."""
        raise RuntimeError("La generacion de video esta desactivada.")
