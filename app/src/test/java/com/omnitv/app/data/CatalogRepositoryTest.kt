package com.omnitv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRepositoryTest {
    private fun video(id: String, title: String = id, episodes: List<Episode> = emptyList(), genres: List<String> = listOf("Drama")) = Video(
        id = id, title = title, description = "Descripción", backdropUrl = "", cardUrl = "",
        year = 2024, durationMinutes = 40, rating = 0.0, certification = "", quality = "",
        genres = genres, videoUrl = "", episodes = episodes, isRemote = true,
        remoteType = if (episodes.isEmpty()) "movie" else "series"
    )

    @Test
    fun `no hay catalogo ni featured de demostracion`() {
        assertNull(CatalogRepository.find("sintel"))
        assertNull(CatalogRepository.find("big-buck-bunny"))
        assertNull(CatalogRepository.featuredFor(null))
    }

    @Test
    fun `una tarjeta no borra los episodios de una ficha conocida`() {
        val ep = Episode("s1e1", "Uno", 1, 40, "", "", "", streamId = "tt9001:1:1")
        val id = "tt-test-merge"
        CatalogRepository.registerRemote(video(id, episodes = listOf(ep)))
        CatalogRepository.registerRemote(video(id, title = "Tarjeta"))
        val full = CatalogRepository.find(id)!!
        assertEquals(1, full.episodes.size)
        assertEquals("Descripción", full.description)
        val episode = CatalogRepository.find("$id~s1e1")!!
        assertEquals("tt9001:1:1", episode.streamId)
        assertTrue(episode.sources.isEmpty())
    }

    @Test
    fun `siguiente es el proximo episodio y no un relacionado por genero`() {
        val id = "tt-test-next"
        val episodes = listOf(
            Episode("s1e1", "Uno", 1, 40, "", "", "", streamId = "tt9002:1:1"),
            Episode("s1e2", "Dos", 2, 40, "", "", "", streamId = "tt9002:1:2")
        )
        CatalogRepository.registerRemote(video(id, episodes = episodes))
        CatalogRepository.registerRemote(video("tt-other", genres = listOf("Drama")))
        val next = CatalogRepository.nextEpisode("$id~s1e1")!!
        assertEquals("$id~s1e2", next.id)
        assertEquals("tt9002:1:2", next.streamId)
        assertNull(CatalogRepository.nextEpisode("$id~s1e2"))
        assertNull(CatalogRepository.nextEpisode(id))
    }

    @Test
    fun `la navegacion conserva un episodio aunque no este en la caché por id`() {
        val ep = video("tt-test-nav~s1e1").copy(streamId = "tt9003:1:1", remoteType = "series")
        CatalogRepository.registerNavigation(ep)
        assertEquals(ep, CatalogRepository.find(ep.id))
    }

    @Test
    fun `una fuente directa no permitida no se entrega al reproductor`() {
        val bad = video("tt-test-policy").copy(videoUrl = "http://public.example/v.mp4")
        assertTrue(bad.playbackSources.isEmpty())
        val lan = video("tt-test-lan").copy(videoUrl = "http://192.168.1.8/v.m3u8", streamFormat = StreamFormat.HLS)
        assertEquals(1, lan.playbackSources.size)
    }
}
