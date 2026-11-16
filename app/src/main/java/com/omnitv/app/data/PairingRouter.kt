package com.omnitv.app.data

import java.security.MessageDigest

/** Lo que el panel necesita de los ajustes del televisor. */
internal interface PanelStore {
    /** Código de emparejamiento vigente. */
    val pairingCode: String

    /** Datos que se muestran en el formulario (solo tras validar el código). */
    fun data(): PanelData

    fun setAccount(email: String)
    fun setAddons(urls: List<String>)
    fun setStreamAddons(urls: List<String>)
    fun setNuvioPlugins(urls: List<String>)
    fun setNuvioBridge(url: String)
    fun setCloudstreamBridge(url: String)
    fun setFfmpegBridge(url: String)
    fun setApiKeys(keys: Map<String, String>)
}

/**
 * Reglas del panel de vinculación: qué responde a cada petición.
 *
 * Invariante de seguridad: **sin el código correcto o una sesión vigente no
 * se lee ni se escribe ningún dato** de [store]. El QR lleva el código en
 * `/?code=…`; la respuesta es un 303 a `/` con cookie de sesión [SessionGuard]
 * ligada a la IP y al código vigente (el código no sobrevive en la URL);
 * quien abre la URL a mano lo teclea una vez en el formulario (`POST /unlock`).
 * Rotar el código en Ajustes invalida todas las sesiones al instante.
 *
 * @param onEvent aviso legible de lo ocurrido, para mostrarlo en Ajustes.
 */
