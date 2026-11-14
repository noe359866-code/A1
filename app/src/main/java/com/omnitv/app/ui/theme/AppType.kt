package com.omnitv.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Escala tipográfica de Omni TV.
 *
 * Estética Apple: pesos altos, interletraje cerrado y jerarquía muy marcada.
 * (SF Pro no se distribuye; usamos la sans-serif del sistema con métricas
 * equivalentes.)
 *
 * **Escala de 10 pies.** La app solo se usa en un televisor, a 2–3 m del sofá.
 * Un televisor de 1080p se declara normalmente como 960 × 540 dp (densidad 320),
 * así que el lienzo de diseño es pequeño y, aun así, el texto tiene que verse a
 * tres metros: el cuerpo base son 21 sp y nada baja de 15 sp. La escala anterior
 * (cuerpo de 16 sp, metadatos de 14 sp, chips de 11 sp) era de móvil: a esa
 * distancia no se lee.
 *
 * Referencias: guía de diseño de Android TV (cuerpo recomendado 18–24 sp,
 * mínimo legible 12 sp) y las recomendaciones de accesibilidad de Google TV.
 */
object AppType {
    /** Título del héroe: 54 sp (antes 46). */
    val hero = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 54.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1.0).sp
    )

    /** Título de pantalla: 40 sp (antes 34). */
    val screenTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.6).sp
    )

    /** Título de sección: 27 sp (antes 21). */
    val sectionTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.25).sp
    )

    /** Título dentro de una tarjeta: 18 sp (antes 15). */
    val cardTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 23.sp
    )

    /** Texto corrido: 21 sp (antes 16). Es el cuerpo base de la app. */
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 21.sp,
        lineHeight = 29.sp
    )

    /** Metadatos (año, duración, ★, «3 resultados»): 18 sp (antes 14). */
    val metadata = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp
    )

    /** Texto de botón: 21 sp (antes 16). */
    val button = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp
    )

    /** Pestañas de la barra superior: 20 sp (antes 15). */
    val tab = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    )

    /**
     * Etiquetas en versalitas («NOMBRE», «AVATAR», «HD»): 15 sp (antes 11).
     *
     * Es el tamaño más pequeño de la app; con interletraje amplio las
     * mayúsculas se leen bien a distancia.
     */
    val chip = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 1.0.sp
    )

    /** Tiempos del reproductor: 19 sp (antes 13). */
    val playerTime = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp
    )

    /** Marca «Omni TV» de la barra: 26 sp (antes 22). */
    val brand = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        letterSpacing = (-0.3).sp
    )

    /** Antetítulo en versalitas espaciadas: 15 sp (antes 11). */
    val brandMark = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 3.sp
    )
}
