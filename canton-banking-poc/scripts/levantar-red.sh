#!/usr/bin/env bash
# Levanta la red Canton local del PoC: sincronizador + cuatro nodos bancarios,
# despliega el DAR y asigna las identidades.
set -euo pipefail

cd "$(dirname "$0")/.."

SDK_VERSION="$(grep '^sdk-version:' daml.yaml | awk '{print $2}')"
CANTON_JAR="${CANTON_JAR:-$HOME/.daml/sdk/${SDK_VERSION}/canton/canton.jar}"
DAR=".daml/dist/canton-banking-poc-1.0.0.dar"

if [ ! -f "$CANTON_JAR" ]; then
  echo "No se encuentra canton.jar en: $CANTON_JAR" >&2
  echo "Instala el Daml SDK ${SDK_VERSION} o exporta CANTON_JAR con su ruta." >&2
  exit 1
fi

if [ ! -f "$DAR" ]; then
  echo "El DAR no existe todavia. Compilando..."
  daml build
fi

# Los identificadores de Party se regeneran en cada arranque.
rm -f canton/parties.json canton/participants.json

echo "Arrancando la red interbancaria Canton..."
echo "  (el arranque completo tarda entre 60 y 120 segundos)"

exec java -XX:+UseSerialGC -Xmx3g -jar "$CANTON_JAR" daemon \
  -c canton.conf \
  --bootstrap canton/bootstrap.canton \
  --log-level-stdout WARN
