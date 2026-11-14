package com.omnitv.app.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifrado de secretos en reposo (claves de API del panel de vinculación).
 *
 * AES-256-GCM con una clave del **Android Keystore**: la clave no sale del
 * almacén del sistema, así que el texto guardado en SharedPreferences no se
 * puede descifrar fuera de este dispositivo (ni desde una copia de
 * seguridad).
 *
 * El Keystore solo genera claves AES desde Android 6.0 (API 23). En API 22
 * [available] es `false` y los secretos se guardan sin cifrar; la interfaz
 * lo indica tal cual en lugar de prometer un cifrado que no existe.
 */
object SecretBox {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "omni_tv_secrets_v1"

    /** Este dispositivo puede cifrar con el Keystore. */
    val available: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    /** Cifra [plain]; `null` si no hay Keystore o si este falla. */
    fun encrypt(plain: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return runCatching { SecretCodec.seal(keystoreKey(), plain) }.getOrNull()
    }

    /** Descifra un valor de [encrypt]; `null` si no se puede (clave perdida, dato alterado). */
    fun decrypt(sealed: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return runCatching { SecretCodec.open(keystoreKey(), sealed) }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}

/**
 * Formato del dato cifrado: hexadecimal de ( longitud-IV (1 byte) · IV ·
 * texto cifrado + etiqueta GCM ). El IV lo genera el propio cifrador en
 * cada llamada (el Keystore exige IV aleatorio). Sin dependencias de
 * Android, para poder probarlo con tests unitarios.
 */
internal object SecretCodec {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    fun seal(key: SecretKey, plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val blob = ByteArray(1 + iv.size + encrypted.size)
        blob[0] = iv.size.toByte()
        System.arraycopy(iv, 0, blob, 1, iv.size)
        System.arraycopy(encrypted, 0, blob, 1 + iv.size, encrypted.size)
        return blob.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    fun open(key: SecretKey, sealed: String): String {
        require(sealed.isNotEmpty() && sealed.length % 2 == 0) { "formato desconocido" }
        val blob = ByteArray(sealed.length / 2) { i ->
            sealed.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val ivSize = blob[0].toInt()
        require(ivSize in 1..32 && blob.size > 1 + ivSize) { "formato desconocido" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, 1, ivSize))
        val plain = cipher.doFinal(blob, 1 + ivSize, blob.size - 1 - ivSize)
        return String(plain, Charsets.UTF_8)
    }
}
