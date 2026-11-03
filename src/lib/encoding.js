/**
 * Base64url UTF-8 y hex usando únicamente APIs web estándar.
 *
 * La versión original usaba la API Buffer de Node, que los Workers no exponen
 * salvo que se active `nodejs_compat`. Estas funciones son equivalentes byte a
 * byte y funcionan igual en Node 18+, workerd y navegador.
 */

const encoder = new TextEncoder();
const decoder = new TextDecoder();

/** Texto UTF-8 -> base64url sin padding (igual que el codificador de Node). */
export function encodeBase64Url(text) {
  const bytes = encoder.encode(String(text));
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

/**
 * base64url -> texto UTF-8. Tolera caracteres extraños y padding ausente,
 * igual que el decodificador de Node: si algo no es decodificable devuelve ''.
 */
export function decodeBase64Url(value) {
  try {
    const normalized = String(value)
      .replace(/[^A-Za-z0-9+/=_-]/g, '')
      .replace(/-/g, '+')
      .replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    const binary = atob(padded);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return decoder.decode(bytes);
  } catch {
    return '';
  }
}

/** Array de bytes -> hexadecimal en minúsculas. */
export function bytesToHex(bytes) {
  let hex = '';
  for (const byte of bytes) hex += byte.toString(16).padStart(2, '0');
  return hex;
}
