/**
 * Capa de voz del cliente delgado.
 *
 * Reconocimiento y sintesis se resuelven con las APIs del WebView
 * (Android 9+ / Chromium 66+) o, en la app nativa, con el puente
 * `window.DriveAINative` que expone VoiceRecorder.kt. Cero dependencias
 * externas: no sumamos megabytes al presupuesto de RAM de la radio.
 */

declare global {
  interface Window {
    webkitSpeechRecognition?: any;
    SpeechRecognition?: any;
    /** Puente inyectado por MainActivity.kt (addJavascriptInterface). */
    DriveAINative?: {
      startRecording?: () => void;
      stopRecording?: () => void;
      speak?: (text: string) => void;
      isNative?: () => boolean;
    };
  }
}

export const isNativeBridge = (): boolean =>
  Boolean(window.DriveAINative?.isNative?.());

export interface Listener {
  start: () => void;
  stop: () => void;
  supported: boolean;
}

/** Dictado continuo con corte automatico al terminar la frase. */
export function createListener(
  onResult: (text: string) => void,
  onEnd: () => void,
  onError?: (reason: string) => void,
): Listener {
  const Ctor = window.SpeechRecognition ?? window.webkitSpeechRecognition;
  if (!Ctor) {
    return { start: () => onError?.('sin-reconocimiento'), stop: () => undefined, supported: false };
  }
  const rec = new Ctor();
  rec.lang = 'es-MX';
  rec.interimResults = false;
  rec.maxAlternatives = 1;
  rec.continuous = false;

  rec.onresult = (e: any) => {
    const text = e.results?.[0]?.[0]?.transcript ?? '';
    if (text.trim()) onResult(text.trim());
  };
  rec.onerror = (e: any) => onError?.(e?.error ?? 'error');
  rec.onend = () => onEnd();

  return {
    supported: true,
    start: () => {
      try {
        rec.start();
      } catch {
        /* ya estaba escuchando */
      }
    },
    stop: () => {
      try {
        rec.stop();
      } catch {
        /* no estaba escuchando */
      }
    },
  };
}

/** TTS en espanol. Devuelve una promesa que resuelve al callar. */
export function speak(text: string): Promise<void> {
  if (window.DriveAINative?.speak) {
    window.DriveAINative.speak(text);
    // El TTS nativo estima ~14 caracteres por segundo en es-MX.
    return new Promise((r) => setTimeout(r, Math.min(12_000, text.length * 70)));
  }
  if (!('speechSynthesis' in window)) return Promise.resolve();

  return new Promise((resolve) => {
    window.speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    u.lang = 'es-MX';
    u.rate = 1.05; // ligeramente rapido: menos segundos de distraccion
    u.pitch = 1;
    u.onend = () => resolve();
    u.onerror = () => resolve();
    window.speechSynthesis.speak(u);
    // Red de seguridad: algunos WebViews nunca disparan onend.
    setTimeout(resolve, Math.min(14_000, text.length * 90));
  });
}

export function shutUp(): void {
  if ('speechSynthesis' in window) window.speechSynthesis.cancel();
}
