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
| **Media3 ExoPlayer** | Reproductor estilo **Xuper TV**: franja EPG *Ahora/A continuación*, **multi-servidor** con conmutación automática ante errores, selectores de **calidad / audio / subtítulos**, **relación de aspecto y zoom** (16:9, 4:3, 21:9…), controles ±10 s, barra con búfer y auto-ocultado |
| **Navegación por mando** | Pestañas superiores tipo tvOS (Inicio · Películas · Buscar · Ajustes), ficha de detalle y reproductor con `navigation-compose` |
| **Perfiles como Netflix** | «¿Quién está viendo?», avatares con caras ilustradas estilo Netflix, modo infantil, *Continuar viendo* y *Mi lista* por perfil, gestión completa (crear/editar/eliminar) y cambio rápido desde la barra superior |

## 📺 Pantallas

0. **¿Quién está viendo?** — puerta de entrada estilo Netflix: tiles cuadrados
   redondeados con **avatares de caras ilustradas** (sonrisa clásica, gran sonrisa,
   ojos cerrados, lengua, pícara, sorprendida, guiño y sonrisa con dientes),
   chip *KIDS* en perfiles infantiles y acceso a *Gestionar perfiles* / *Añadir perfil*.
1. **Inicio** — banner héroe cinematográfico (tipo *Watch Now*) + filas horizontales:
   *Mi lista* y *Continuar viendo* (con barra de progreso, **propias de cada perfil**),
   *Estrenos destacados*, *Top 10 hoy*, *Cortometrajes premiados* y *Transmisiones en directo · HLS*.
2. **Películas** — rejilla con todo el catálogo (filtrado en perfiles infantiles).
3. **Buscar** — campo de búsqueda con sugerencias de género y rejilla de resultados.
4. **Detalle — transparente con tráiler de fondo** *(estilo Netflix)*: el
   **tráiler se reproduce en bucle y silenciado** detrás de la interfaz
   (con botón de sonido y chip *TRÁILER*), título grande, **match %** verde
   («98 % para ti»), chip de tipo (**Película · Serie · Anime**), metadatos,
   botones *Reproducir / Mi lista / Sonido del tráiler*, **lista de episodios**
   reproducibles en series y anime, reparto, géneros e info sobre velos de
   degradado. *Más como esto* al final.
5. **Reproductor** — estilo Xuper TV sobre Media3 ExoPlayer (ver sección propia).
   Al salir, el progreso se guarda en el perfil activo.
6. **Ajustes** — tarjetas con iconos: perfil activo, **Autoplay / Auto-next**,
   calidad preferida, velocidad, subtítulos por defecto y datos del sistema.
7. **Gestionar / Editar perfil** — modo edición con lápiz sobre cada avatar; el editor
   cambia nombre, color e interruptor de perfil infantil, y permite eliminar.

## 🎮 Controles del mando (D-pad)

| Tecla | Inicio / listas | Reproductor |
| --- | --- | --- |
| **Centro / Enter** | Abrir elemento enfocado | Botón enfocado · mostrar controles |
| **◀ / ▶** | Moverse por las filas | Retroceder / avanzar **10 s** (con controles ocultos) · navegar controles |
| **▲ / ▼** | Moverse entre filas | Mostrar controles · menús de opciones |
| **Atrás** | Volver | Cerrar menú → ocultar controles → salir |

## 🎬 Reproductor estilo Xuper TV

El reproductor replica las funciones del «smart player» de Xuper TV
(investigación completa y fuentes en **[docs/XUPER-TV-RESEARCH.md](docs/XUPER-TV-RESEARCH.md)**):

| Función Xuper TV | En Omni TV |
| --- | --- |
| **EPG en tiempo real** | Franja *AHORA · A CONTINUACIÓN* bajo el título, con el siguiente programa |
| **Multi-servidor** | Cada título tiene *Servidor 1 / Servidor 2*; menú para conmutar y **cambio automático** si el enlace falla (conservando la posición) |
| **Streaming adaptativo** | ABR nativo (HLS) + menú **Calidad**: Automática o pista concreta (1080p · 720p…, con kbps) vía `DefaultTrackSelector` |
| **Audio y subtítulos** | Menús de **pista de audio** e **idioma de subtítulos** (activar/desactivar) vía TrackSelection de Media3 |
| **Relación de aspecto y zoom** | Modos *Ajustar · Llenar · Zoom · 16:9 · 4:3 · 21:9* |
| **Rewind / fast forward** | Botones ⏪ 10 s / 10 s ⏩ + atajos ◀▶ del mando |
| **Control para mando de TV** | Botones grandes de vidrio, menús emergentes navegables con D-pad, botón central grande al pausar |
| **Info del contenido** | Panel *Info* con sinopsis, metadatos, servidor activo, idioma y clasificación |

### Autoplay · Auto-next · y más

| Función | Descripción |
| --- | --- |
| **Autoplay** | Los vídeos empiezan a reproducirse solos al abrirlos (ajustable en *Ajustes → Reproducción*) |
| **Auto-next** | Al terminar un vídeo aparece el panel **«A continuación»** con miniatura, **cuenta atrás de 10 s** y botones *Reproducir ahora* / *Cancelar*; el siguiente título sustituye al actual (sin ensuciar el historial). Botón **⏭ Siguiente** también en la barra de transporte |
| **Velocidad de reproducción** | Menú 0,75× · 1× · 1,25× · 1,5× · 2×, con chip visible en la cabecera y valor recordado entre vídeos |
| **Temporizador de apagado** | 15 / 30 / 60 min: el botón muestra la cuenta atrás ⏱ y al expirar pausa la reproducción |
| **Calidad preferida** | En *Ajustes*: Auto / 4K / 1080p / 720p / 480p — se aplica automáticamente al iniciar cada vídeo |
| **Subtítulos por defecto** | Activa la pista de subtítulos automáticamente si existe |