internal class PairingRouter(
    private val store: PanelStore,
    private val guard: AttemptGuard = AttemptGuard(),
    private val onEvent: (String) -> Unit = {},
    private val sessions: SessionGuard = SessionGuard()
) {

    /** Decide la respuesta a una petición. [client] identifica al remitente (su IP). */
    fun route(request: PanelRequest, client: String): PanelResponse {
        val path = request.path
        val token = cookie(request, SESSION_COOKIE)
        val currentCode = store.pairingCode
        return when {
            request.method == "GET" && (path == "/" || path == "/index.html") ->
                openPanel(request.query["code"].orEmpty(), token, client, currentCode)

            request.method == "POST" && path == "/unlock" ->
                unlock(parseForm(request.body), token, client, currentCode, request)

            request.method == "GET" && path == "/status" ->
                status(request.query["code"].orEmpty(), token, client, currentCode)

            request.method == "POST" && path == "/save" ->
                save(parseForm(request.body), token, client, currentCode, request)

            else -> PanelResponse(404, PairingPortal.html(null, "Página no encontrada", isError = true))
        }
    }

    private enum class CodeCheck { OK, WRONG, LOCKED }
    private enum class Auth { OK, NO_CODE, WRONG, LOCKED }

    /** Valida el código de emparejamiento aplicando el límite de intentos. */
    private fun checkCode(code: String, client: String): CodeCheck {
        if (guard.isLocked(client)) return CodeCheck.LOCKED
        val expected = store.pairingCode
        // Comparación en tiempo constante; un código sin inicializar no abre nada
        val matches = expected.isNotBlank() && expected != UNSET_CODE && MessageDigest.isEqual(
            code.trim().uppercase().toByteArray(Charsets.UTF_8),
            expected.uppercase().toByteArray(Charsets.UTF_8)
        )
        return if (matches) {
            guard.recordSuccess(client)
            CodeCheck.OK
        } else {
            guard.recordFailure(client)
            onEvent("⚠️ Código incorrecto desde $client")
            CodeCheck.WRONG
        }
    }

    /** Sesión vigente → acceso; si no, se valida el código (o se pide). */
    private fun authorize(
        code: String,
        token: String?,
        client: String,
        currentCode: String,
        allowBlank: Boolean
    ): Auth {
        if (sessions.valid(token, client, currentCode)) return Auth.OK
        if (code.isBlank() && allowBlank) return Auth.NO_CODE
        return when (checkCode(code, client)) {
            CodeCheck.OK -> Auth.OK
            CodeCheck.WRONG -> Auth.WRONG
            CodeCheck.LOCKED -> Auth.LOCKED
        }
    }

    /** Respuesta común cuando el código no vale: página bloqueada, sin datos. */
    private fun denied(check: Auth): PanelResponse = when (check) {
        Auth.LOCKED -> PanelResponse(429, PairingPortal.html(null, MSG_LOCKED, isError = true))
        else -> PanelResponse(403, PairingPortal.html(null, MSG_WRONG_CODE, isError = true))
    }

    /**
     * 303 a `/` con la cookie de sesión: el navegador aterriza en una URL
     * limpia (el código del QR/formulario no queda en el historial) y la
     * sesión HttpOnly hace el resto. Cuerpo vacío a propósito.
     */
    private fun redirectToPanel(client: String, currentCode: String): PanelResponse =
        PanelResponse(
            303,
            "",
            extraHeaders = listOf("Location: /") + sessionHeaders(client, currentCode)
        )

    /**
     * Renueva la sesión en cada respuesta autenticada: el móvil no tiene que
     * volver a escribir el código mientras siga usando el panel (TTL deslizante).
     */
    private fun sessionHeaders(client: String, currentCode: String): List<String> =
        listOf(setCookie(sessions.issue(client, currentCode)))

    private fun openPanel(code: String, token: String?, client: String, currentCode: String): PanelResponse {
        when (authorize(code, token, client, currentCode, allowBlank = true)) {
            // Sin código ni sesión: solo el formulario para introducirlo.
            Auth.NO_CODE -> return PanelResponse(200, PairingPortal.html(null))
            Auth.WRONG -> return denied(Auth.WRONG)
            Auth.LOCKED -> return denied(Auth.LOCKED)
            Auth.OK -> Unit
        }
        if (code.isNotBlank()) {
            onEvent("Panel web abierto desde $client")
            return redirectToPanel(client, currentCode)
        }
        return PanelResponse(
            200,
            PairingPortal.html(store.data()),
            extraHeaders = sessionHeaders(client, currentCode)
        )
    }

    /**
     * Camino manual: quien abre el panel a mano teclea el código en el
     * formulario; el POST canjea el código por una sesión (PRG → 303 a `/`),
     * así el secreto no queda en el historial ni se reenvía al refrescar.
     * El camino principal desde el QR entra por `GET /?code=…`.
     */
    private fun unlock(
        form: Map<String, String>,
        token: String?,
        client: String,
        currentCode: String,
        request: PanelRequest
    ): PanelResponse {
        if (isCrossOrigin(request)) {
            onEvent("⚠️ /unlock rechazado por origen cruzado desde $client")
            return PanelResponse(403, PairingPortal.html(null, MSG_CROSS_ORIGIN, isError = true))
        }
        val code = form["code"].orEmpty()
        return when (authorize(code, token, client, currentCode, allowBlank = false)) {
            Auth.OK -> {
                onEvent("Panel web desbloqueado desde $client")
                // 303 a `/`: el código no queda en la URL ni se reenvía al refrescar
                redirectToPanel(client, currentCode)
            }
            Auth.LOCKED -> denied(Auth.LOCKED)
            else -> denied(Auth.WRONG)
        }
    }

    private fun status(code: String, token: String?, client: String, currentCode: String): PanelResponse {
        val jsonType = "application/json; charset=utf-8"
        return when (authorize(code, token, client, currentCode, allowBlank = false)) {
            Auth.NO_CODE -> PanelResponse(403, """{"error":"code_required"}""", jsonType)
            Auth.OK -> {
                val data = store.data()
                PanelResponse(
                    200,
                    """{"account":${jsonString(data.account)},"addons":${data.addons.size},"streams":${data.streamAddons.size},"plugins":${data.nuvioPlugins.size},"cloudstream":${if (data.cloudstreamBridge.isBlank()) 0 else 1},"ffmpeg":${if (data.ffmpegBridge.isBlank()) 0 else 1},"apis":${data.apiKeys.size}}""",
                    jsonType,
                    sessionHeaders(client, currentCode)
                )
            }
            Auth.LOCKED -> PanelResponse(429, """{"error":"too_many_attempts"}""", jsonType)
            Auth.WRONG -> PanelResponse(403, """{"error":"wrong_code"}""", jsonType)
        }
    }

    private fun save(
        form: Map<String, String>,
        token: String?,
        client: String,
        currentCode: String,
        request: PanelRequest
    ): PanelResponse {
        if (isCrossOrigin(request)) {
            onEvent("⚠️ POST /save rechazado por origen cruzado desde $client")
            return PanelResponse(403, PairingPortal.html(null, MSG_CROSS_ORIGIN, isError = true))
        }
        when (authorize(form["code"].orEmpty(), token, client, currentCode, allowBlank = false)) {
            Auth.OK -> Unit
            Auth.LOCKED -> return denied(Auth.LOCKED)
            else -> return denied(Auth.WRONG)
        }

        var rejectedUrls = 0
        form["account"]?.trim()?.takeIf { it.isNotBlank() }?.let { store.setAccount(it) }
        form["addons"]?.let { text ->
            val values = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val accepted = values.mapNotNull(StremioUrl::normalize).distinct()
            store.setAddons(accepted)
            // Las duplicadas simplemente se omiten; solo cuentan las rechazadas de verdad
            rejectedUrls += values.size - accepted.size
        }
        form["streamAddons"]?.let { text ->
            val (accepted, rejected) = splitUrls(text)
            store.setStreamAddons(accepted)
            rejectedUrls += rejected.size
        }
        form["nuvioPlugins"]?.let { text ->
            val (accepted, rejected) = splitUrls(text)
            store.setNuvioPlugins(accepted)
            rejectedUrls += rejected.size
        }
        form["nuvioBridge"]?.let { text ->
            val value = text.trim()
            val valid = runCatching { val uri = java.net.URI(value); uri.rawQuery == null && uri.rawFragment == null }.getOrDefault(false)
            if (value.isEmpty() || (NetPolicy.isAllowed(value) && valid)) store.setNuvioBridge(value)
            else rejectedUrls++
        }
        form["cloudstreamBridge"]?.let { text ->
            val value = text.trim()
            if (value.isEmpty() || CloudstreamBridge.normalize(value) != null) store.setCloudstreamBridge(value)
            else rejectedUrls++
        }
        form["ffmpegBridge"]?.let { text ->
            val value = text.trim()
            if (value.isEmpty() || MediaBridge.normalize(value) != null) store.setFfmpegBridge(value)
            else rejectedUrls++
        }
        form["apis"]?.let { text ->
            val keys = text.lines()
                .map { it.trim() }
                .filter { '=' in it }
                .associate {
                    val (k, v) = it.split('=', limit = 2)
                    k.trim().uppercase() to v.trim()
                }
            store.setApiKeys(keys)
        }

        val data = store.data()
        onEvent(
            "✅ Configuración guardada desde el panel web (addons: ${data.addons.size}, " +
                "streams: ${data.streamAddons.size}, repos Nuvio: ${data.nuvioPlugins.size}, APIs: ${data.apiKeys.size})"
        )
        val message = buildString {
            append("✅ Guardado en el televisor. Los manifests y repositorios se comprobarán al abrir Inicio/Ajustes.")
            if (rejectedUrls > 0) {
                append(" ⚠️ Se descartaron $rejectedUrls URL no válidas. Recuerda: https:// siempre; http:// solo en tu red local; ")
                append("y en los addons Stremio, la configuración va en la ruta (…/clave/manifest.json), no en «?parámetros» ni «#fragmento».")
            }
        }
        return PanelResponse(
            200,
            PairingPortal.html(data, message),
            extraHeaders = sessionHeaders(client, currentCode)
        )
    }

    /** Separa las líneas de un campo de URLs en aceptadas y rechazadas por [NetPolicy]. */
    private fun splitUrls(text: String): Pair<List<String>, List<String>> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.partition { NetPolicy.isAllowed(it) }

    /**
     * Mitigación CSRF: un formulario de otra web siempre anuncia un `Origin`
     * distinto (o `Sec-Fetch-Site: cross-site`). Con `SameSite=Strict` la
     * cookie de sesión tampoco se envía desde otro sitio. Los clientes sin
     * esas cabeceras (curl, apps propias) siguen pudiendo guardar con el código.
     */
    private fun isCrossOrigin(request: PanelRequest): Boolean {
        val site = header(request, "sec-fetch-site")?.trim()?.lowercase()
        // Chromium puede clasificar una petición desde una IP local como
        // same-site aunque el panel esté en otro puerto/origen. No se debe
        // bloquear por esa etiqueta: en ese caso exigimos Origin y lo
        // comparamos exactamente con Host más abajo.
        if (site == "cross-site") return true
        if (site == "same-site" && header(request, "origin").isNullOrBlank()) return true
        if (site != null && site !in setOf("same-origin", "same-site", "none")) return true
        val origin = header(request, "origin")?.trim().orEmpty()
        if (origin.isEmpty()) return false
        if (origin.equals("null", true)) return true
        val uri = runCatching { java.net.URI(origin) }.getOrNull() ?: return true
        if (!uri.scheme.equals("http", true)) return true // el panel se sirve por HTTP en la LAN
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return true
        val authority = host + if (uri.port >= 0) ":${uri.port}" else ""
        val expected = header(request, "host")?.trim().orEmpty()
        return expected.isNotBlank() && !authority.equals(expected, ignoreCase = true)
    }

    /** `readPanelRequest` normaliza a minúsculas; aceptar cualquier caso evita sorpresas. */
    private fun header(request: PanelRequest, name: String): String? =
        request.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private fun cookie(request: PanelRequest, name: String): String? {
        val raw = header(request, "cookie") ?: return null
        for (part in raw.split(';')) {
            val idx = part.indexOf('=')
            if (idx > 0 && part.substring(0, idx).trim().equals(name, ignoreCase = true)) {
                return part.substring(idx + 1).trim().takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun setCookie(token: String): String =
        "Set-Cookie: $SESSION_COOKIE=$token; Path=/; HttpOnly; SameSite=Strict; Max-Age=600"

    private fun jsonString(value: String): String = buildString {
        append('"')
        for (c in value) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c < ' ' -> append(String.format("\\u%04x", c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    private companion object {
        const val SESSION_COOKIE = "omnitv_session"
        const val UNSET_CODE = "------"
        const val MSG_WRONG_CODE = "❌ Código de emparejamiento incorrecto"
        const val MSG_LOCKED = "⏳ Demasiados intentos fallidos. Espera un minuto y vuelve a probar."
        const val MSG_CROSS_ORIGIN = "❌ Petición rechazada: abre el panel desde el QR del televisor"
    }
}
