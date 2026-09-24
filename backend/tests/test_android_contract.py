"""Contrato entre el backend (Fase 1/2) y la app Android (Fase 3).

La app declara los campos de la API en sus DTO de kotlinx.serialization. Este
test lee ese fichero Kotlin y comprueba que sigue coincidiendo con lo que
devuelve realmente ``GET /api/v1/news``: si alguien renombra o elimina un
campo del backend, el test falla antes de romper la app.
"""

from __future__ import annotations

import re
from pathlib import Path
from typing import Dict, Set

import pytest
from fastapi.testclient import TestClient

DTO_FILE = (
    Path(__file__).resolve().parents[2]
    / "android"
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "elprofeta"
    / "app"
    / "data"
    / "remote"
    / "dto"
    / "NewsDto.kt"
)

#: Campos que la app declara como obligatorios (sin valor por defecto).
PROPERTY_RE = re.compile(
    r"""(?:@SerialName\("(?P<serial>[^"]+)"\)\s*)?val\s+(?P<name>\w+)\s*:\s*(?P<type>[^=,\n]+)(?P<default>\s*=)?""",
)


def _parse_dto_fields(source: str, data_class: str) -> Dict[str, bool]:
    """Devuelve {nombre_json: tiene_valor_por_defecto} de una data class."""
    start = source.index(f"data class {data_class}(")
    open_paren = source.index("(", start)

    # Se busca el parentesis de cierre del constructor contando profundidad:
    # las anotaciones @SerialName("...") tambien llevan parentesis.
    depth = 0
    end = open_paren
    for index in range(open_paren, len(source)):
        if source[index] == "(":
            depth += 1
        elif source[index] == ")":
            depth -= 1
            if depth == 0:
                end = index
                break
    block = source[open_paren:end]

    fields: Dict[str, bool] = {}
    for match in PROPERTY_RE.finditer(block):
        json_name = match.group("serial") or match.group("name")
        fields[json_name] = bool(match.group("default"))
    return fields


@pytest.fixture(name="dto_source", scope="module")
def dto_source_fixture() -> str:
    if not DTO_FILE.exists():
        pytest.skip("La app Android no esta presente en este checkout.")
    return DTO_FILE.read_text(encoding="utf-8")


def test_article_dto_matches_api_response(
    client: TestClient, published_edition, make_preferences, dto_source: str
) -> None:
    """Todos los campos que envia la API existen en el DTO y viceversa."""
    make_preferences(1)
    payload = client.get("/api/v1/news", params={"user_id": 1}).json()
    article = payload["articles"][0]

    dto_fields = _parse_dto_fields(dto_source, "ArticleDto")
    api_fields: Set[str] = set(article)

    missing_in_app = api_fields - set(dto_fields)
    assert not missing_in_app, f"La app no conoce estos campos: {sorted(missing_in_app)}"

    # Un campo que la app exige (sin valor por defecto) debe venir siempre.
    required_by_app = {name for name, has_default in dto_fields.items() if not has_default}
    assert required_by_app <= api_fields, (
        f"La app exige campos que la API ya no envia: {sorted(required_by_app - api_fields)}"
    )


def test_edition_dto_matches_api_response(
    client: TestClient, published_edition, make_preferences, dto_source: str
) -> None:
    make_preferences(1)
    payload = client.get("/api/v1/news", params={"user_id": 1}).json()

    dto_fields = _parse_dto_fields(dto_source, "EditionDto")
    api_fields = set(payload["edition"])

    assert api_fields <= set(dto_fields)
    required_by_app = {name for name, has_default in dto_fields.items() if not has_default}
    assert required_by_app <= api_fields


def test_video_status_values_are_known_by_the_app() -> None:
    """Los estados de video del backend estan contemplados en el mapper Kotlin."""
    mapper = DTO_FILE.parent.parent.parent / "mapper" / "NewsMappers.kt"
    if not mapper.exists():
        pytest.skip("La app Android no esta presente en este checkout.")

    source = mapper.read_text(encoding="utf-8")
    from app.models.enums import VideoStatus

    for status in VideoStatus:
        if status is VideoStatus.NOT_REQUESTED:
            continue  # es el valor por defecto del `else`
        assert f'"{status.value}"' in source, f"El mapper no contempla {status.value}"
