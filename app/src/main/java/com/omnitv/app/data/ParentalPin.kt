package com.omnitv.app.data

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PIN de control parental, sin Android: así se puede probar en la JVM.
 *
 * No se guarda el PIN ni una huella reutilizable de 4 cifras sin sal: se
 * deriva con SHA-256 sobre `salt || pin`, con un salt aleatorio por
 * dispositivo. Sigue siendo un **bloqueo de uso doméstico**, no criptografía
 * de alta seguridad (10.000 combinaciones), por eso también se limita el
 * ritmo de intentos en la interfaz.
 */
object ParentalPin {

    const val LENGTH = 4
    private const val SALT_BYTES = 16

    /** Solo exactamente 4 dígitos ASCII. Evita `trim` que acepte espacios. */
    fun isWellFormed(pin: String): Boolean =
        pin.length == LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(): String = randomBytes(SALT_BYTES).toHex()

    /** Huella hexadecimal de `salt || pin`; se guarda en los ajustes. */
    fun hash(pin: String, salt: String): String? {
        if (!isWellFormed(pin) || !isHex(salt)) return null
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.fromHex() ?: return null)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return digest.digest().toHex()
    }

    /** Comparación en tiempo constante; nunca devuelve `true` con datos vacíos. */
    fun verify(pin: String, salt: String, expected: String): Boolean {
        val actual = hash(pin, salt) ?: return false
        if (!isHex(expected) || expected.length != actual.length) return false
        // Las huellas se guardan en minúsculas, pero aceptar mayúsculas evita
        // falsos negativos si algún backup/import cambió la representación.
        val normalized = expected.lowercase()
        return MessageDigest.isEqual(actual.toByteArray(Charsets.US_ASCII), normalized.toByteArray(Charsets.US_ASCII))
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (b in this@toHex) {
            val v = b.toInt() and 0xff
            append(HEX[v ushr 4]); append(HEX[v and 0x0f])
        }
    }

    private fun isHex(value: String): Boolean =
        value.isNotEmpty() && value.length % 2 == 0 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    private fun String.fromHex(): ByteArray? = runCatching {
        ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }.getOrNull()

    private val HEX = "0123456789abcdef".toCharArray()
}
