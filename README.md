# Omni TV · App de streaming para Android TV

<p align="center">
  <em>«Seis pantallas, una experiencia»</em> — una app de streaming <strong>solo para Android TV</strong>,
  construida con <strong>Kotlin</strong>, <strong>Jetpack Compose for TV</strong> y <strong>Media3 ExoPlayer</strong>,
  con un diseño inspirado en la experiencia de <strong>Apple TV / tvOS</strong>.
</p>

---

## ✨ Características

| | |
| --- | --- |
| **Solo Android TV** | `AndroidManifest.xml` con `leanback` obligatorio, `touchscreen` opcional y `LEANBACK_LAUNCHER` + banner 320×180 para el launcher de TV |
| **Diseño Apple TV** | Fondo casi negro `#0B0B0F`, acento azul `#0A84FF`, tarjetas con foco que escalan a 1.08, botones píldora, velos de degradado, tipografía de jerarquía marcada |
| **Compose for TV** | `androidx.tv:tv-material` (`MaterialTheme`, `Card` con estados de foco D-pad) + Compose estándar con `focusRestorer` |
| **Media3 ExoPlayer** | Reproductor a pantalla completa con controles propios (play/pausa, barra de progreso con búfer, ±10 s), soporte **MP4 y HLS** (módulo `media3-exoplayer-hls`), audio focus integrado |
| **Navegación por mando** | Pestañas superiores tipo tvOS (Inicio · Películas · Buscar · Ajustes), ficha de detalle y reproductor con `navigation-compose` |
| **Perfiles como Netflix** | «¿Quién está viendo?», avatares de color, modo infantil, *Continuar viendo* y *Mi lista* por perfil, gestión completa (crear/editar/eliminar) y cambio rápido desde la barra superior |

## 📺 Pantallas

0. **¿Quién está viendo?** — puerta de entrada estilo Netflix: avatares circulares
   de color con inicial, chip *KIDS* en perfiles infantiles y acceso a
   *Gestionar perfiles* / *Añadir perfil*.
1. **Inicio** — banner héroe cinematográfico (tipo *Watch Now*) + filas horizontales:
   *Mi lista* y *Continuar viendo* (con barra de progreso, **propias de cada perfil**),
   *Estrenos destacados*, *Top 10 hoy*, *Cortometrajes premiados* y *Transmisiones en directo · HLS*.
2. **Películas** — rejilla con todo el catálogo (filtrado en perfiles infantiles).
3. **Buscar** — campo de búsqueda con sugerencias de género y rejilla de resultados.
4. **Detalle** — fondo a sangre con velo, título grande, metadatos (año · duración · ★ · calidad · clasificación), botones *Reproducir* / *Mi lista* y recomendaciones.
5. **Reproductor** — Media3 ExoPlayer con overlay propio estilo Apple. Al salir,
   el progreso se guarda en el perfil activo.
6. **Ajustes** — perfil activo con *Cambiar de perfil*, interruptores tipo iOS y opciones de reproducción.
7. **Gestionar / Editar perfil** — modo edición con lápiz sobre cada avatar; el editor
   cambia nombre, color e interruptor de perfil infantil, y permite eliminar.

## 🎮 Controles del mando (D-pad)

| Tecla | Inicio / listas | Reproductor |
| --- | --- | --- |
| **Centro / Enter** | Abrir elemento enfocado | Mostrar controles · reproducir/pausar |
| **◀ / ▶** | Moverse por las filas | Retroceder / avanzar **10 s** |
| **▲ / ▼** | Moverse entre filas | Mostrar controles |
| **Atrás** | Volver | Salir del reproductor |

## 👤 Perfiles (como Netflix)

- **Puerta de perfiles**: al arrancar siempre aparece «¿Quién está viendo?».
- **Estado por perfil**: *Continuar viendo* (progreso real del reproductor, se
  elimina al ver ≥ 95 % del título) y *Mi lista* (alta/baja desde la ficha).
- **Perfiles infantiles**: solo ven contenido clasificado TP/7+, en inicio,
  películas y buscador.
- **Gestión**: crear, editar (nombre, color de avatar, modo infantil) y eliminar
  (no se puede borrar el último perfil). Persistido en `SharedPreferences`
  (JSON con `org.json`, sin dependencias).
