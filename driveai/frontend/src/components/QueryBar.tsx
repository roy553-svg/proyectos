import { useState } from 'react';
import { Send } from 'lucide-react';

/** Sugerencias de 1 toque: cero escritura mientras el coche rueda. */
const SUGERENCIAS = [
  '¿Por qué el cielo es azul?',
  'Consejos para conducir con lluvia',
  '¿Cómo están mis llantas?',
  '¿Cuánta autonomía me queda?',
];

/**
 * Barra de consultas tactil. Alternativa al orbe cuando hay ruido en cabina
 * o el copiloto escribe por el conductor.
 */
export function QueryBar({
  onAsk,
  disabled,
}: {
  onAsk: (text: string) => void;
  disabled: boolean;
}) {
  const [text, setText] = useState('');

  const submit = (value: string) => {
    const clean = value.trim();
    if (!clean || disabled) return;
    onAsk(clean);
    setText('');
  };

  return (
    <div className="flex flex-col gap-3">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit(text);
        }}
        className="flex gap-3"
      >
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Pregunta lo que sea..."
          enterKeyHint="send"
          autoComplete="off"
          className="min-h-touch flex-1 select-text rounded-2xl border-2 border-cockpit-line
                     bg-cockpit-panel px-5 text-xl text-zinc-100 placeholder:text-zinc-600
                     outline-none focus:border-cockpit-info"
        />
        <button type="submit" disabled={disabled || !text.trim()} className="btn-info px-8">
          <Send className="h-6 w-6" aria-hidden />
          PREGUNTAR
        </button>
      </form>

      <div className="flex gap-2 overflow-x-auto pb-1">
        {SUGERENCIAS.map((s) => (
          <button
            key={s}
            type="button"
            disabled={disabled}
            onClick={() => submit(s)}
            className="min-h-touch shrink-0 rounded-2xl border border-cockpit-line
                       bg-zinc-900/70 px-5 text-base font-medium text-zinc-300
                       active:scale-[0.97] disabled:opacity-40"
          >
            {s}
          </button>
        ))}
      </div>
    </div>
  );
}
