package com.omnitv.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Escala tipográfica unificada de Omni TV para Android TV (10 pies).
 *
 * Estética inspirada en tvOS / Apple TV y adaptada a la distancia de salón (2–3 m):
 * pesos definidos, interletraje cerrado y jerarquía consistente.
 */
object AppType {
    /** Título del héroe: 54 sp. */
    val hero = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 54.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1.0).sp
    )

    /** Título de pantalla: 40 sp. */
    val screenTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.6).sp
    )

    /** Título de sección y diálogos: 27 sp. */
    val sectionTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.25).sp
    )

    /** Título de tarjetas en ajustes / tarjetas destacadas: 22 sp. */
    val cardHeading = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    )

    /** Nombres de perfil: 22 sp. */
    val profileName = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    )

    /** Título dentro de una tarjeta de póster: 18 sp. */
    val cardTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 23.sp
    )

    /** Texto corrido base de la app: 21 sp. */
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 21.sp,
        lineHeight = 29.sp
    )

    /** Elementos de control y categorías en ajustes/reproductor: 19 sp. */
    val controlLabel = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp
    )

    /** Metadatos (año, duración, ★, resultados): 18 sp. */
    val metadata = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp
    )

    /** Texto de botón: 21 sp. */
    val button = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp
    )

    /** Pestañas de la barra superior: 16 sp para mostrar todas las etiquetas sin saltos ni desborde. */
    val tab = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    )

    /**
     * Etiquetas en versalitas («NOMBRE», «AVATAR», «HD», «KIDS»): 15 sp.
     * Mínimo legible para TV de salón con interletraje amplio.
     */
    val chip = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 1.0.sp
    )

    /** Cabeceras de menú de opciones: 16 sp versalitas. */
    val menuHeader = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 1.0.sp
    )

    /** Tiempos del reproductor: 19 sp. */
    val playerTime = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp
    )

    /** Marca «Omni TV» de la barra: 26 sp. */
    val brand = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        letterSpacing = (-0.3).sp
    )

    /** Antetítulo en versalitas espaciadas: 15 sp. */
    val brandMark = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 3.sp
    )

    /** Código de emparejamiento / valores monospace: 22 sp. */
    val monospaceCode = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 1.5.sp
    )

    /** Numerales editoriales del bloque «Top 10 hoy» (estilo Cinejoy / NovaHD). */
    val rankDisplay = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 68.sp,
        lineHeight = 68.sp,
        letterSpacing = (-2.0).sp
    )
}
