#!/usr/bin/env python3
"""Verificacion estatica ligera de las fuentes Kotlin, sin compilador.

Comprueba dos cosas que en un proyecto Android grande se rompen a menudo al
refactorizar y que no hacen falta ni el SDK ni la red para detectar:

  1. que los delimitadores ({ } ( ) [ ]) cuadran en cada archivo,
  2. que toda llamada a una funcion del propio proyecto usa argumentos con
     nombre que esa funcion declara de verdad.

No sustituye a `./gradlew :app:assembleDebug`: es un cedazo rapido.

    python3 herramientas/verificar_kotlin.py
"""
import re, glob, sys

def limpiar(texto):
    """Quita cadenas y comentarios. Las cadenas van PRIMERO: si no, una URL como
    "https://..." se confundiria con un comentario y se comeria el resto."""
    texto = re.sub(r'"""(?:.|\n)*?"""', '""', texto)
    texto = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', texto)
    texto = re.sub(r"'(?:\\.|[^'\\\n])'", "' '", texto)
    texto = re.sub(r'//[^\n]*', '', texto)
    texto = re.sub(r'/\*(?:.|\n)*?\*/', '', texto)
    # El "->" de los lambda no abre ni cierra nivel: se neutraliza.
    return texto.replace("->", "@@")


archivos = sorted(glob.glob("nucleo/src/**/*.kt", recursive=True) +
                  glob.glob("app/src/**/*.kt", recursive=True))
problemas = []

# --- 1. balance de delimitadores ---
for ruta in archivos:
    c = limpiar(open(ruta, encoding="utf-8").read())
    for abre, cierra, nombre in (("{", "}", "llaves"), ("(", ")", "parentesis"), ("[", "]", "corchetes")):
        if c.count(abre) != c.count(cierra):
            problemas.append(f"{ruta}: {nombre} descompensados ({c.count(abre)} vs {c.count(cierra)})")

# --- 2. declaraciones de funciones propias ---
def bloque(texto, inicio):
    """Devuelve el contenido del parentesis que empieza en `inicio`."""
    profundidad, i = 0, inicio
    while i < len(texto):
        if texto[i] == "(":
            profundidad += 1
        elif texto[i] == ")":
            profundidad -= 1
            if profundidad == 0:
                return texto[inicio + 1:i], i
        i += 1
    return None, inicio

declaraciones = {}
for ruta in archivos:
    c = limpiar(open(ruta, encoding="utf-8").read())
    for m in re.finditer(r"\bfun\s+(?:<[^>]*>\s*)?([A-Za-z_]\w*)\s*\(", c):
        nombre = m.group(1)
        params, _ = bloque(c, m.end() - 1)
        if params is None:
            continue
        # separa los parametros por comas de primer nivel
        partes, prof, actual = [], 0, ""
        for ch in params:
            if ch in "(<[":
                prof += 1
            elif ch in ")>]":
                prof -= 1
            if ch == "," and prof == 0:
                partes.append(actual); actual = ""
            else:
                actual += ch
        if actual.strip():
            partes.append(actual)
        info = []
        for p in partes:
            p = p.strip()
            mp = re.match(r"(?:vararg\s+|crossinline\s+|noinline\s+)?([A-Za-z_]\w*)\s*:", p)
            if mp:
                info.append((mp.group(1), "=" in p))
        declaraciones.setdefault(nombre, []).append((ruta, info))

# --- 3. llamadas con argumentos con nombre ---
for ruta in archivos:
    c = limpiar(open(ruta, encoding="utf-8").read())
    for nombre, versiones in declaraciones.items():
        if len(versiones) != 1:
            continue  # sobrecargas: se omiten
        _, params = versiones[0]
        nombres = {n for n, _ in params}
        obligatorios = {n for n, tiene_default in params if not tiene_default}
        for m in re.finditer(r"(?<![\w.])" + re.escape(nombre) + r"\s*\(", c):
            antes = c[max(0, m.start() - 30):m.start()]
            if re.search(r"\bfun\s+$", antes) or antes.rstrip().endswith("."):
                continue
            args, _ = bloque(c, m.end() - 1)
            if args is None:
                continue
            usados, prof = set(), 0
            for ma in re.finditer(r"([A-Za-z_]\w*)\s*=(?!=)", args):
                trozo = args[:ma.start()]
                if trozo.count("(") - trozo.count(")") == 0 and \
                   trozo.count("{") - trozo.count("}") == 0 and \
                   trozo.count("[") - trozo.count("]") == 0:
                    usados.add(ma.group(1))
            if not usados:
                continue
            desconocidos = usados - nombres
            if desconocidos:
                problemas.append(f"{ruta}: llamada a {nombre}() con argumentos que no existen: {sorted(desconocidos)}")

if problemas:
    print(f"{len(problemas)} posibles problemas:")
    for p in problemas:
        print(" -", p)
    sys.exit(1)
print(f"OK: {len(archivos)} archivos Kotlin, delimitadores equilibrados y "
      f"{len(declaraciones)} funciones propias con llamadas coherentes.")
