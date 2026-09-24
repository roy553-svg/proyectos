"""Errores de las integraciones externas."""

from __future__ import annotations


class IntegrationError(RuntimeError):
    """Fallo al comunicarse con un servicio externo.

    Los mensajes no deben incluir claves ni cabeceras de autenticacion.
    """
