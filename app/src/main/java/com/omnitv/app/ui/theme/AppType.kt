package com.omnitv.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tipografía Omni TV — escala “10 pies”.
 * Optimizada para lectura a 2-3m en panel 1080p (lienzo 960×540dp).
 * Cuerpo 21sp, nada por debajo de 14sp. Jerarquía marcada con pesos y tracking.
 */
object AppType {

    private val Sans = FontFamily.SansSerif

    val display = TextStyle(Sans, FontWeight.Black, 58.sp, 62.sp, letterSpacing = (-1.2).sp)
    val hero = TextStyle(Sans, FontWeight.Bold, 54.sp, 60.sp, letterSpacing = (-1.0).sp)
    val screenTitle = TextStyle(Sans, FontWeight.Bold, 40.sp, 46.sp, letterSpacing = (-0.6).sp)
    val sectionTitle = TextStyle(Sans, FontWeight.SemiBold, 27.sp, 33.sp, letterSpacing = (-0.25).sp)
    val cardTitle = TextStyle(Sans, FontWeight.SemiBold, 18.sp, 23.sp)
    val body = TextStyle(Sans, FontWeight.Normal, 21.sp, 29.sp)
    val bodyStrong = TextStyle(Sans, FontWeight.SemiBold, 21.sp, 29.sp)
    val metadata = TextStyle(Sans, FontWeight.Medium, 18.sp, 24.sp)
    val caption = TextStyle(Sans, FontWeight.Medium, 16.sp, 21.sp)
    val button = TextStyle(Sans, FontWeight.SemiBold, 21.sp, letterSpacing = 0.2.sp)
    val buttonSmall = TextStyle(Sans, FontWeight.SemiBold, 17.sp)
    val tab = TextStyle(Sans, FontWeight.SemiBold, 20.sp, letterSpacing = 0.2.sp)
    val chip = TextStyle(Sans, FontWeight.Bold, 15.sp, letterSpacing = 1.0.sp)
    val overline = TextStyle(Sans, FontWeight.Bold, 13.sp, letterSpacing = 2.2.sp)
    val playerTime = TextStyle(Sans, FontWeight.Medium, 19.sp)
    val brand = TextStyle(Sans, FontWeight.ExtraBold, 26.sp, letterSpacing = (-0.3).sp)
    val brandMark = TextStyle(Sans, FontWeight.Bold, 15.sp, letterSpacing = 3.sp)

    // Detalle — estilos afinados
    val heroKicker = TextStyle(Sans, FontWeight.Bold, 14.sp, letterSpacing = 1.8.sp)
    val match = TextStyle(Sans, FontWeight.Bold, 19.sp)
    val quote = TextStyle(Sans, FontWeight.Normal, 20.sp, 28.sp)
}
