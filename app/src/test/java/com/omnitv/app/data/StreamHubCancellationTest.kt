package com.omnitv.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class StreamHubCancellationTest {

    @Test
    fun `un error normal del addon usa el fallback vacio`() = runBlocking {
        val result = runAddonCancellable(emptyList<String>()) {
            throw IllegalStateException("provider failed")
        }
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `la cancelacion se propaga y no se convierte en lista vacia`() {
        val expected = CancellationException("player closed")
        try {
            runBlocking {
                runAddonCancellable(emptyList<String>()) {
                    throw expected
                }
            }
            fail("la cancelación debe propagarse")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
    }
}
