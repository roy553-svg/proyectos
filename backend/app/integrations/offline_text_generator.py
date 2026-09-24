"""Redactor de respaldo sin IA.

Se usa cuando no hay ``GEMINI_API_KEY`` configurada: genera un resumen
extractivo determinista para que el pipeline sea ejecutable de principio a fin
sin servicios externos.
"""

from __future__ import annotations

import re

from app.integrations.schemas import GeneratedArticle, RawNewsItem

MAX_SUMMARY_CHARS = 220


def _first_sentences(text: str, max_chars: int = MAX_SUMMARY_CHARS) -> str:
    """Devuelve las primeras frases completas que quepan en ``max_chars``."""
    clean = re.sub(r"\s+", " ", text).strip()
    if len(clean) <= max_chars:
        return clean

    summary = ""
    for sentence in re.split(r"(?<=[.!?])\s+", clean):
        if not sentence:
            continue
        candidate = f"{summary} {sentence}".strip()
        if len(candidate) > max_chars:
            break
        summary = candidate
    return summary or f"{clean[: max_chars - 3].rstrip()}..."


class OfflineTextGenerator:
    """Resume la noticia sin llamar a ningun servicio externo."""

    name = "offline"

    def rewrite(self, item: RawNewsItem) -> GeneratedArticle:
        """Devuelve la noticia con un resumen extractivo."""
        content = re.sub(r"\s+", " ", item.content).strip()
        summary = _first_sentences(item.summary or content)
        return GeneratedArticle(
            title=item.title.strip(),
            content=content,
            summary=summary,
            model=None,
        )
