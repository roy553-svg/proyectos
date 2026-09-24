Transaction Processing System.

Backend en C# / ASP.NET CoreAPI + Worker en una sola solución Procesamiento asíncrono con colas de
Optimización SQL Server + RabbitMQ.

"Desplegué la API y el worker en Azure App Service usando el plan gratuito y Azure Service Bus Basic,
manteniendo la base de datos local para evitar costos, pero con una arquitectura lista para producción.”

---

# El Profeta — periódico mágico semanal

Proyecto independiente que convive en este repositorio (no comparte código con
el sistema de transacciones de arriba).

| Fase | Carpeta | Contenido |
| --- | --- | --- |
| 1 y 2 | [`backend/`](backend/README.md) | API FastAPI + SQLAlchemy, pipeline semanal con Gemini/Replicate/NewsAPI y APScheduler |
| 3 | [`android/`](android/README.md) | App Kotlin + Jetpack Compose (Retrofit, Media3/ExoPlayer) |

Arranque rápido:

```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
cp .env.example .env
alembic upgrade head
python -m scripts.seed --reset
uvicorn app.main:app --reload
# http://127.0.0.1:8000/docs
```

La app Android se abre con Android Studio desde `android/` y apunta por
defecto a `http://10.0.2.2:8000/` (el host de desarrollo visto desde el
emulador). Cada carpeta tiene su propio README con los detalles.
