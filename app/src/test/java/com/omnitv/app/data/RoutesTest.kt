package com.omnitv.app.data

import com.omnitv.app.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.URLDecoder

class RoutesTest {
    @Test
    fun `los ids de episodios y addons sobreviven como un unico argumento`() {
        val ids = listOf(
            "tt0944947~s1e2",
            "tt0944947:1:2",
            "tmdb:123~s1e2",
            "addon/provider/serie~episode",
            "título con espacios~1"
        )
        for (id in ids) {
            val route = Routes.player(id)
            val argument = route.substringAfter("player/")
            assertFalse("ruta sin escapar: $route", '/' in argument)
            assertEquals(id, URLDecoder.decode(argument, "UTF-8"))
            assertEquals(id, URLDecoder.decode(Routes.detail(id).substringAfter("detail/"), "UTF-8"))
        }
    }

    @Test
    fun `los espacios no se convierten en mas al navegar`() {
        val argument = Routes.editProfile("perfil nuevo").substringAfter("editProfile/")
        assertFalse('+' in argument)
        assertEquals("perfil nuevo", URLDecoder.decode(argument, "UTF-8"))
    }
}
