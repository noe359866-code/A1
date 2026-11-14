package com.omnitv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El PIN parental se guarda solo como huella con salt, nunca en claro. */
class ParentalPinTest {

    @Test
    fun `solo acepta exactamente cuatro digitos ASCII`() {
        assertTrue(ParentalPin.isWellFormed("0000"))
        assertTrue(ParentalPin.isWellFormed("9876"))
        assertFalse(ParentalPin.isWellFormed("123"))
        assertFalse(ParentalPin.isWellFormed("12345"))
        assertFalse(ParentalPin.isWellFormed("12 4"))
        assertFalse(ParentalPin.isWellFormed("12a4"))
        assertFalse(ParentalPin.isWellFormed("１２３４")) // dígitos Unicode
        assertFalse(ParentalPin.isWellFormed(""))
    }

    @Test
    fun `la huella es estable con el mismo salt y distinta con otro`() {
        val a = ParentalPin.newSalt()
        val b = ParentalPin.newSalt()
        assertNotEquals(a, b)
        val first = ParentalPin.hash("1234", a)
        assertEquals(first, ParentalPin.hash("1234", a))
        assertNotEquals(first, ParentalPin.hash("1234", b))
        assertNotEquals(first, ParentalPin.hash("1235", a))
        // SHA-256 en hexadecimal: ni el PIN en claro ni caracteres ajenos.
        assertEquals(64, first!!.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `verify acierta solo con el PIN correcto`() {
        val salt = ParentalPin.newSalt()
        val hash = ParentalPin.hash("4321", salt)!!
        assertTrue(ParentalPin.verify("4321", salt, hash))
        assertFalse(ParentalPin.verify("4322", salt, hash))
        assertFalse(ParentalPin.verify("4321", ParentalPin.newSalt(), hash))
        assertFalse(ParentalPin.verify("4321", salt, ""))
        assertFalse(ParentalPin.verify("4321", salt, hash.dropLast(1)))
        assertFalse(ParentalPin.verify("432", salt, hash))
        assertTrue(ParentalPin.verify("4321", salt, hash.uppercase()))
    }

    @Test
    fun `hash rechaza entradas mal formadas`() {
        val salt = ParentalPin.newSalt()
        assertNull(ParentalPin.hash("12a4", salt))
        assertNull(ParentalPin.hash("12345", salt))
        assertNull(ParentalPin.hash("1234", "no-es-hex"))
        assertNull(ParentalPin.hash("1234", ""))
        assertFalse(ParentalPin.verify("1234", "no-es-hex", "abc"))
    }
}
