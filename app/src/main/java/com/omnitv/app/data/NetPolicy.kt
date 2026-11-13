package com.omnitv.app.data

import java.net.URI

/**
 * Política de red de Omni TV: qué URLs externas acepta la app.
 *
 * - `https://` → siempre.
 * - `http://` (sin cifrar) → **solo en la red local**: IP privada
 *   (10.x, 172.16–31.x, 192.168.x), enlace local (169.254.x), loopback,
 *   `localhost`, nombres mDNS `*.local` y sus equivalentes IPv6
 *   (`::1`, `fc00::/7`, `fe80::/10`).
 *
 * El manifiesto habilita el tráfico sin cifrar a nivel de plataforma porque
 * `network_security_config` no sabe expresar rangos de IP; **esta clase es
 * la que impone el límite**. Se aplica a los addons añadidos desde el panel
 * y a los enlaces de reproducción que entregan los addons de streams (el
 * puente `tools/omni-stream-bridge` se sirve en `http://<ip-lan>:8787`).
 *
 * Solo se miran IP literales: nunca se resuelve DNS (un nombre público no
 * puede colarse apuntando a una IP privada).
 */
object NetPolicy {

    /** La URL es aceptable según la política (esquema, host y sin credenciales). */
    fun isAllowed(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return false
        // Sin «usuario:clave@host»: evita URLs que aparentan otro destino
        if (uri.userInfo != null) return false
        return when (uri.scheme?.lowercase()) {
            "https" -> true
            "http" -> isLocalHost(host)
            else -> false
        }
    }

    /** El host pertenece a la red local (ver cabecera de la clase). */
    fun isLocalHost(host: String): Boolean {
        val h = host.trim().lowercase().trimEnd('.')
        if (h.isEmpty()) return false
        if (h == "localhost" || h.endsWith(".local")) return true
        if (h.startsWith("[") && h.endsWith("]")) return isLocalIpv6(h.substring(1, h.length - 1))
        if (':' in h) return isLocalIpv6(h)
        return isPrivateIpv4(h)
    }

    /** IPv4 literal en formato decimal estricto `a.b.c.d` y de rango privado. */
    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { part ->
            // Solo decimal canónico: sin vacíos, signos, hex ni ceros a la izquierda
            if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return false
            if (part.length > 1 && part[0] == '0') return false
            part.toInt().also { if (it > 255) return false }
        }
        val (a, b) = octets
        return when (a) {
            10, 127 -> true
            172 -> b in 16..31
            192 -> b == 168
            169 -> b == 254
            else -> false
        }
    }

    /** IPv6 literal: loopback, local única (`fc00::/7`) o enlace local (`fe80::/10`). */
    private fun isLocalIpv6(literal: String): Boolean {
        val address = literal.substringBefore('%') // quita la zona («%eth0»)
        if (address.isEmpty() || !address.all { it == ':' || it in '0'..'9' || it in 'a'..'f' }) return false
        if (address == "::1") return true
        val first = address.substringBefore(':')
        if (first.length != 4) return false
        return first.startsWith("fc") || first.startsWith("fd") ||
            first.startsWith("fe8") || first.startsWith("fe9") ||
            first.startsWith("fea") || first.startsWith("feb")
    }
}
