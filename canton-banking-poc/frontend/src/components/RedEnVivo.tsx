import { useCallback, useEffect, useState } from "react";
import { INSTITUCIONES, ROLES, type Rol } from "../lib/tipos";
import { ESTILO_ROL } from "../lib/estilos";
import { leerRed, type EstadoNodo } from "../lib/ledgerReal";
import { Etiqueta } from "./Primitivos";

const NOMBRE_LIBRO = (party: string) => party.split("::")[0];

export function RedEnVivo() {
  const [estado, setEstado] = useState<EstadoNodo[] | null>(null);
  const [cargando, setCargando] = useState(false);

  const consultar = useCallback(async () => {
    setCargando(true);
    setEstado(await leerRed(ROLES));
    setCargando(false);
  }, []);

  useEffect(() => {
    void consultar();
  }, [consultar]);

  const algunoVivo = estado?.some((n) => n.disponible) ?? false;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="max-w-3xl text-xs leading-relaxed text-[var(--color-tinta-suave)]">
          Esta vista no simula nada: consulta la API JSON de los cuatro nodos participantes por
          separado y muestra los contratos que cada uno tiene realmente en su almacen local. Si dos
          nodos devuelven conjuntos distintos, la privacidad no es una promesa de producto sino un
          hecho observable.
        </p>
        <button
          type="button"
          onClick={() => void consultar()}
          disabled={cargando}
          className="shrink-0 rounded-lg border border-[var(--color-borde-vivo)] bg-[var(--color-panel-alto)] px-3.5 py-2 text-xs font-medium text-[var(--color-tinta)] transition-colors hover:border-cyan-500/50 disabled:opacity-50"
        >
          {cargando ? "Consultando..." : "Volver a consultar"}
        </button>
      </div>

      {!algunoVivo && estado ? (
        <div className="rounded-lg border border-amber-500/30 bg-amber-500/5 p-4">
          <h3 className="text-sm font-semibold text-amber-200">La red Canton no responde</h3>
          <p className="mt-1.5 text-xs leading-relaxed text-[var(--color-tinta-suave)]">
            Arranque la red local y vuelva a consultar. Desde la raiz del proyecto:
          </p>
          <pre className="mt-2.5 overflow-x-auto rounded-md border border-[var(--color-borde)] bg-black/40 p-3 font-mono text-[11px] text-[var(--color-tinta-suave)]">
{`daml build
./scripts/levantar-red.sh      # en otra terminal
./scripts/ejecutar-demo.sh`}
          </pre>
          <p className="mt-2.5 text-[11px] text-[var(--color-tinta-tenue)]">
            Mientras tanto, el resto de la aplicacion funciona en modo demostracion con el mismo
            escenario y los mismos importes que verifica el script de Daml.
          </p>
        </div>
      ) : null}

      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        {(estado ?? ROLES.map((rol): EstadoNodo => ({ rol, disponible: false, contratos: [] }))).map((nodo) => {
          const est = ESTILO_ROL[nodo.rol];
          const institucion = INSTITUCIONES[nodo.rol];
          const librosVistos = new Set(
            nodo.contratos
              .map((c) => (c.campos as { bank?: string }).bank)
              .filter((b): b is string => Boolean(b))
              .map(NOMBRE_LIBRO),
          );
          const porPlantilla = nodo.contratos.reduce<Record<string, number>>((acc, c) => {
            acc[c.plantilla] = (acc[c.plantilla] ?? 0) + 1;
            return acc;
          }, {});

          return (
            <div
              key={nodo.rol}
              className={`rounded-xl border bg-[var(--color-panel)] p-4 ${
                nodo.disponible ? est.borde : "border-[var(--color-borde)]"
              }`}
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span
                    className={`h-2 w-2 rounded-full ${
                      nodo.disponible ? `${est.punto} latido` : "bg-slate-600"
                    }`}
                  />
                  <h3 className={`text-sm font-semibold ${est.texto}`}>
                    {institucion.nombreCorto}
                  </h3>
                </div>
                <span className="font-mono text-[10px] text-[var(--color-tinta-tenue)]">
                  :{institucion.puertoJson}
                </span>
              </div>

              {nodo.disponible ? (
                <>
                  <p className="mt-3 font-mono text-3xl font-semibold tracking-tight text-[var(--color-tinta)]">
                    {nodo.contratos.length}
                  </p>
                  <p className="text-[10px] uppercase tracking-wider text-[var(--color-tinta-tenue)]">
                    contratos en su almacen
                  </p>

                  <ul className="mt-3 space-y-1 border-t border-[var(--color-borde)] pt-2.5">
                    {Object.entries(porPlantilla)
                      .sort(([a], [b]) => a.localeCompare(b))
                      .map(([plantilla, total]) => (
                        <li key={plantilla} className="flex justify-between gap-2 text-[11px]">
                          <span className="truncate font-mono text-[var(--color-tinta-suave)]">
                            {plantilla}
                          </span>
                          <span className="font-mono text-[var(--color-tinta)]">{total}</span>
                        </li>
                      ))}
                    {nodo.contratos.length === 0 ? (
                      <li className="text-[11px] text-[var(--color-tinta-tenue)]">
                        sin contratos todavia
                      </li>
                    ) : null}
                  </ul>

                  <div className="mt-3 border-t border-[var(--color-borde)] pt-2.5">
                    <Etiqueta>Libros visibles</Etiqueta>
                    <p className="mt-1 font-mono text-[11px] text-[var(--color-tinta)]">
                      {librosVistos.size > 0 ? [...librosVistos].sort().join(", ") : "—"}
                    </p>
                  </div>
                </>
              ) : (
                <p className="mt-3 text-[11px] leading-relaxed text-[var(--color-tinta-tenue)]">
                  {nodo.error ?? "Sin conexion con este nodo."}
                </p>
              )}
            </div>
          );
        })}
      </div>

      {algunoVivo ? <Veredicto estado={estado ?? []} /> : null}
    </div>
  );
}

