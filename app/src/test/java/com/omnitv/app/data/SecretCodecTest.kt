package com.omnitv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

/**
 * Formato del cifrado de las claves de API. Aquí se usa una clave AES en
 * memoria; en el dispositivo la clave la aporta el Android Keystore
 * ([SecretBox]), que no existe en los tests de JVM.
 */
class SecretCodecTest {

    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val otherKey = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
    private val secret = """{"TMDB":"abc123","TRAKT":"ñ-ü-✓"}"""

    @Test
    fun `lo cifrado se recupera con la misma clave`() {
        assertEquals(secret, SecretCodec.open(key, SecretCodec.seal(key, secret)))
        assertEquals("", SecretCodec.open(key, SecretCodec.seal(key, "")))
    }

    @Test
    fun `el texto cifrado no contiene el secreto y cambia en cada llamada`() {
        val first = SecretCodec.seal(key, secret)
        val second = SecretCodec.seal(key, secret)

        assertFalse(first.contains("abc123"))
        assertTrue(first.matches(Regex("[0-9a-f]+")))
        assertNotEquals("el IV debe ser distinto cada vez", first, second)
    }

    @Test
    fun `no se descifra con otra clave`() = assertOpenFails(otherKey, SecretCodec.seal(key, secret))

    @Test
    fun `un dato alterado se rechaza`() {
        val sealed = SecretCodec.seal(key, secret)
        val last = sealed.last()
        assertOpenFails(key, sealed.dropLast(1) + if (last == '0') '1' else '0')
    }

    @Test
    fun `un dato con formato desconocido se rechaza`() {
        assertOpenFails(key, "")
        assertOpenFails(key, "abc")
        assertOpenFails(key, "no-es-hexadecimal")
        assertOpenFails(key, """{"TMDB":"abc123"}""")
        assertOpenFails(key, "0c00")
    }

    private fun assertOpenFails(key: SecretKeySpec, sealed: String) {
        try {
            SecretCodec.open(key, sealed)
            fail("debería fallar al abrir «$sealed»")
        } catch (expected: Exception) {
            // correcto: SecretBox lo convierte en «sin claves guardadas»
        }
    }
}
