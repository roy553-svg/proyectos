# Precisión: qué puede prometer esta app y qué no

Este documento cumple el requisito 20 del enunciado: no prometer precisión que el
hardware no da, y separar lo que hace el teléfono solo de lo que exige
infraestructura instalada en la plaza.

## Lo que el teléfono puede hacer solo

| Capacidad | Calidad real | Comentario |
|---|---|---|
| Saber hacia dónde mira | muy buena (1–3°) con ARCore | la brújula sola se va 20–40° dentro de un centro comercial |
| Medir cuánto se ha movido | buena: 1–3 % de error acumulado | odometría visual-inercial de ARCore |
| Calcular la ruta | exacta | es determinista: grafo + A\* |
| Dibujar la flecha y el mapa | exacto | depende solo de los datos y de la pose |
| Leer un QR | exacto | posición + orientación en un solo gesto |
| Saber **dónde está** al arrancar | **no puede** | ARCore no tiene referencia absoluta; el GPS en interiores da 15–30 m |
| Saber en qué piso está | **no puede de forma fiable** | el barómetro no distingue plantas con seguridad |

Las dos últimas filas son la razón de que exista el paso "¿dónde estás?".

## Limitación principal, dicha sin adornos

**ARCore no sabe en qué parte de la plaza está el usuario ni dónde está el
norte.** Su sistema de coordenadas nace donde arranca la sesión y con una
orientación arbitraria. Por eso la app necesita un ajuste externo (QR o punto
marcado en el mapa) antes de mostrar la flecha, y por eso la flecha no aparece
hasta que ese ajuste existe.

Consecuencias que el usuario nota:

- La precisión **empeora a medida que se camina** (≈2 % de la distancia recorrida).
  Se muestra en pantalla como `±N m`, no se esconde.
- Si ARCore pierde el seguimiento (poca luz, suelo pulido sin textura, movimiento
  brusco), la flecha se atenúa y aparece el motivo concreto.
- Al volver del mapa 2D la sesión AR es nueva: se vuelve a anclar en la última
  posición conocida y la precisión declarada baja a la del modo manual.
- Un cambio de piso no se detecta solo: la app lo pide confirmar, y ese gesto
  sirve además para reanclar la posición.

## Solución para el MVP

1. **Arranque con QR** en los puntos donde la gente se detiene: entradas, cruce del
   pasillo central, junto a ascensores. Coste: imprimir y pegar etiquetas.
2. **Alternativa manual** de dos toques en el mapa (dónde estoy / hacia dónde
   miro) para poder probar sin haber pegado nada.
3. **Odometría de ARCore** entre ajustes, con filtro de pose para que la flecha no
   tiemble.
4. **Reanclaje voluntario**: escanear otro QR en cualquier momento borra la deriva.
5. **Respaldo siempre disponible**: mapa 2D con la ruta marcada, y modo
   brújula + pasos en dispositivos sin ARCore.

Con esto, en un pasillo de centro comercial la experiencia es: dirección correcta
siempre, distancia con un error de 1–3 m tras 50–100 m caminados, y "has llegado"
con un radio de 4 m.

## Solución para una implementación comercial

Por orden de relación coste/beneficio:

1. **QR + balizas BLE** (recomendado). Una baliza cada 10–15 m corrige la deriva de
   forma continua (2–5 m) sin que el usuario haga nada. Trilateración simple o
   *fingerprinting* de RSSI. Entra como un `ProveedorPose` nuevo.
2. **Wi-Fi RTT (802.11mc)**: 1–2 m, sin hardware nuevo si los puntos de acceso son
   compatibles, pero depende también del modelo de teléfono.
3. **UWB**: 10–30 cm, la mejor precisión, con anclas cableadas y coste alto; hoy
   pocos móviles lo llevan.
4. **Mapa VPS propio** (nube de puntos del centro comercial, tipo *ARCore Cloud
   Anchors* o un servicio de localización visual): reconoce el sitio por la
   cámara y da pose absoluta, a cambio de levantar y mantener el mapa visual y de
   necesitar red.
5. **Detección automática de piso**: combinación de baliza por planta y barómetro
   relativo; nunca barómetro solo.

Lo que **no** cambia al dar cualquiera de estos pasos: el modelo de datos, el
grafo, A\*, el motor de navegación, la flecha, el mapa 2D y el panel de
administración. Eso era el objetivo de la arquitectura.

## Qué parte necesita infraestructura en la plaza

| Parte | Teléfono solo | Necesita instalar algo |
|---|---|---|
| Mapa y grafo | sí (se configura una vez en el editor) | — |
| Ruta y recálculo | sí | — |
| Orientación de la flecha | sí | — |
| Posición inicial | sí, a mano (±3 m) | QR impresos (±0,5 m) |
| Posición continua sin deriva | no | BLE / Wi-Fi RTT / UWB |
| Piso actual automático | no | baliza por planta |
