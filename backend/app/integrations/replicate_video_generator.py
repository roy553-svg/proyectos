"""Adaptador de Replicate para generar animaciones (Stable Video Diffusion).

La generacion es asincrona: ``submit`` encola una prediccion y devuelve su
identificador, y ``poll`` consulta el estado mas tarde (lo hace el job
periodico del scheduler). Asi ninguna peticion HTTP de la API publica queda
bloqueada esperando a un modelo de video.
"""

from __future__ import annotations

import logging
from typing import Any, Dict, Optional

import httpx

from app.integrations.errors import IntegrationError
from app.integrations.schemas import VideoJob
from app.models.enums import VideoStatus

logger = logging.getLogger(__name__)

#: Estados de Replicate -> estados internos.
STATUS_MAP: Dict[str, VideoStatus] = {
    "starting": VideoStatus.PENDING,
    "processing": VideoStatus.PROCESSING,
    "succeeded": VideoStatus.READY,
    "failed": VideoStatus.FAILED,
    "canceled": VideoStatus.FAILED,
}

#: Parametros por defecto de Stable Video Diffusion.
DEFAULT_INPUT: Dict[str, Any] = {
    "video_length": "14_frames_with_svd",
    "sizing_strategy": "maintain_aspect_ratio",
    "frames_per_second": 6,
    "motion_bucket_id": 127,
    "cond_aug": 0.02,
}


def _extract_video_url(output: Any) -> Optional[str]:
    """Obtiene la URL del video de la salida de Replicate.

    Segun el modelo, ``output`` puede ser una cadena o una lista de cadenas.
    """
    if isinstance(output, str):
        return output or None
    if isinstance(output, list):
        for value in reversed(output):
            if isinstance(value, str) and value:
                return value
    return None


class ReplicateVideoGenerator:
    """Crea y consulta predicciones de video en Replicate."""

    name = "replicate"
    enabled = True

    def __init__(
        self,
        api_token: str,
        *,
        base_url: str,
        model_version: str,
        timeout: float = 30.0,
        client: httpx.Client | None = None,
    ) -> None:
        self._api_token = api_token
        self._base_url = base_url.rstrip("/")
        self._model_version = model_version
        self._timeout = timeout
        self._client = client

    def submit(self, *, image_url: str, prompt: str) -> VideoJob:
        """Encola la animacion de una imagen.

        ``prompt`` no se envia al modelo (Stable Video Diffusion solo acepta
        imagen), pero se conserva en ``metadata`` para poder auditar con que
        noticia se pidio cada video.
        """
        payload = {
            "version": self._model_version,
            "input": {"input_image": image_url, **DEFAULT_INPUT},
        }
        data = self._request("POST", "/predictions", json=payload)
        job = self._to_job(data)
        job.metadata = {"prompt": prompt, "image_url": image_url}
        return job

    def poll(self, job_id: str) -> VideoJob:
        """Consulta el estado de una prediccion ya encolada."""
        data = self._request("GET", f"/predictions/{job_id}")
        return self._to_job(data)

    # -- Internos ---------------------------------------------------------

    def _request(self, method: str, path: str, **kwargs: Any) -> Dict[str, Any]:
        """Llamada autenticada a la API de Replicate."""
        client = self._client or httpx.Client(timeout=self._timeout)
        try:
            response = client.request(
                method,
                f"{self._base_url}{path}",
                headers={
                    "Authorization": f"Token {self._api_token}",
                    "Content-Type": "application/json",
                },
                **kwargs,
            )
            response.raise_for_status()
            return response.json()
        except httpx.HTTPError as exc:
            raise IntegrationError(
                f"Replicate no respondio correctamente: {type(exc).__name__}"
            ) from None
        finally:
            if self._client is None:
                client.close()

    @staticmethod
    def _to_job(data: Dict[str, Any]) -> VideoJob:
        """Normaliza la prediccion de Replicate a ``VideoJob``."""
        job_id = data.get("id")
        if not job_id:
            raise IntegrationError("Replicate devolvio una prediccion sin identificador.")

        status = STATUS_MAP.get(str(data.get("status")), VideoStatus.PROCESSING)
        video_url = _extract_video_url(data.get("output")) if status is VideoStatus.READY else None
        if status is VideoStatus.READY and not video_url:
            # La prediccion termino pero no hay fichero utilizable.
            status = VideoStatus.FAILED

        return VideoJob(
            job_id=str(job_id),
            status=status,
            video_url=video_url,
            error=data.get("error"),
        )