function Veredicto({ estado }: { estado: EstadoNodo[] }) {
  const librosDe = (rol: Rol) =>
    new Set(
      (estado.find((n) => n.rol === rol)?.contratos ?? [])
        .map((c) => (c.campos as { bank?: string }).bank)
        .filter((b): b is string => Boolean(b))
        .map(NOMBRE_LIBRO),
    );

  const alfa = librosDe("alfa");
  const beta = librosDe("beta");
  const gamma = librosDe("gamma");
  const central = librosDe("central");

  const segregado =
    [...alfa].every((l) => l === "BancoAlfa") &&
    [...beta].every((l) => l === "BancoBeta") &&
    [...gamma].every((l) => l === "BancoGamma");
  const supervisa = central.size >= 2;

  return (
    <div
      className={`rounded-lg border p-4 ${
        segregado ? "border-emerald-500/30 bg-emerald-500/5" : "border-rose-500/30 bg-rose-500/5"
      }`}
    >
      <h3
        className={`text-sm font-semibold ${segregado ? "text-emerald-200" : "text-rose-200"}`}
      >
        {segregado
          ? "Verificado: cada banco solo tiene su propio libro"
          : "Atencion: se han detectado libros cruzados"}
      </h3>
      <p className="mt-1.5 text-xs leading-relaxed text-[var(--color-tinta-suave)]">
        {segregado ? (
          <>
            Banco Beta fue contraparte de Banco Alfa en la liquidacion y aun asi no tiene ni un solo
            contrato del libro de Alfa en su nodo. Banco Gamma no tiene constancia de que la
            operacion haya existido.{" "}
            {supervisa
              ? "El Banco Central ve los libros supervisados porque fue declarado observador de forma explicita en los contratos."
              : "Ejecute la demo para poblar el libro del supervisor."}
          </>
        ) : (
          <>Revise la configuracion de observadores del modelo Daml.</>
        )}
      </p>
    </div>
  );
}
