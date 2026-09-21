# Bot de Precios RD — imagen lista para correr 24/7
FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    TZ=America/Santo_Domingo

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends tzdata ca-certificates \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY bot.py pruebas.py ./
RUN mkdir -p /app/data
VOLUME ["/app/data"]

CMD ["python", "-u", "bot.py"]
