package com.omnitv.app.data

/**
 * Estudio/productora (como el «Browse by studio» de sweetfox.app).
 *
 * - [id]: id de compañía de TMDB (`sweetfox.app/company/{id}`).
 * - [logoUrl]: logo público del CDN de TMDB (`image.tmdb.org/t/p/w300/…`).
 */
data class Studio(
    val id: Int,
    val name: String,
    val logoUrl: String
)

object Studios {

    private fun logo(file: String) = "https://image.tmdb.org/t/p/w300/$file"

    /** Los 24 estudios del «Browse by studio» de sweetfox.app. */
    val all = listOf(
        Studio(521, "DreamWorks Animation", logo("3BPX5VGBov8SDqTV7wC1L1xShAS.png")),
        Studio(420, "Marvel", logo("hUzeosd33nzE5MCNsZxCGEKTXaQ.png")),
        Studio(11537, "LAIKA", logo("AgCkAk8EpUG9fTmK6mWcaJA2Zwh.png")),
        Studio(1632, "Lionsgate", logo("cisLn1YAUuptXVBa0xjq7ST9cH0.png")),
        Studio(3172, "Blumhouse", logo("rzKluDcRkIwHZK2pHsiT667A2Kw.png")),
        Studio(90733, "NEON", logo("3K9wCZTyDgop3ITK1rDi6T2PckE.png")),
        Studio(923, "Legendary", logo("5UQsZrfbfG2dYJbx8DxfoTr2Bvu.png")),
        Studio(127928, "20th Century", logo("h0rjX5vjW5r8yEnUBStFarjcLT4.png")),
        Studio(12, "New Line Cinema", logo("2ycs64eqV5rqKYHyQK0GVoKGvfX.png")),
        Studio(2, "Walt Disney", logo("wdrCwmRnLFJhEoH8GSfymY85KHT.png")),
        Studio(6704, "Illumination", logo("fOG2oY4m1YuYTQh4bMqqZkmgOAI.png")),
        Studio(3, "Pixar", logo("1TjvGVDMYsj6JBxOAkUHpPEwLf7.png")),
        Studio(33, "Universal", logo("8lvHyhjr8oUKOOy2dKXoALWKdp0.png")),
        Studio(882, "TOHO", logo("fRSWWjquvzcHjACbtF53utZFIll.png")),
        Studio(293354, "A24", logo("u7nsZM0vmBMDpzIy27164AwMjXV.png")),
        Studio(10146, "Focus Features", logo("xnFIOeq5cKw09kCWqV7foWDe4AA.png")),
        Studio(5, "Columbia", logo("71BqEFAF4V3qjjMPCpLuyJFB9A.png")),
        Studio(127929, "Searchlight", logo("7DLKyL15ETI9645XSr9JcbMV79c.png")),
        Studio(10342, "Studio Ghibli", logo("uFuxPEZRUcBTEiYIxjHJq62Vr77.png")),
        Studio(56, "Amblin", logo("cEaxANEisCqeEoRvODv2dO1I0iI.png")),
        Studio(1, "Lucasfilm", logo("tlVSws0RvvtPBwViUyOFAO0vcQS.png")),
        Studio(184898, "DC", logo("uVpjHYq5AbiG2qj82ZRBM4VFL3o.png")),
        Studio(4, "Paramount", logo("jay6WcMgagAklUt7i9Euwj1pzTF.png")),
        Studio(174, "Warner Bros.", logo("zhD3hhtKB5qyv7ZeL4uLpNxgMVU.png"))
    )

    fun byId(id: Int): Studio? = all.firstOrNull { it.id == id }
}
