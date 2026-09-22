import { useMemo, useState } from "react";
import { INSTITUCIONES, ROLES, transaccionVisible, type Rol } from "./lib/tipos";
import { DATOS_OPERACION, estadoEn, PASO_MAXIMO, TRANSACCIONES } from "./lib/escenario";
import { ESTILO_ROL } from "./lib/estilos";
import { ArbolTransaccion } from "./components/ArbolTransaccion";
import { LibroContable } from "./components/LibroContable";
import { PanelCripto } from "./components/PanelCripto";
import { RedEnVivo } from "./components/RedEnVivo";
import { Panel, Etiqueta, IconoCandado } from "./components/Primitivos";

type Pestana = "flujo" | "libro" | "cripto" | "vivo";

const PESTANAS: { id: Pestana; etiqueta: string; descripcion: string }[] = [
  { id: "flujo", etiqueta: "Privacidad de sub-transaccion", descripcion: "Arbol de la transaccion y quien ve cada nodo" },
  { id: "libro", etiqueta: "Libro contable", descripcion: "Contratos que este nodo almacena" },
  { id: "cripto", etiqueta: "Auditoria criptografica", descripcion: "Compromisos, firmas y ausencia de fugas" },
  { id: "vivo", etiqueta: "Red en vivo", descripcion: "Consulta real a los cuatro nodos Canton" },
];

