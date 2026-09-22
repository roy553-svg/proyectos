import { useEffect, useState } from "react";
import { INSTITUCIONES, informeesDe, type Contrato, type Rol } from "../lib/tipos";
import { agrupar, compromisoDe, serializacionCanonica } from "../lib/cripto";
import { Etiqueta } from "./Primitivos";

interface Compromiso {
  contrato: Contrato;
  hash: string;
}

export function PanelCripto({
  contratos,
  perspectiva,
}: {
  contratos: Contrato[];
  perspectiva: Rol;
}) {
  const [compromisos, setCompromisos] = useState<Compromiso[]>([]);
  const [expandido, setExpandido] = useState<string | null>(null);

  const visibles = contratos.filter((c) => informeesDe(c).includes(perspectiva));

  useEffect(() => {
    let cancelado = false;
    Promise.all(
      visibles.map(async (contrato) => ({ contrato, hash: await compromisoDe(contrato) })),
    ).then((resultado) => {
      if (!cancelado) setCompromisos(resultado);
    });
    return () => {
      cancelado = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [contratos, perspectiva]);

  return (
    <div className="space-y-5">
      <div className="grid gap-3 sm:grid-cols-3">
        <Metrica
          titulo="Compromisos del nodo"
          valor={String(compromisos.length)}
          pie="hashes SHA-256 que este participante puede probar"
        />
        <Metrica
          titulo="Datos ajenos accesibles"
          valor="0"
          pie="ningun payload de otra institucion en este nodo"
          acento="text-emerald-300"
        />
        <Metrica
          titulo="Riesgo de front-running"
          valor="Nulo"
          pie="sin orden, importe ni contraparte observables por terceros"
          acento="text-emerald-300"
        />
      </div>

      <div>
        <Etiqueta>Hashes de compromiso por contrato</Etiqueta>
        <p className="mt-1.5 text-xs leading-relaxed text-[var(--color-tinta-suave)]">
          Cada participante guarda el hash canonico de los contratos que le conciernen.
          Con el puede demostrar ante un tribunal o un supervisor{" "}
          <strong className="text-[var(--color-tinta)]">que dato tenia</strong> sin necesidad de
          revelarlo, y el contrario no puede fabricar una version distinta de los hechos.
        </p>
        <ul className="mt-3 space-y-1.5">
          {compromisos.map(({ contrato, hash }) => (
            <li key={contrato.id}>
              <button
                type="button"
                onClick={() => setExpandido(expandido === contrato.id ? null : contrato.id)}
                className="flex w-full items-center gap-3 rounded-lg border border-[var(--color-borde)] bg-[var(--color-panel-alto)] px-3 py-2 text-left transition-colors hover:border-[var(--color-borde-vivo)]"
              >
                <span className="w-52 shrink-0 truncate font-mono text-[11px] text-[var(--color-tinta)]">
                  {contrato.plantilla}
                </span>
                <span className="min-w-0 flex-1 truncate font-mono text-[10px] text-cyan-300/80">
                  {hash}
                </span>
                <span className="shrink-0 text-[10px] text-[var(--color-tinta-tenue)]">
                  {expandido === contrato.id ? "ocultar" : "ver preimagen"}
                </span>
              </button>
              {expandido === contrato.id ? (
                <div className="aparecer mt-1.5 rounded-lg border border-[var(--color-borde)] bg-black/30 p-3">
                  <Etiqueta>Serializacion canonica (preimagen del hash)</Etiqueta>
                  <pre className="mt-2 overflow-x-auto font-mono text-[10px] leading-relaxed text-[var(--color-tinta-suave)]">
                    {serializacionCanonica(contrato)}
                  </pre>
                  <Etiqueta>SHA-256</Etiqueta>
                  <p className="mt-1 break-all font-mono text-[10px] text-cyan-300/80">
                    {agrupar(hash)}
                  </p>
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      </div>

      <div className="grid gap-3 lg:grid-cols-3">
        <Nota
          titulo="El secuenciador no lee nada"
          cuerpo="Ordena y entrega sobres cifrados extremo a extremo. Conoce el tamano y el destinatario de cada mensaje, nunca su contenido. No puede reconstruir un saldo ni una posicion."
        />
        <Nota
          titulo="El mediador solo agrega firmas"
          cuerpo="Recibe respuestas de confirmacion referidas a hashes de vista, no a los datos. Decide si la transaccion se compromete sin haber visto jamas un importe."
        />
        <Nota
          titulo="Ausencia de mempool publico"
          cuerpo="No existe una cola global de operaciones pendientes. Un competidor no puede ver una orden antes de que se liquide, porque nunca llega a su nodo: el front-running carece de superficie."
        />
      </div>

      <div className="rounded-lg border border-[var(--color-borde)] bg-[var(--color-panel-alto)] p-4">
        <Etiqueta>Nota metodologica</Etiqueta>
        <p className="mt-1.5 text-xs leading-relaxed text-[var(--color-tinta-suave)]">
          En modo demostracion estos hashes se calculan en el navegador con Web Crypto sobre la
          serializacion canonica mostrada arriba: ilustran el mecanismo, no reproducen los hashes
          internos de un nodo Canton. Con la red arrancada, la pestana{" "}
          <strong className="text-[var(--color-tinta)]">Red en vivo</strong> consulta la API JSON de
          los cuatro nodos y muestra identificadores de contrato autenticos, que Canton deriva del
          hash del nodo de creacion.
        </p>
        <p className="mt-2 text-xs leading-relaxed text-[var(--color-tinta-suave)]">
          La perspectiva activa es{" "}
          <strong className="text-[var(--color-tinta)]">
            {INSTITUCIONES[perspectiva].nombre}
          </strong>
          . Cambiela en la cabecera para ver como se reduce o amplia el conjunto de compromisos.
        </p>
      </div>
    </div>
  );
}

function Metrica({
  titulo,
  valor,
  pie,
  acento = "text-[var(--color-tinta)]",
}: {
  titulo: string;
  valor: string;
  pie: string;
  acento?: string;
}) {
  return (
    <div className="rounded-lg border border-[var(--color-borde)] bg-[var(--color-panel-alto)] p-4">
      <Etiqueta>{titulo}</Etiqueta>
      <p className={`mt-1.5 font-mono text-2xl font-semibold tracking-tight ${acento}`}>{valor}</p>
      <p className="mt-1 text-[11px] leading-relaxed text-[var(--color-tinta-tenue)]">{pie}</p>
    </div>
  );
}

function Nota({ titulo, cuerpo }: { titulo: string; cuerpo: string }) {
  return (
    <div className="rounded-lg border border-[var(--color-borde)] bg-[var(--color-panel-alto)] p-4">
      <h3 className="text-xs font-semibold text-[var(--color-tinta)]">{titulo}</h3>
      <p className="mt-1.5 text-[11px] leading-relaxed text-[var(--color-tinta-suave)]">{cuerpo}</p>
    </div>
  );
}
