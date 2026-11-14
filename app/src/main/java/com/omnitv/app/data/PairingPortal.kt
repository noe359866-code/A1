package com.omnitv.app.data

/** Datos que el panel muestra a quien ya presentó el código correcto. */
internal class PanelData(
    val code: String,
    val account: String,
    val addons: List<String>,
    val streamAddons: List<String>,
    val nuvioPlugins: List<String>,
    val nuvioBridge: String,
    val apiKeys: Map<String, String>,
    val secretsEncrypted: Boolean
)

/**
 * HTML del panel de vinculación (lo que ve el móvil).
 *
 * Tiene dos caras y **solo la segunda lleva datos**:
 * - [html] con `data == null` → página bloqueada: únicamente pide el código.
 *   No contiene el código, ni la cuenta, ni los addons, ni las claves.
 * - [html] con datos → formulario completo, para quien ya acertó el código.
 */
internal object PairingPortal {

    fun html(data: PanelData?, message: String = "", isError: Boolean = false): String {
        val notice = if (message.isEmpty()) "" else
            """<div class="msg${if (isError) " err" else ""}">${esc(message)}</div>"""
        val content = if (data == null) lockedForm() else settingsForm(data)
        val subtitle = if (data == null) {
            "Introduce el código que aparece en tu televisor"
        } else {
            "Conectado con tu televisor"
        }

        return """
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="no-referrer">
<title>Omni TV · Vinculación</title>
<style>
  body { margin:0; font-family:system-ui,sans-serif; background:#0B0B0F; color:#F5F5F7; }
  .wrap { max-width:640px; margin:0 auto; padding:24px 18px 60px; }
  h1 { font-size:22px; margin:0 0 4px; } h1 span { color:#0A84FF; }
  .sub { color:#8E8E93; font-size:13px; margin:0 0 22px; }
  .card { background:#16161C; border:1px solid rgba(255,255,255,.08); border-radius:14px; padding:18px; margin:14px 0; }
  .card h2 { font-size:15px; margin:0 0 12px; }
  label { display:block; font-size:12px; color:#8E8E93; margin:10px 0 4px; }
  input, textarea { width:100%; box-sizing:border-box; background:#0F0F14; color:#F5F5F7;
    border:1px solid rgba(255,255,255,.14); border-radius:10px; padding:12px; font-size:15px; }
  textarea { min-height:96px; font-family:ui-monospace,monospace; font-size:13px; }
  button { width:100%; margin-top:18px; background:#0A84FF; color:#fff; border:0; border-radius:12px;
    padding:15px; font-size:16px; font-weight:700; }
  .ok { color:#30D158; font-size:13px; } .msg { background:#1F1F26; border-radius:10px; padding:12px; font-size:14px; margin:12px 0; }
  .msg.err { background:#3A1518; color:#FF9F9A; }
  .hint { color:#6E6E73; font-size:11px; margin-top:6px; }
</style>
</head>
<body>
<div class="wrap">
  <h1>Omni <span>TV</span> · Panel de vinculación</h1>
  <p class="sub">$subtitle</p>
  $notice
  $content
</div>
</body>
</html>
        """.trimIndent()
    }

    /** Página bloqueada: solo el campo del código. Sin ningún dato del televisor. */
    private fun lockedForm(): String = """
  <form method="GET" action="/">
    <div class="card">
      <h2>🔒 Código de emparejamiento</h2>
      <label>Está en el televisor: Ajustes → Vincular
        <input name="code" placeholder="6 caracteres" autocomplete="off" autocapitalize="characters"
          maxlength="12" required>
      </label>
      <p class="hint">También puedes escanear el QR del televisor: abre el panel ya desbloqueado.</p>
    </div>
    <button type="submit">Entrar</button>
  </form>"""

    /** Formulario completo (solo tras validar el código). */
    private fun settingsForm(data: PanelData): String {
        val addonsValue = esc(data.addons.joinToString("\n"))
        val streamsValue = esc(data.streamAddons.joinToString("\n"))
        val pluginsValue = esc(data.nuvioPlugins.joinToString("\n"))
        val apisValue = esc(data.apiKeys.entries.joinToString("\n") { "${it.key}=${it.value}" })
        val storageHint = if (data.secretsEncrypted) {
            "Se guardan cifradas en el televisor (Android Keystore) y no se incluyen en copias de seguridad."
        } else {
            "Se guardan solo en el televisor, <strong>sin cifrar</strong>: este dispositivo " +
                "no admite el cifrado del sistema (requiere Android 6.0 o posterior)."
        }

        return """
  <form method="POST" action="/save">
    <input type="hidden" name="code" value="${esc(data.code)}">
    <p class="ok">Código verificado ✓</p>
    <div class="card">
      <h2>👤 Enlazar cuenta</h2>
      <label>Email de tu cuenta Omni TV
        <input name="account" type="email" placeholder="tu@email.com" value="${esc(data.account)}">
      </label>
    </div>
    <div class="card">
      <h2>🧩 Addons Stremio</h2>
      <label>URLs de manifest.json (una por línea)
        <textarea name="addons" placeholder="https://v3-cinemeta.strem.io/manifest.json">$addonsValue</textarea>
      </label>
      <p class="hint">URL del manifest, conservando su configuración. Se comprobarán resources/types/idPrefixes: catálogo y fichas en Inicio; streams HTTP al reproducir. Un índice Nuvio con scrapers[] va en el campo de abajo.</p>
    </div>
    <div class="card">
      <h2>🎬 Proyectos de streams (URL base)</h2>
      <label>Proyectos que entregan enlaces de reproducción (una URL por línea)
        <textarea name="streamAddons" placeholder="https://tu-proyecto.ejemplo">$streamsValue</textarea>
      </label>
      <p class="hint">El addon interno «Omni Streams» les pide los streams con el protocolo
        <code>stream/{tipo}/{id}</code> (Stremio). Compatible con proyectos tipo
        proyectos que expongan esa API. Los addons Stremio se instalan arriba por su manifest, no aquí.</p>
      <p class="hint">Se admite <code>https://</code> siempre, y <code>http://</code> solo si es una IP de tu
        red local (p. ej. <code>http://192.168.1.20:8787</code>, el puente en tu PC o móvil).</p>
    </div>
    <div class="card">
      <h2>🔌 Plugins Nuvio · repositorios JS</h2>
      <label>URLs de manifest con scrapers[] (una por línea)
        <textarea name="nuvioPlugins" placeholder="https://raw.githubusercontent.com/…/manifest.json">$pluginsValue</textarea>
      </label>
      <p class="hint">No son addons Stremio ni catálogos. Se listan en el televisor y solo se ejecutan mediante el puente JS de abajo.</p>
      <label>Puente JS (URL base; vacío = desconectado)
        <input name="nuvioBridge" type="url" placeholder="http://192.168.1.20:8788" value="${esc(data.nuvioBridge)}">
      </label>
      <p class="hint">El puente debe implementar POST /scrape/{id} y GET /health. Omni TV no ejecuta los JS en el televisor.
        Guarda solo repositorios de confianza: el equipo del puente ejecutará su código.</p>
    </div>
    <div class="card">
      <h2>🔑 Añadir APIs</h2>
      <label>Una por línea con el formato NOMBRE=clave
        <textarea name="apis" placeholder="TMDB=tu_api_key&#10;TRAKT=tu_api_key">$apisValue</textarea>
      </label>
      <p class="hint">$storageHint</p>
    </div>
    <button type="submit">Guardar en el televisor</button>
  </form>"""
    }

    /** Escapa texto para HTML (contenido y atributos entre comillas dobles). */
    internal fun esc(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}