export function App() {
  const [perspectiva, setPerspectiva] = useState<Rol>("beta");
  const [paso, setPaso] = useState(6);
  const [pestana, setPestana] = useState<Pestana>("flujo");
  const [txSeleccionada, setTxSeleccionada] = useState("tx-5");

  const contratos = useMemo(() => estadoEn(paso), [paso]);
  const tx = TRANSACCIONES.find((t) => t.id === txSeleccionada) ?? TRANSACCIONES[0];
  const visiblesEnPaso = TRANSACCIONES.filter((t) => t.paso <= paso);
  const est = ESTILO_ROL[perspectiva];

  return (
    <div className="mx-auto min-h-screen max-w-[1600px] px-4 pb-16 sm:px-6 lg:px-8">
      {/* ---------------------------------------------------------------- */}
      <header className="border-b border-[var(--color-borde)] py-6">
        <div className="flex flex-wrap items-start justify-between gap-6">
          <div>
            <div className="flex items-center gap-2.5">
              <span className="rounded-md border border-cyan-500/40 bg-cyan-500/10 px-2 py-0.5 font-mono text-[10px] uppercase tracking-[0.16em] text-cyan-300">
                Canton Network
              </span>
              <span className="font-mono text-[10px] uppercase tracking-[0.16em] text-[var(--color-tinta-tenue)]">
                Daml 3.4 · prueba de concepto
              </span>
            </div>
            <h1 className="mt-2.5 text-2xl font-semibold tracking-tight text-[var(--color-tinta)] sm:text-3xl">
              Libro de transacciones segregado por institucion
            </h1>
            <p className="mt-1.5 max-w-3xl text-sm leading-relaxed text-[var(--color-tinta-suave)]">
              Liquidacion interbancaria atomica entre dos bancos, con auditoria regulatoria y sin que
              ninguna institucion acceda al libro de otra. Lo que ve a continuacion es el mismo
              escenario que verifican las aserciones del modelo Daml.
            </p>
          </div>

          <div className="rounded-xl border border-[var(--color-borde)] bg-[var(--color-panel)] px-4 py-3">
            <Etiqueta>Operacion en curso</Etiqueta>
            <p className="mt-1 font-mono text-lg font-semibold text-[var(--color-tinta)]">
              {DATOS_OPERACION.importeFormateado}
            </p>
            <p className="font-mono text-[11px] text-[var(--color-tinta-tenue)]">
              {DATOS_OPERACION.settlementId}
            </p>
            <p className="mt-1 text-[11px] text-[var(--color-tinta-suave)]">
              {DATOS_OPERACION.referencia}
            </p>
          </div>
        </div>
      </header>

      {/* ---------------------------------------------------------------- */}
      <section className="py-5">
        <Etiqueta>Ver el sistema como</Etiqueta>
        <div className="mt-2.5 grid gap-2.5 sm:grid-cols-2 lg:grid-cols-4">
          {ROLES.map((rol) => {
            const institucion = INSTITUCIONES[rol];
            const estilo = ESTILO_ROL[rol];
            const activo = perspectiva === rol;
            return (
              <button
                key={rol}
                type="button"
                onClick={() => setPerspectiva(rol)}
                className={`rounded-xl border px-4 py-3 text-left transition-all ${
                  activo
                    ? `${estilo.botonActivo} shadow-lg`
                    : "border-[var(--color-borde)] bg-[var(--color-panel)] text-[var(--color-tinta-suave)] hover:border-[var(--color-borde-vivo)]"
                }`}
              >
                <div className="flex items-center gap-2">
                  <span className={`h-2 w-2 rounded-full ${estilo.punto}`} />
                  <span className="text-sm font-semibold">{institucion.nombreCorto}</span>
                </div>
                <p className="mt-1 text-[11px] leading-relaxed opacity-80">{institucion.papel}</p>
                <p className="mt-1 font-mono text-[10px] opacity-60">
                  {institucion.bic} · nodo :{institucion.puertoJson}
                </p>
              </button>
            );
          })}
        </div>
      </section>

      {/* ---------------------------------------------------------------- */}
      <section className="pb-5">
        <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-[var(--color-borde)] bg-[var(--color-panel)] px-4 py-3.5">
          <div className="min-w-0 flex-1">
            <Etiqueta>Avance del ciclo de liquidacion</Etiqueta>
            <div className="mt-2 flex flex-wrap items-center gap-1.5">
              {TRANSACCIONES.map((t) => {
                const alcanzado = t.paso <= paso;
                const visible = transaccionVisible(t, perspectiva);
                return (
                  <button
                    key={t.id}
                    type="button"
                    onClick={() => {
                      setPaso(t.paso);
                      setTxSeleccionada(t.id);
                      setPestana("flujo");
                    }}
                    title={t.titulo}
                    className={`flex items-center gap-1.5 rounded-md border px-2.5 py-1.5 text-[11px] transition-colors ${
                      txSeleccionada === t.id
                        ? "border-cyan-400/60 bg-cyan-500/15 text-cyan-200"
                        : alcanzado
                          ? "border-[var(--color-borde-vivo)] bg-[var(--color-panel-alto)] text-[var(--color-tinta-suave)]"
                          : "border-[var(--color-borde)] text-[var(--color-tinta-tenue)]"
                    }`}
                  >
                    {!visible ? <IconoCandado className="opacity-60" /> : null}
                    <span className="font-mono">{t.paso}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setPaso((p) => Math.max(0, p - 1))}
              disabled={paso === 0}
              className="rounded-lg border border-[var(--color-borde-vivo)] bg-[var(--color-panel-alto)] px-3 py-2 text-xs text-[var(--color-tinta)] transition-colors hover:border-cyan-500/50 disabled:opacity-40"
            >
              Anterior
            </button>
            <button
              type="button"
              onClick={() => setPaso((p) => Math.min(PASO_MAXIMO, p + 1))}
              disabled={paso === PASO_MAXIMO}
              className="rounded-lg border border-cyan-500/40 bg-cyan-500/15 px-3 py-2 text-xs font-medium text-cyan-200 transition-colors hover:bg-cyan-500/25 disabled:opacity-40"
            >
              Siguiente paso
            </button>
          </div>
        </div>
      </section>

      {/* ---------------------------------------------------------------- */}
      <nav className="flex flex-wrap gap-1.5 border-b border-[var(--color-borde)] pb-px">
        {PESTANAS.map((p) => (
          <button
            key={p.id}
            type="button"
            onClick={() => setPestana(p.id)}
            className={`rounded-t-lg border-b-2 px-4 py-2.5 text-xs font-medium transition-colors ${
              pestana === p.id
                ? "border-cyan-400 text-[var(--color-tinta)]"
                : "border-transparent text-[var(--color-tinta-tenue)] hover:text-[var(--color-tinta-suave)]"
            }`}
          >
            {p.etiqueta}
          </button>
        ))}
      </nav>

      <main className="pt-5">
        {pestana === "flujo" ? (
          <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_340px]">
            <Panel
              titulo={tx.titulo}
              descripcion={tx.resumen}
              accion={
                transaccionVisible(tx, perspectiva) ? null : (
                  <span className="flex shrink-0 items-center gap-1.5 rounded-md border border-slate-600/50 bg-slate-800/50 px-2 py-1 text-[10px] uppercase tracking-wider text-slate-400">
                    <IconoCandado /> invisible para este nodo
                  </span>
                )
              }
            >
              {transaccionVisible(tx, perspectiva) ? (
                <ArbolTransaccion tx={tx} perspectiva={perspectiva} />
              ) : (
                <div className="rounded-lg border border-dashed border-slate-700/60 bg-slate-900/30 px-5 py-10 text-center">
                  <IconoCandado className="mx-auto mb-3 h-6 w-6 text-slate-500" />
                  <p className="text-sm font-medium text-[var(--color-tinta-suave)]">
                    El nodo de {INSTITUCIONES[perspectiva].nombreCorto} no recibio ningun nodo de
                    esta transaccion.
                  </p>
                  <p className="mx-auto mt-2 max-w-xl text-xs leading-relaxed text-[var(--color-tinta-tenue)]">
                    No es que los datos esten cifrados y guardados a la espera de una clave: el
                    sincronizador sencillamente nunca se los envia. Desde este nodo, la operacion no
                    ha ocurrido.
                  </p>
                </div>
              )}
            </Panel>

            <div className="space-y-3">
              <Panel titulo="Transacciones del escenario" descripcion="Seleccione una para inspeccionar su arbol.">
                <ul className="space-y-1.5">
                  {visiblesEnPaso.map((t) => {
                    const visible = transaccionVisible(t, perspectiva);
                    return (
                      <li key={t.id}>
                        <button
                          type="button"
                          onClick={() => setTxSeleccionada(t.id)}
                          className={`w-full rounded-lg border px-3 py-2.5 text-left transition-colors ${
                            txSeleccionada === t.id
                              ? "border-cyan-400/50 bg-cyan-500/10"
                              : "border-[var(--color-borde)] bg-[var(--color-panel-alto)] hover:border-[var(--color-borde-vivo)]"
                          }`}
                        >
                          <div className="flex items-center gap-2">
                            {visible ? (
                              <span className={`h-1.5 w-1.5 shrink-0 rounded-full ${est.punto}`} />
                            ) : (
                              <IconoCandado className="shrink-0 text-slate-500" />
                            )}
                            <span
                              className={`truncate text-xs font-medium ${
                                visible ? "text-[var(--color-tinta)]" : "text-slate-500"
                              }`}
                            >
                              {t.titulo}
                            </span>
                          </div>
                          <p className="mt-0.5 pl-3.5 font-mono text-[10px] text-[var(--color-tinta-tenue)]">
                            {t.instante}
                          </p>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              </Panel>

              <Panel titulo="Por que importa" descripcion="Traduccion ejecutiva de lo que muestra el arbol.">
                <ul className="space-y-2.5 text-xs leading-relaxed text-[var(--color-tinta-suave)]">
                  <li className="flex gap-2">
                    <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-cyan-400" />
                    <span>
                      En el paso 3, Banco Beta{" "}
                      <strong className="text-[var(--color-tinta)]">confirma</strong> la transaccion
                      de fondeo sin recibir el subarbol del cargo. Valida una operacion cuyo detalle
                      no puede leer.
                    </span>
                  </li>
                  <li className="flex gap-2">
                    <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-violet-400" />
                    <span>
                      En el paso 4 ocurre lo simetrico: Alfa confirma sin ver el saldo de Beta.
                      Ninguna de las dos partes obtiene ventaja informativa sobre la otra.
                    </span>
                  </li>
                  <li className="flex gap-2">
                    <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-amber-400" />
                    <span>
                      Banco Gamma no aparece en ningun nodo de ninguna transaccion. Para su
                      infraestructura, esta operacion no existe.
                    </span>
                  </li>
                  <li className="flex gap-2">
                    <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-emerald-400" />
                    <span>
                      El Banco Central lo ve todo, pero unicamente porque los contratos lo declaran
                      observador. No puede ejecutar ninguna choice.
                    </span>
                  </li>
                </ul>
              </Panel>
            </div>
          </div>
        ) : null}

        {pestana === "libro" ? (
          <Panel
            titulo={`Libro contable desde ${INSTITUCIONES[perspectiva].nombre}`}
            descripcion="Se listan todos los contratos que existen en la red. Los que este nodo no posee aparecen tachados: no son datos cifrados a la espera de clave, sencillamente no estan en su almacen."
          >
            <LibroContable contratos={contratos} perspectiva={perspectiva} />
          </Panel>
        ) : null}

        {pestana === "cripto" ? (
          <Panel
            titulo="Panel de auditoria criptografica"
            descripcion="Compromisos verificables, papel del secuenciador y del mediador, y por que un competidor no puede adelantarse a una orden."
          >
            <PanelCripto contratos={contratos} perspectiva={perspectiva} />
          </Panel>
        ) : null}

        {pestana === "vivo" ? (
          <Panel
            titulo="Red Canton en vivo"
            descripcion="Consulta directa a la API JSON de los cuatro nodos participantes."
          >
            <RedEnVivo />
          </Panel>
        ) : null}
      </main>

      <footer className="mt-10 border-t border-[var(--color-borde)] pt-5 text-[11px] leading-relaxed text-[var(--color-tinta-tenue)]">
        Prueba de concepto con fines de demostracion. El modelo Daml y sus aserciones son
        ejecutables y verificables (<span className="font-mono">daml test</span>); esta interfaz
        reproduce ese mismo escenario y, en la pestana Red en vivo, consulta nodos Canton reales.
      </footer>
    </div>
  );
}
