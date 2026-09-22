#!/usr/bin/env bash
# Ejecuta la demo contra la red Canton REAL, con cada Party en el nodo de su
# propia institucion. Requiere que scripts/levantar-red.sh ya este corriendo.
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f canton/parties.json ]; then
  echo "La red no esta arrancada (falta canton/parties.json)." >&2
  echo "Arranca primero:  ./scripts/levantar-red.sh" >&2
  exit 1
fi

echo "Ejecutando BankLedger:demoEnRedReal contra los cuatro nodos..."
daml script \
  --dar .daml/dist/canton-banking-poc-1.0.0.dar \
  --script-name BankLedger:demoEnRedReal \
  --participant-config canton/participants.json \
  --input-file canton/parties.json \
  --wall-clock-time

echo
echo "Comprobando que nodo ve que cosa..."
python3 scripts/verificar-privacidad.py
