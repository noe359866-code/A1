package com.omnitv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests unitarios de perfiles y avatares para Android TV. */
class ProfileStoreTest {

    @Test
    fun `avatarRes y avatarLabel soportan todos los indices ciclicos`() {
        assertEquals(12, AVATAR_COUNT)
        for (i in 0 until AVATAR_COUNT) {
            assertTrue(avatarRes(i) > 0)
            assertTrue(avatarLabel(i).isNotBlank())
        }
        // Soporte de índices fuera de rango o negativos
        assertEquals(avatarRes(0), avatarRes(AVATAR_COUNT))
        assertEquals(avatarRes(1), avatarRes(AVATAR_COUNT + 1))
        assertEquals(avatarRes(0), avatarRes(-AVATAR_COUNT))
    }

    @Test
    fun `modelo Profile tiene valores por defecto correctos`() {
        val profile = Profile(id = "p1", name = "Test")
        assertEquals(0, profile.avatarIndex)
        assertFalse(profile.isKids)
        assertTrue(profile.autoPlayNext)
        assertTrue(profile.progress.isEmpty())
        assertTrue(profile.myList.isEmpty())
    }

    @Test
    fun `perfil infantil filtra correctamente segun isKids`() {
        val adult = Profile(id = "adult", name = "Adulto", isKids = false)
        val kids = Profile(id = "kids", name = "Peques", isKids = true)
        assertFalse(adult.isKids)
        assertTrue(kids.isKids)
    }

    @Test
    fun `copia de perfil mantiene integridad de datos`() {
        val original = Profile(
            id = "p1",
            name = "Nombre Original",
            avatarIndex = 3,
            isKids = false,
            autoPlayNext = true,
            progress = mapOf("tt001" to 0.45f),
            myList = setOf("tt001", "tt002")
        )

        val updated = original.copy(
            name = "Nombre Modificado",
            avatarIndex = 8,
            isKids = true,
            autoPlayNext = false
        )

        assertEquals("Nombre Modificado", updated.name)
        assertEquals(8, updated.avatarIndex)
        assertTrue(updated.isKids)
        assertFalse(updated.autoPlayNext)
        assertEquals(1, updated.progress.size)
        assertEquals(2, updated.myList.size)
    }
}
