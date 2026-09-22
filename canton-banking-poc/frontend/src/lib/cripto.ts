import type { Contrato } from "./tipos";

/**
 * Utilidades criptograficas de la demo.
 *
 * Los hashes se calculan de verdad en el navegador con Web Crypto (SHA-256)
 * sobre la serializacion canonica del contrato. Ilustran el mecanismo de
 * compromiso de Canton -un participante puede probar QUE dato tenia sin
 * revelarlo-, pero no son los hashes internos de un nodo Canton real: para
 * eso esta el modo "red en vivo", que muestra los identificadores de contrato
 * autenticos que devuelve la API JSON.
 */

/** Serializacion canonica: claves ordenadas, sin espacios superfluos. */
export function serializacionCanonica(contrato: Contrato): string {
  const campos = Object.keys(contrato.campos)
    .sort()
    .map((k) => `${JSON.stringify(k)}:${JSON.stringify(contrato.campos[k])}`)
    .join(",");
  const signatarios = [...contrato.signatarios].sort();
  const observadores = [...contrato.observadores].sort();
  return `{"template":${JSON.stringify(contrato.plantilla)},"signatories":${JSON.stringify(
    signatarios,
  )},"observers":${JSON.stringify(observadores)},"payload":{${campos}}}`;
}

export async function sha256Hex(texto: string): Promise<string> {
  const datos = new TextEncoder().encode(texto);
  const resumen = await crypto.subtle.digest("SHA-256", datos);
  return [...new Uint8Array(resumen)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Hash de compromiso de un contrato. */
export const compromisoDe = (contrato: Contrato): Promise<string> =>
  sha256Hex(serializacionCanonica(contrato));

/**
 * Bloque de bytes con aspecto de texto cifrado, derivado de forma
 * determinista de una semilla. Sirve para representar visualmente un dato que
 * este nodo NO posee; en Canton el nodo ni siquiera recibe el sobre.
 */
export function bloqueOpaco(semilla: string, longitud = 96): string {
  const alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  let h1 = 0x811c9dc5;
  let h2 = 0x01000193;
  for (let i = 0; i < semilla.length; i++) {
    h1 = (h1 ^ semilla.charCodeAt(i)) >>> 0;
    h1 = Math.imul(h1, 0x01000193) >>> 0;
    h2 = (h2 + Math.imul(semilla.charCodeAt(i) + i, 0x85ebca6b)) >>> 0;
  }
  let salida = "";
  for (let i = 0; i < longitud; i++) {
    h1 = (Math.imul(h1 ^ (h1 >>> 15), 0x2545f491) + h2) >>> 0;
    h2 = (Math.imul(h2 ^ (h2 >>> 13), 0x27d4eb2f) + i) >>> 0;
    // `>>> 0` es imprescindible: el XOR devuelve un entero con signo y un
    // indice negativo daria `undefined`.
    salida += alfabeto[((h1 ^ h2) >>> 0) % alfabeto.length];
  }
  return salida;
}

/** Trocea una cadena larga en grupos legibles. */
export const agrupar = (texto: string, tam = 8): string =>
  (texto.match(new RegExp(`.{1,${tam}}`, "g")) ?? []).join(" ");
