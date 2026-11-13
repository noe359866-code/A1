package com.omnitv.app.data

/**
 * Plataforma de streaming (como «Browse by Provider» de cinejoy.pk).
 *
 * - [id]: id de proveedor de TMDB (la web usa `cinejoy.pk/provider/{id}`).
 * - [logoUrl]: logo público del CDN de TMDB (`image.tmdb.org/t/p/w154/…`).
 * - [nuvioMoviesCatalog] / [nuvioSeriesCatalog]: catálogos del addon Nuvio
 *   con contenido real por plataforma, cuando existen.
 */
data class StreamingProvider(
    val id: Int,
    val name: String,
    val logoUrl: String,
    val nuvioMoviesCatalog: String? = null,
    val nuvioSeriesCatalog: String? = null
)

object Providers {

    private fun logo(file: String) = "https://image.tmdb.org/t/p/w154/$file"

    /** Las 17 plataformas de «Browse by Provider» de cinejoy.pk. */
    val all = listOf(
        StreamingProvider(
            8, "Netflix", logo("rK1KljqmbvO9HQa1PBFLILWah72.png"),
            nuvioMoviesCatalog = "netflix-movies", nuvioSeriesCatalog = "netflix-series"
        ),
        StreamingProvider(
            9, "Amazon Prime Video", logo("gMZdpavHmxFNnLpMHwVxfqeux2g.png"),
            nuvioMoviesCatalog = "prime-video-movies", nuvioSeriesCatalog = "prime-video-series"
        ),
        StreamingProvider(
            337, "Disney Plus", logo("5eZ872CghnHFLB1j8grszbrx0dx.png"),
            nuvioMoviesCatalog = "disney-plus-movies", nuvioSeriesCatalog = "disney-plus-series"
        ),
        StreamingProvider(350, "Apple TV+", logo("9icYBfYFcwgCbky5VdGUIKJ4C5i.png")),
        StreamingProvider(2, "Apple TV", logo("qdEGArH3lKfFnAtYXMkSYk5wxuG.png")),
        StreamingProvider(15, "Hulu", logo("44uAnmSqvA4yBOdbPWN8YgQHjWm.png")),
        StreamingProvider(1899, "HBO Max", logo("skypuy7SXuugIQeYg0IglmzoKaS.png")),
        StreamingProvider(2303, "Paramount Plus", logo("4N4BMd0Mm0kHAmF7RZgL5lW3cwc.png")),
        StreamingProvider(386, "Peacock Premium", logo("a1UIdq5BrkcAxnxcUhFsNbXnxeu.png")),
        StreamingProvider(283, "Crunchyroll", logo("uFL3c4Cq8M6WoLymlC5Y8bmGytV.png")),
        StreamingProvider(43, "Starz", logo("h25xjouKmiSmFiiqw0aDXbxGZo7.png")),
        StreamingProvider(526, "AMC+", logo("wsCUflcmL4dCkzBUaGP8cj1cTkh.png")),
        StreamingProvider(34, "MGM Plus", logo("q63Uzpu7JAs566vA2G23Lk7LcID.png")),
        StreamingProvider(188, "YouTube Premium", logo("eWHpKZihdiY617s5LipWJWvN28U.png")),
        StreamingProvider(192, "YouTube", logo("5Maob4o5w8oZnNeYpCDyVFD3M7X.png")),
        StreamingProvider(73, "Tubi TV", logo("9dEuvA8wg5TSeFBZlPxSVxFdimJ.png")),
        StreamingProvider(300, "Pluto TV", logo("fN4czqaMQNLeF6sSSIjGbAWzvwK.png"))
    )

    fun byId(id: Int): StreamingProvider? = all.firstOrNull { it.id == id }
}