- **Cambio rápido**: avatar en la esquina superior derecha de la barra de
  pestañas → vuelve al selector; también desde *Ajustes → Cambiar de perfil*.
- Perfiles de demostración: **Sofía**, **Marco** y **Peques** (infantil).

## 🛠 Stack técnico

| Componente | Versión |
| --- | --- |
| Kotlin | 2.0.20 |
| Android Gradle Plugin | 8.7.2 · Gradle 8.9 |
| Jetpack Compose (BOM) | 2024.10.01 |
| Compose for TV (`tv-material`) | 1.0.0 |
| Media3 ExoPlayer (+ HLS, ui) | 1.4.1 |
| Navigation Compose | 2.8.4 |
| Coil (imágenes) | 2.6.0 |
| minSdk / targetSdk / compileSdk | 22 / 34 / 35 |

Sin dependencias de red propias: el catálogo de demostración vive en
`CatalogRepository` y usa **streams públicos de prueba** (bucket de samples de
Google, HLS de prueba de Apple *bipbop* y Mux) — aptos para desarrollar, no para
distribución comercial.

## 📁 Estructura del proyecto

```
A1/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml          # leanback, banner TV, sin touchscreen
│       ├── java/com/omnitv/app/
│       │   ├── MainActivity.kt          # ComponentActivity + keep-screen-on
│       │   ├── OmniTvApp.kt             # NavHost + pestañas + rutas
│       │   ├── data/
│       │   │   ├── Models.kt            # Video, MediaRow, StreamFormat
│       │   │   ├── Profile.kt           # perfiles + paleta de avatares
│       │   │   ├── ProfileStore.kt      # persistencia de perfiles (SharedPreferences)
│       │   │   └── CatalogRepository.kt # catálogo demo (MP4 + HLS, por perfil)
│       │   ├── player/
│       │   │   └── PlayerScreen.kt      # Media3 ExoPlayer + controles Apple
│       │   └── ui/
│       │       ├── theme/               # Color, AppType (tipografía), Theme
│       │       ├── components/          # Cards, PillButton, HeroBanner, SeekBar…
│       │       │                        # ProfileComponents: avatares y fichas de perfil
│       │       └── screens/             # Perfiles, Home, Detail, Movies, Search…
│       └── res/
│           ├── drawable/                # tv_banner, iconos vectoriales
│           ├── mipmap*/                 # icono adaptativo (lupa con 6 puntos)
│           └── values/                  # strings, colors, themes
├── gradle/libs.versions.toml            # catálogo de versiones
└── gradlew / gradle/wrapper/            # Gradle 8.9
```

## 🚀 Compilar y ejecutar

Requisitos: **JDK 17**, **Android SDK 35**, Android Studio (Ladybug o posterior) o
CLI con el SDK instalado.

```bash
# Instalar en un dispositivo / emulador de Android TV conectado
./gradlew installDebug

# APK de depuración
./gradlew assembleDebug

# Lint del contrato de TV
./gradlew lint
```

> **Emulador:** crea un AVD con perfil *Android TV (1080p)*, API 22+ (recomendado 34).
> La app solo aparece en el launcher de TV (categoría `LEANBACK_LAUNCHER`).

## 🍎 Decisiones de diseño (estética Apple TV)

- **Foco protagonista**: la tarjeta enfocada escala (1.08) y las filas restauran
  el foco con `Modifier.focusRestorer()` al volver con «Atrás».
- **Casi negro, no negro puro**: `#0B0B0F` de fondo y `#16161C` de superficie,
  como el modo oscuro de tvOS.
- **Velo de degradado** en héroe y detalle para legibilidad del texto blanco.
- **Un único acento**: azul systemBlue `#0A84FF`; el blanco se reserva para el foco
  y los botones primarios.
- **Botones píldora** con icono (▶ Reproducir / ⓘ Más información) idénticos en
  héroe, detalle y reproductor.
- **Tipografía**: pesos altos, interletraje cerrado y jerarquía muy marcada
  (46 / 34 / 21 / 16 / 14 sp).

## 📄 Licencia

MIT — los streams de demostración pertenecen a sus respectivos autores
(Blender Foundation, Google sample bucket, Apple, Mux) y se usan solo con fines
de prueba.
