package com.omnitv.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.Inet6Address

/**
 * DNS de Cloudflare (DoH). Se prueba sin red: la consulta HTTP se inyecta, así
 * que aquí solo se comprueba lo que sí es nuestro: el parseo de la respuesta,
 * la validación de las IP, la caché con TTL y las reglas de seguridad.
 */
class DohTest {

    /** Respuesta con la forma real de `cloudflare-dns.com/dns-query?type=A`. */
    private fun answer(status: Int = 0, ttl: Int = 300, vararg records: Pair<Int, String>): String {
        val answers = records.joinToString(",") { (type, data) ->
            """{"name":"ejemplo.com","type":$type,"TTL":$ttl,"data":"$data"}"""
        }
        return """{"Status":$status,"TC":false,"RD":true,"RA":true,"AD":false,"CD":false,"Question":[{"name":"ejemplo.com","type":1}],"Answer":[$answers]}"""
    }

    // ------------------------- Lectura de la respuesta -------------------------

    @Test
    fun `lee las direcciones A y AAAA`() {
        val addresses = Doh.resolve("addon.ejemplo") { url ->
            when {
                url.contains("type=1&") || url.endsWith("type=1") -> answer(
                    0, 120, Doh.TYPE_A to "192.0.2.10", Doh.TYPE_A to "192.0.2.11"
                )
                else -> answer(0, 120, 5 to "addon.ejemplo.", Doh.TYPE_AAAA to "2001:db8::1")
            }
        }
        assertEquals(3, addresses.size)
        assertTrue(addresses.take(2).all { it is Inet4Address })
        assertTrue(addresses[2] is Inet6Address)
        assertEquals("192.0.2.10", addresses[0].hostAddress)
    }

    @Test
    fun `las respuestas con error del DNS no dan direcciones`() {
        // Status 3 = NXDOMAIN; también un JSON sin Answer
        assertTrue(Doh.resolve("noexiste.ejemplo") { answer(3, 60) }.isEmpty())
        assertTrue(Doh.resolve("noexiste.ejemplo") { """{"Status":0,"Answer":[]}""" }.isEmpty())
        assertTrue(Doh.resolve("noexiste.ejemplo") { "" }.isEmpty())
        assertTrue(Doh.resolve("noexiste.ejemplo") { "no es json" }.isEmpty())
        assertTrue(Doh.resolve("noexiste.ejemplo") { null }.isEmpty())
    }

    @Test
    fun `un CNAME o una basura no se toman por direccion`() {
        val body = answer(
            0, 60,
            5 to "otro.ejemplo.",                       // CNAME
            Doh.TYPE_A to "no-soy-una-ip",              // texto
            Doh.TYPE_A to "1.2.3.4/24",                 // con máscara
            Doh.TYPE_A to "evil.ejemplo",               // nombre, no IP
            Doh.TYPE_A to "198.51.100.7"                // la única válida
        )
        val addresses = Doh.resolve("ejemplo.com") { body }
        assertEquals(listOf("198.51.100.7"), addresses.map { it.hostAddress })
    }

    @Test
    fun `la URL de la consulta va escapada y con el tipo`() {
        assertEquals(
            "https://cloudflare-dns.com/dns-query?name=addon.ejemplo&type=1",
            Doh.queryUrl("addon.ejemplo", Doh.TYPE_A)
        )
        assertEquals(
            "https://cloudflare-dns.com/dns-query?name=mal%20nombre&type=28",
            Doh.queryUrl("mal nombre", Doh.TYPE_AAAA)
        )
    }

    // ------------------------- Caché y TTL -------------------------

    @Test
    fun `la cache respeta el TTL y evita consultas repetidas`() {
        var clock = 0L
        var calls = 0
        val fetch: (String) -> String? = { calls++; answer(0, ttl = 120, Doh.TYPE_A to "192.0.2.30") }

        Doh.clearCache()
        val first = Doh.cached("cache.ejemplo", now = { clock }, fetch = fetch)
        val second = Doh.cached("cache.ejemplo", now = { clock += 60_000; clock }, fetch = fetch)
        assertEquals(first.map { it.hostAddress }, second.map { it.hostAddress })
        assertEquals("no debería volver a consultar dentro del TTL", 2, calls) // A + AAAA

        Doh.cached("cache.ejemplo", now = { clock += 61_000; clock }, fetch = fetch)
        assertEquals("pasado el TTL debe volver a preguntar", 4, calls)
        Doh.clearCache()
    }

