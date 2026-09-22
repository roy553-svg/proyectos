"""Adaptador de la API de Gemini para redactar noticias.

Se usa la API REST directamente con ``httpx`` para no anadir un SDK mas: el
pipeline solo necesita una llamada ``generateContent``. La clave viaja en la
cabecera ``x-goog-api-key`` (nunca en la URL, que suele acabar en los logs).
"""

from __future__ import annotations

import json
import logging
from typing import Any, Dict

import httpx

from app.integrations.errors import IntegrationError
from app.integrations.schemas import GeneratedArticle, RawNewsItem

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = (
    "Eres el redactor jefe de 'El Profeta', el periodico magico. Reescribe la "
    "noticia recibida con un tono periodistico sobrio y ligeramente solemne, "
    "sin inventar datos que no aparezcan en el texto original y sin anadir "
    "opiniones. Responde unicamente con un objeto JSON con las claves "
    "'title' (maximo 120 caracteres), 'content' (entre 120 y 200 palabras, "
    "en parrafos separados por saltos de linea) y 'summary' (maximo 220 "
    "caracteres)."
)

#: Estructura exigida a la respuesta del modelo.
RESPONSE_SCHEMA: Dict[str, Any] = {
    "type": "OBJECT",
    "properties": {
        "title": {"type": "STRING"},
        "content": {"type": "STRING"},
        "summary": {"type": "STRING"},
    },
    "required": ["title", "content", "summary"],
}


class GeminiTextGenerator:
    """Redacta noticias con Gemini a partir del texto original."""

    name = "gemini"

    def __init__(
        self,
        api_key: str,
        *,
        base_url: str,
        model: str,
        timeout: float = 60.0,
        client: httpx.Client | None = None,
    ) -> None:
        self._api_key = api_key
        self._base_url = base_url.rstrip("/")
        self._model = model
        self._timeout = timeout
        self._client = client

    @property
    def model(self) -> str:
        """Modelo configurado (se guarda en ``NewsArticle.ai_model``)."""
        return self._model

    def rewrite(self, item: RawNewsItem) -> GeneratedArticle:
        """Reescribe la noticia; lanza ``IntegrationError`` si la API falla."""
        payload = {
            "systemInstruction": {"parts": [{"text": SYSTEM_PROMPT}]},
            "contents": [{"role": "user", "parts": [{"text": self._user_prompt(item)}]}],
            "generationConfig": {
                "responseMimeType": "application/json",
                "responseSchema": RESPONSE_SCHEMA,
                "temperature": 0.7,
            },
        }

        data = self._post(f"/models/{self._model}:generateContent", payload)
        return self._to_article(data, item)

    # -- Internos ---------------------------------------------------------

    @staticmethod
    def _user_prompt(item: RawNewsItem) -> str:
        """Construye el prompt con la noticia original."""
        return (
            f"Categoria: {item.category}\n"
            f"Idioma de salida: {item.language}\n"
            f"Titular original: {item.title}\n"
            f"Entradilla original: {item.summary}\n"
            f"Cuerpo original:\n{item.content}"
        )

    def _post(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        """Envia la peticion a Gemini y devuelve el JSON de respuesta."""
        client = self._client or httpx.Client(timeout=self._timeout)
        try:
            response = client.post(
                f"{self._base_url}{path}",
                json=payload,
                headers={
                    "x-goog-api-key": self._api_key,
                    "Content-Type": "application/json",
                },
            )
            response.raise_for_status()
            return response.json()
        except httpx.HTTPError as exc:
            raise IntegrationError(
                f"Gemini no respondio correctamente: {type(exc).__name__}"
            ) from None
        finally:
            if self._client is None:
                client.close()

    def _to_article(self, data: Dict[str, Any], item: RawNewsItem) -> GeneratedArticle:
        """Extrae el JSON generado por el modelo y lo valida."""
        try:
            parts = data["candidates"][0]["content"]["parts"]
            text = "".join(part.get("text", "") for part in parts)
            generated = json.loads(text)
        except (KeyError, IndexError, TypeError, json.JSONDecodeError) as exc:
            raise IntegrationError(
                f"Respuesta de Gemini con formato inesperado: {type(exc).__name__}"
            ) from None

        title = str(generated.get("title") or item.title).strip()
        content = str(generated.get("content") or item.content).strip()
        summary = str(generated.get("summary") or item.summary).strip()
        if not content:
            raise IntegrationError("Gemini devolvio un cuerpo de noticia vacio.")

        return GeneratedArticle(
            title=title[:300],
            content=content,
            summary=summary[:500],
            model=self._model,
        )
