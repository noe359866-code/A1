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
    fun setApiKeys(keys: Map<String, String>)
}

/**
 * Reglas del panel de vinculación: qué responde a cada petición.
 *
 * Invariante de seguridad: **sin el código de emparejamiento correcto no se
 * lee ni se escribe ningún dato** de [store]. Toda respuesta a un cliente no
 * validado es la página bloqueada de [PairingPortal], que no contiene nada
 * del televisor (ni siquiera el código).
 *
 * @param onEvent aviso legible de lo ocurrido, para mostrarlo en Ajustes.
 */
internal class PairingRouter(
    private val store: PanelStore,
    private val guard: AttemptGuard = AttemptGuard(),
    private val onEvent: (String) -> Unit = {}
) {

    /** Decide la respuesta a una petición. [client] identifica al remitente (su IP). */
    fun route(request: PanelRequest, client: String): PanelResponse {
        val path = request.path
        return when {
            request.method == "GET" && (path == "/" || path == "/index.html") ->
                openPanel(request.query["code"].orEmpty(), client)

            request.method == "GET" && path == "/status" ->
                status(request.query["code"].orEmpty(), client)

            request.method == "POST" && path == "/save" ->
                save(parseForm(request.body), client)

            else -> PanelResponse(404, PairingPortal.html(null, "Página no encontrada", isError = true))
        }
    }

    private enum class CodeCheck { OK, WRONG, LOCKED }

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

    /** Respuesta común cuando el código no vale: página bloqueada, sin datos. */
    private fun denied(check: CodeCheck): PanelResponse = when (check) {
        CodeCheck.LOCKED -> PanelResponse(429, PairingPortal.html(null, MSG_LOCKED, isError = true))
        else -> PanelResponse(403, PairingPortal.html(null, MSG_WRONG_CODE, isError = true))
    }

    private fun openPanel(code: String, client: String): PanelResponse {
        // Sin código: solo el formulario para introducirlo (no cuenta como intento)
        if (code.isBlank()) return PanelResponse(200, PairingPortal.html(null))
        val check = checkCode(code, client)
        if (check != CodeCheck.OK) return denied(check)
        onEvent("Panel web abierto desde $client")
        return PanelResponse(200, PairingPortal.html(store.data()))
    }

    private fun status(code: String, client: String): PanelResponse {
        val jsonType = "application/json; charset=utf-8"
        if (code.isBlank()) return PanelResponse(403, """{"error":"code_required"}""", jsonType)
        return when (checkCode(code, client)) {
            CodeCheck.OK -> {
                val data = store.data()
                PanelResponse(
                    200,
                    """{"account":${jsonString(data.account)},"addons":${data.addons.size},"streams":${data.streamAddons.size},"plugins":${data.nuvioPlugins.size},"apis":${data.apiKeys.size}}""",
                    jsonType
                )
            }
            CodeCheck.LOCKED -> PanelResponse(429, """{"error":"too_many_attempts"}""", jsonType)
            CodeCheck.WRONG -> PanelResponse(403, """{"error":"wrong_code"}""", jsonType)
        }
    }

    private fun save(form: Map<String, String>, client: String): PanelResponse {
        val check = checkCode(form["code"].orEmpty(), client)
        if (check != CodeCheck.OK) return denied(check)

        var rejectedUrls = 0
        form["account"]?.trim()?.takeIf { it.isNotBlank() }?.let { store.setAccount(it) }
        form["addons"]?.let { text ->
            val (accepted, rejected) = splitUrls(text)
            store.setAddons(accepted)
            rejectedUrls += rejected.size
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
                "streams: ${data.streamAddons.size}, plugins: ${data.nuvioPlugins.size}, APIs: ${data.apiKeys.size})"
        )
        val message = buildString {
            append("✅ Guardado. La cuenta, los addons y las APIs ya están en el televisor.")
            if (rejectedUrls > 0) {
                append(" ⚠️ Se descartaron $rejectedUrls URL no válidas: usa https://, o http:// solo ")
                append("con una IP de tu red local (192.168.x.x, 10.x.x.x…).")
            }
        }
        return PanelResponse(200, PairingPortal.html(data, message))
    }

    /** Separa las líneas de un campo de URLs en aceptadas y rechazadas por [NetPolicy]. */
    private fun splitUrls(text: String): Pair<List<String>, List<String>> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.partition { NetPolicy.isAllowed(it) }

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
        const val UNSET_CODE = "------"
        const val MSG_WRONG_CODE = "❌ Código de emparejamiento incorrecto"
        const val MSG_LOCKED = "⏳ Demasiados intentos fallidos. Espera un minuto y vuelve a probar."
    }
}