    @Test
    fun `el TTL se acota entre 30 s y 5 min`() {
        var clock = 0L
        var calls = 0
        // TTL 0 (o ausente) → mínimo 30 s
        val zero: (String) -> String? = { calls++; answer(0, 0, Doh.TYPE_A to "192.0.2.40") }
        Doh.clearCache()
        Doh.cached("ttl-cero.ejemplo", now = { clock }, fetch = zero)
        Doh.cached("ttl-cero.ejemplo", now = { clock += 29_000; clock }, fetch = zero)
        assertEquals("no debe consultar antes de 30 s", 2, calls)
        Doh.cached("ttl-cero.ejemplo", now = { clock += 2_000; clock }, fetch = zero)
        assertEquals("pasados 30 s sí", 4, calls)

        // TTL enorme → máximo 5 min
        clock = 0
        calls = 0
        val huge: (String) -> String? = { calls++; answer(0, 86_400, Doh.TYPE_A to "192.0.2.41") }
        Doh.cached("ttl-largo.ejemplo", now = { clock }, fetch = huge)
        Doh.cached("ttl-largo.ejemplo", now = { clock += 299_000; clock }, fetch = huge)
        assertEquals(2, calls)
        Doh.cached("ttl-largo.ejemplo", now = { clock += 2_000; clock }, fetch = huge)
        assertEquals(4, calls)
        Doh.clearCache()
    }

    // ------------------------- Seguridad -------------------------

    @Test
    fun `solo se resuelven nombres, nunca IP ni basura`() {
        var called = false
        val fetch: (String) -> String? = { called = true; null }

        assertTrue(Doh.resolve("192.168.1.20", fetch = fetch).isEmpty())
        assertTrue(Doh.resolve("[::1]", fetch = fetch).isEmpty())
        assertTrue(Doh.resolve("", fetch = fetch).isEmpty())
        assertTrue(Doh.resolve("   ", fetch = fetch).isEmpty())
        assertTrue(Doh.resolve("mal nombre.com", fetch = fetch).isEmpty())
        assertTrue(Doh.resolve("a/b.com", fetch = fetch).isEmpty())
        assertTrue(Doh.resolve("user@host.com", fetch = fetch).isEmpty())
        assertTrue(Doh.resolve("host:8080", fetch = fetch).isEmpty())
        assertFalse("no debe consultar nada de eso", called)
    }

    @Test
    fun `el reintento solo vale para https y respeta el interruptor`() {
        var called = false
        val fetch: (String) -> String? = { called = true; answer(0, 60, Doh.TYPE_A to "192.0.2.50") }

        // http:// nunca sale por el DNS público (NetPolicy solo lo permite en la LAN)
        assertNull(Doh.retry("http://192.168.1.20:8787/stream/movie/tt1.json", fetch = fetch))
        assertNull(Doh.retry("http://publico.ejemplo/manifest.json", fetch = fetch))
        // interruptor apagado
        assertNull(Doh.retry("https://addon.ejemplo/manifest.json", enabled = false, fetch = fetch))
        // una IP no necesita DNS
        assertNull(Doh.retry("https://192.0.2.60/manifest.json", fetch = fetch))
        assertFalse(called)
    }

    @Test
    fun `el verificador del reintento comprueba el nombre original`() {
        // El verificador no acepta certificados sin comprobar: delega en el del sistema.
        val verifier = Doh.hostnameVerifier("addon.ejemplo")
        assertFalse(verifier.verify("192.0.2.10", null))
    }

    @Test
    fun `las IP literales se reconocen bien`() {
        assertTrue(Doh.parseLiteral("192.168.1.20") is Inet4Address)
        assertTrue(Doh.parseLiteral(" [::1] ") is Inet6Address)
        assertTrue(Doh.parseLiteral("2001:db8::1") is Inet6Address)
        assertNull(Doh.parseLiteral("ejemplo.com"))
        assertNull(Doh.parseLiteral(""))
        assertNull(Doh.parseLiteral("1.2.3.4 file:///"))
        assertNull(Doh.parseLiteral("999.1.1.1"))
    }
}
