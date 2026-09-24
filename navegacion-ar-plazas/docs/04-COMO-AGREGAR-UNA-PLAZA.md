# Cómo añadir una plaza nueva

Hay dos caminos: el editor visual de la propia app (el normal) y editar el JSON
(cómodo para cargar datos masivos).

## Camino 1: el editor visual de la app

1. **Pantalla inicial → "Panel de administración" → botón `+`.**
   Nombre, ciudad y las medidas reales del piso 1 **en metros**. Todo el sistema
   trabaja en metros; si tienes el plano en píxeles, mide una referencia conocida
   (el ancho de un pasillo, la fachada) y convierte.

2. **Crea los pisos** con el botón `+` de la barra del editor. Cada piso tiene su
   ancho, su alto y su *rumbo del norte* (ver paso 7).

3. **Coloca los nodos de pasillo.** Herramienta `Nodo`, y toca a lo largo de los
   pasillos cada 8–12 m y en cada cruce. Cada nodo nuevo se conecta
   automáticamente con el más cercano del mismo piso, así que si los colocas en
   orden el pasillo queda encadenado solo.

4. **Coloca los establecimientos.** Elige la herramienta (`Tienda`,
   `Restaurante`, `Baño`, `Farmacia`, `Cajero`, `Ascensor`, `Escalera`,
   `Entrada`) y toca **en la puerta**, no en el centro del local. Al hacerlo la app
   crea tres cosas: el local en el plano, su nodo de acceso y la arista que lo une
   al nodo más cercano.

5. **Repasa las conexiones.** Herramienta `Conectar`: toca un nodo y luego otro.
   Para unir pisos, selecciona el nodo del ascensor en el piso 1, cambia de piso
   con las pestañas de arriba y toca el del piso 2: la app detecta que es un enlace
   vertical y le pone el coste de ascensor (18 m equivalentes) o de escalera (26 m).

6. **Crea los puntos QR.** Herramienta `Punto QR`, toca cerca del nodo donde vas a
   pegar la etiqueta y rellena:
   - **Rumbo**: la dirección del mapa hacia la que mirará quien lea el QR
     (0° = hacia arriba del plano, 90° = hacia la derecha). Este dato es el que
     permite orientar la flecha AR sin brújula, así que conviene medirlo bien.
   - **Descripción**: "Entrada Sur", "junto al ascensor"…
   Después pulsa el código en la lista de abajo para ver la imagen del QR y el
   texto que contiene, e imprímelo.

7. **Ajusta el rumbo del norte** (pestaña `Norte N°`): el azimut de brújula hacia
   el que apunta el eje +Y del plano. Solo lo usa el modo sin AR (brújula); con QR
   o con ARCore no interviene.

8. **Guarda** con el icono de confirmación. El editor muestra abajo el resultado de
   la validación: nodos aislados, establecimientos inalcanzables o referencias
   rotas. Si dice "Mapa válido: todos los lugares son alcanzables", la plaza está
   lista para navegar.

Los datos se guardan en el almacenamiento privado de la app
(`filesDir/plazas/plaza_<id>.json`), sin permisos y sin red.

## Camino 2: escribir el JSON

Útil para cargar 200 locales de una vez desde una hoja de cálculo. El formato es
el de `nucleo/src/main/resources/plazas/plaza_aurora.json`:

```json
{
  "id": "plaza_aurora",
  "nombre": "Plaza Aurora",
  "ciudad": "Ciudad Demo",
  "ficticia": true,
  "pisos": [
    {
      "id": "p1", "nivel": 1, "nombre": "Piso 1",
      "anchoMetros": 80.0, "altoMetros": 50.0, "rumboNorteGrados": 12.0,
      "planoImagen": null,
      "pasillos": [ { "x": 5.0, "y": 22.0, "ancho": 71.0, "alto": 6.0, "etiqueta": "Pasillo principal" } ],
      "locales":  [ { "x": 5.0, "y": 28.0, "ancho": 10.0, "alto": 16.0, "etiqueta": "Moda Lumen" } ]
    }
  ],
  "establecimientos": [
    {
      "id": "est_moda_lumen", "nombre": "Moda Lumen", "categoria": "TIENDA",
      "pisoId": "p1", "posicion": { "x": 10.0, "y": 36.0 },
      "nodoDestinoId": "P1_A_moda_lumen", "activo": true, "descripcion": null
    }
  ],
  "nodos": [
    { "id": "P1_A_moda_lumen", "pisoId": "p1", "posicion": { "x": 10.0, "y": 28.0 }, "tipo": "ACCESO", "nombre": "Puerta de Moda Lumen" }
  ],
  "aristas": [
    { "desdeId": "P1_A_moda_lumen", "hastaId": "P1_P10", "tipo": "PASILLO", "bidireccional": true, "costeExtraMetros": 0.0 }
  ],
  "puntosQr": [
    {
      "codigo": "QR_001", "plazaId": "plaza_aurora", "pisoId": "p1",
      "nodoId": "P1_ENT_SUR", "posicion": { "x": 40.0, "y": 2.0 },
      "rumboGrados": 0.0, "descripcion": "Entrada Sur - mirando al interior"
    }
  ]
}
```

Reglas del formato:

- **Unidades**: metros. **Origen**: esquina inferior izquierda del plano. **X** a la
  derecha, **Y** hacia arriba.
- **Rumbos**: grados horarios desde +Y (0 = arriba del plano, 90 = derecha).
- `categoria`: `TIENDA`, `RESTAURANTE`, `BANO`, `FARMACIA`, `CAJERO`, `ASCENSOR`,
  `ESCALERA`, `ENTRADA`, `SALIDA`, `SERVICIO`.
- `tipo` de nodo: `PASILLO`, `ACCESO`, `ASCENSOR`, `ESCALERA`, `ENTRADA`.
- `tipo` de arista: `PASILLO`, `ASCENSOR`, `ESCALERA`. En los enlaces verticales,
  `costeExtraMetros` es el coste en "metros equivalentes" de usar ese enlace.
- Cada establecimiento debe apuntar a un `nodoDestinoId` existente y del mismo piso.

Para instalar el archivo:

- **en desarrollo**: ponlo en `nucleo/src/main/resources/plazas/` y añádelo a
  `SemillaPlazas.RUTAS_RECURSOS`; se copia al almacenamiento local en el primer
  arranque;
- **generado por script**: `herramientas/generar_plaza_aurora.py` es un ejemplo
  completo de generación reproducible (geometría, nodos, aristas y QR coherentes);
- **en un dispositivo**: `adb push plaza_x.json /data/local/tmp/` y cópialo con la
  app, o impórtalo desde el panel de administración (guardar sobre un id existente
  lo reemplaza).

## Comprobar que la plaza es navegable

```
cd navegacion-ar-plazas
gradle :nucleo:test --tests "com.rumbo.nucleo.PlazaAuroraTest"
```

`PlazaAuroraTest` valida el JSON de demostración: referencias, conectividad, que
haya ruta desde **cada entrada hasta cada establecimiento activo**, que cada
establecimiento esté cerca de su nodo de acceso y que los QR se puedan codificar y
volver a leer. Copia ese test apuntando a tu archivo y tendrás la misma garantía
antes de pisar la plaza.

## Datos reales y datos de demostración

`Plaza.ficticia` marca los datos de demostración, y la UI lo muestra ("datos de
demostración"). **Plaza Aurora es inventada**: no representa ningún centro
comercial real, y los nombres de sus locales son ficticios a propósito. Al cargar
una plaza real, pon `ficticia: false` y usa los nombres con los permisos que
corresponda.
