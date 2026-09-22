/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Origen del gateway. Vacio = mismo host (proxy de Vite o WebView nativo). */
  readonly VITE_API_BASE?: string;
  /** Identidad del conductor para el aislamiento de memoria. */
  readonly VITE_DRIVER_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
