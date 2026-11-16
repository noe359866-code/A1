package com.omnitv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCodecTest {

    @Test
    fun `un perfil dañado no elimina los perfiles validos`() {
        val raw = """
            [
              {"id":"adult","name":"Adulto","progress":{"movie-1":0.4,"broken":"not-a-number"},"myList":["movie-1"]},
              {"id":"","name":"Sin ID"},
              {"id":"kids","name":"Peques","isKids":true,"progress":{"movie-2":0.7}},
              {"id":"adult","name":"Duplicado"}
            ]
        """.trimIndent()

        val profiles = ProfileCodec.decode(raw)

        assertEquals(listOf("adult", "kids"), profiles.map { it.id })
        assertEquals(0.4f, profiles[0].progress["movie-1"]!!, 0.0001f)
        assertEquals(setOf("movie-1"), profiles[0].myList)
        assertTrue(profiles[1].isKids)
        assertEquals(0.7f, profiles[1].progress["movie-2"]!!, 0.0001f)
    }

    @Test
    fun `progreso invalido o completado no se restaura`() {
        val raw = """[
            {"id":"p1","name":"Perfil","progress":{
              "ok":0.5,"too-low":0.01,"completed":0.98,"negative":-1,"too-high":1.4,"bad":"x"
            }}
        ]"""

        val profile = ProfileCodec.decode(raw).single()

        assertEquals(mapOf("ok" to 0.5f), profile.progress)
    }

    @Test
    fun `la codificacion limita campos y mantiene los datos validos`() {
        val profile = Profile(
            id = "p1",
            name = "Cine",
            avatarIndex = 99,
            isKids = true,
            autoPlayNext = false,
            progress = mapOf("movie" to 0.55f, "invalid" to Float.NaN, "done" to 1f),
            myList = setOf("movie", "")
        )

        val decoded = ProfileCodec.decode(ProfileCodec.encode(listOf(profile))).single()

        assertEquals(AVATAR_COUNT - 1, decoded.avatarIndex)
        assertTrue(decoded.isKids)
        assertFalse(decoded.autoPlayNext)
        assertEquals(mapOf("movie" to 0.55f), decoded.progress)
        assertEquals(setOf("movie"), decoded.myList)
    }

    @Test
    fun `el limite de perfiles se aplica sin exceder el almacenamiento`() {
        val profiles = (0..ProfileCodec.MAX_PROFILES).map { Profile(id = "p$it", name = "Perfil $it") }
        val decoded = ProfileCodec.decode(ProfileCodec.encode(profiles))
        assertEquals(ProfileCodec.MAX_PROFILES, decoded.size)
    }

    @Test
    fun `json raiz invalido no lanza excepcion`() {
        assertTrue(ProfileCodec.decode("no es json").isEmpty())
    }
}