### Ajustes rediseñados

Tarjetas con iconos y filas con foco: **Perfil** (avatar + cambiar de perfil),
**Reproducción** (Autoplay, Siguiente automático, Inicio silenciado),
**Calidad y velocidad** (calidad preferida, velocidad y subtítulos por defecto,
valores ciclables con «›») y **Sistema** (idioma, motor Media3, versión).
Todo persistido en `SharedPreferences`.

## 👤 Perfiles (como Netflix)

- **Puerta de perfiles**: al arrancar siempre aparece «¿Quién está viendo?».
- **Estado por perfil**: *Continuar viendo* (progreso real del reproductor, se
  elimina al ver ≥ 95 % del título) y *Mi lista* (alta/baja desde la ficha).
- **Perfiles infantiles**: solo ven contenido clasificado TP/7+, en inicio,
  películas y buscador.
- **Gestión**: crear, editar (nombre, avatar ilustrado, modo infantil) y eliminar
  (no se puede borrar el último perfil). Persistido en `SharedPreferences`
  (JSON con `org.json`, sin dependencias).
- **Avatares**: 8 caras vectoriales originales (los assets reales de Netflix son
  material con copyright) inspiradas en sus avatares clásicos, en tiles
  cuadrados redondeados idénticos al selector de Netflix.
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

Sin dependencias de red propias: el cliente Stremio usa `HttpURLConnection` +
`org.json` del propio Android. El catálogo de demostración vive en
`CatalogRepository` y usa **streams públicos de prueba** (bucket de samples de
Google, HLS de prueba de Apple *bipbop* y Mux) — aptos para desarrollar, no para
distribución comercial.

## 🌐 Catálogo real (addons de Stremio)

La app consume **catálogos reales** con el protocolo de addons de Stremio
(`catalog` / `meta`):

| Addon | URL | Contenido |
|---|---|---|
| **Cinemeta** | `https://v3-cinemeta.strem.io` | Populares, Mejor valoradas y series (ids IMDb `tt…`) |
| **Nuvio** | `https://catalog.nuvio.tv` | Tendencias, Netflix/Disney+, documentales y familia (TMDB) |

- Portada con filas reales (Películas populares, Series populares, Tendencias,
  Mejor valoradas, En Netflix, Series Disney+, Documentales, En familia),
  junto a las filas del perfil (*Mi lista*, *Continuar viendo*) y el contenido demo.
- **Búsqueda remota** en ambos addons (`extra: search`) combinada con la local.
- Fichas remotas enriquecidas con `meta/{type}/{id}`: reparto, géneros y
  **episodios** (`videos[]` con temporada/episodio/miniatura) con **selector de
  temporadas**.
- Los addons de catálogo **no incluyen streams**: esos títulos se reproducen
  con un flujo *demo* etiquetado (chip `DEMO`) hasta conectar un addon de
  streams (p. ej. Torrentio). Los perfiles infantiles solo ven el catálogo
  local curado (los addons no dan clasificación por edades).

## 📺 Plataformas (como «Browse by Provider» de cinejoy.pk)

Pestaña **Plataformas** inspirada en el *Browse by Provider* de
[cinejoy.pk](https://cinejoy.pk/): rejilla con las **17 plataformas**
(Netflix, Amazon Prime Video, Disney+, Apple TV+, Hulu, HBO Max, Paramount+,
Peacock, Crunchyroll, Starz, AMC+, MGM+, YouTube Premium, YouTube, Tubi TV,
Pluto TV) con sus logos (CDN público de TMDB, `image.tmdb.org`).

Al elegir una plataforma se carga su **catálogo real**:

- **Películas por plataforma** desde `https://cinejoy.pk/provider/{id}`
  (parser tolerante del HTML del servidor).
- **Películas y series** de Netflix, Disney+ y Prime Video desde los
  catálogos del addon **Nuvio** (`netflix-movies`, `disney-plus-series`, …).
- Los títulos (`tmdb:{id}`) se enriquecen con `meta/{type}/tmdb:{id}` de
  Nuvio (reparto, géneros, clasificación por edades) y sus episodios con la
  meta de Cinemeta. Filtros *Todas / Películas / Series*.
- Perfiles infantiles: sección no disponible (catálogo curado local).

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
│       │   │   ├── Models.kt            # Video, MediaRow, StreamSource
│       │   │   ├── Profile.kt           # perfiles + avatarRes (8 caras)
│       │   │   ├── ProfileStore.kt      # persistencia de perfiles (SharedPreferences)
│       │   │   ├── SettingsStore.kt     # autoplay, auto-next, velocidad, calidad…
│       │   │   └── CatalogRepository.kt # catálogo demo (MP4 + HLS, por perfil)
│       │   ├── player/
│       │   │   ├── PlayerScreen.kt      # reproductor estilo Xuper TV (Media3)
│       │   │   └── PlayerUi.kt          # vidrio, píldoras, menús, EPG chip
│       │   └── ui/
│       │       ├── theme/               # Color, AppType (tipografía), Theme
│       │       ├── components/          # Cards, PillButton, HeroBanner, SeekBar…
│       │       │                        # ProfileComponents: avatares y fichas de perfil
│       │       └── screens/             # Perfiles, Home, Detail, Movies, Search…
│       └── res/
│           ├── drawable/                # tv_banner, iconos y 8 caras de avatar
│           ├── mipmap*/                 # icono adaptativo (lupa con 6 puntos)
│           └── values/                  # strings, colors, themes
├── docs/XUPER-TV-RESEARCH.md            # investigación del reproductor Xuper TV
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
