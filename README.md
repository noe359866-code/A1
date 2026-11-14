# Omni TV · App de streaming para Android TV

<p align="center">
  <em>«Seis pantallas, una experiencia»</em> — una app de streaming <strong>solo para Android TV</strong>,
  construida con <strong>Kotlin</strong>, <strong>Jetpack Compose</strong> y <strong>Media3 ExoPlayer</strong>,
  con un diseño inspirado en la experiencia de <strong>Apple TV / tvOS</strong>.
</p>

---

## ✨ Características

| | |
| --- | --- |
| **Solo Android TV** | `AndroidManifest.xml` con `leanback` obligatorio, `touchscreen` opcional y `LEANBACK_LAUNCHER` + banner 320×180 para el launcher de TV |
| **Diseño Apple TV** | Fondo casi negro `#0B0B0F`, acento azul `#0A84FF`, tarjetas con foco que escalan a 1.08, botones píldora, velos de degradado, tipografía de jerarquía marcada |
| **Compose + foco D-pad** | Componentes con foco dibujados a mano (tarjetas que escalan a 1.08 con borde blanco, píldoras) y `focusRestorer` para que la fila recupere el foco al volver; tema Material 3 sobre Compose |
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
   («98 % para ti», solo si el título trae valoración), chip de tipo
   (**Película · Serie · Anime**), metadatos (sin huecos: lo que el origen no
   trae —año, duración, nota— no se muestra),
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
| **Centro / Enter** | Abrir elemento enfocado · **escribir la tecla enfocada** (buscador) | Botón enfocado · mostrar controles |
| **◀ / ▶** | Moverse por las filas | Retroceder / avanzar **10 s** (con controles ocultos) · navegar controles |
| **▲ / ▼** | Moverse entre filas | Mostrar controles · menús de opciones |
| **Atrás** | Volver · **cerrar el teclado** si está abierto | Cerrar menú → ocultar controles → salir |

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
| Compose Material 2 (`Text`) y Material 3 (tema) | versión del BOM 2024.10.01 |
| Media3 ExoPlayer (+ HLS, ui) | 1.4.1 |
| Navigation Compose | 2.8.4 |
| Coil (imágenes) | 2.6.0 |
| ZXing (códigos QR) | 3.5.3 |
| DNS de Cloudflare (DoH) | `cloudflare-dns.com` · sin dependencias nuevas (`HttpURLConnection`) |
| minSdk / targetSdk / compileSdk | 22 / 34 / 35 |

Sin dependencias de red propias: el cliente Stremio usa `HttpURLConnection` +
`org.json` del propio Android. El catálogo de demostración vive en
`CatalogRepository` y usa **streams públicos de prueba** (bucket de samples de
Google, HLS de prueba de Apple *bipbop* y Mux) — aptos para desarrollar, no para
distribución comercial.

**Red y datos guardados** (ver [Seguridad](#-seguridad-y-datos-guardados)):

- `https://` siempre; `http://` **solo hacia la red local** (`NetPolicy`).
- Si el DNS del router falla o filtra, los nombres se resuelven con el
  **DNS de Cloudflare por HTTPS** (`Doh`): `https://cloudflare-dns.com/dns-query`
  (1.1.1.1), con el certificado validado **contra el nombre original**.
- El panel de vinculación **exige el código** antes de leer o escribir nada.
- Las claves de API se cifran con el **Android Keystore** y quedan **fuera de
  las copias de seguridad**.

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
- La valoración sale **solo del texto visible de esa ficha** (`ScrapeText`).
  Si la web no la muestra (estrenos futuros, por ejemplo), el título queda
  **sin nota**: la interfaz no enseña estrella ni «% para ti», en vez de
  inventarse un 7,0.
- Perfiles infantiles: sección no disponible (catálogo curado local).

## 🎬 Addon interno de streams — «Omni Streams»

El addon **«Omni Streams»** (`data/StreamHub.kt`) tiene una única misión:
**entregar enlaces de reproducción de otros proyectos** (StreamFlix Reborn
y afines, Torrentio, Comet, MediaFusion…), igual que los addons de streams
de Stremio. Él no extrae contenido: solo retransmite lo que los proyectos
externos entregan.

- **Protocolo**: `GET {base}/stream/{tipo}/{id}.json` (forma Stremio) o
  `GET {base}/stream/{tipo}/{id}` (forma simple) — documentado en
  [`docs/STREAM-ADDON-PROTOCOL.md`](docs/STREAM-ADDON-PROTOCOL.md) para
  que cualquier proyecto/fork pueda entregar sus streams.
- **Configuración**: Ajustes → Vincular → «🎬 Añadir addons de streams»
  (o panel web del QR), una URL por línea.
- **Reproducción**: al abrir un título remoto, «Omni Streams» consulta en
  paralelo todos los proyectos (10 s máx.), normaliza y deduplica enlaces
  y los pasa al **reproductor multi-servidor** (menú de servidores con el
  nombre del proyecto, fallback automático si un enlace cae). Si nadie
  entrega enlaces → flujo *demo* etiquetado (chip `DEMO`).
- Soporta `.mp4` y `.m3u8` (HLS); los enlaces solo-torrent (`infoHash`) se
  ignoran de momento.
- **Enlaces aceptados**: `https://` siempre; `http://` solo si el host es una
  IP de la red local (`10.x`, `172.16–31.x`, `192.168.x`, `169.254.x`,
  `127.x`), `localhost` o un nombre `*.local`. Un enlace `http://` hacia
  internet se descarta (el manifiesto permite tráfico sin cifrar porque
  `network_security_config` no sabe expresar rangos de IP; el límite lo pone
  la app).
- **Puente listo para copiar**: `tools/omni-stream-bridge/` (Python y
  Kotlin) para que cualquier proyecto tuyo entregue sus streams a Omni TV
  — solo hay que enganchar tus resolvers en `resolve()`.
  Guía: [`tools/omni-stream-bridge/README.md`](tools/omni-stream-bridge/README.md).

## 🔗 Vinculación por QR (panel web embebido)

La pestaña **Ajustes → «Vincular cuenta · Addons · APIs»** muestra un **QR**
con la URL del panel **debajo** (`http://{ip-del-tv}:8080/`) y el **código de
emparejamiento** (`K7QW9Z`, 6 caracteres sin `0/O/1/I`, generado con
`SecureRandom`). El QR codifica esa URL **con** el código (`?code=…`).

- Al escanear el QR, el móvil abre el **portal web servido por la propia TV**
  (`data/PairingServer.kt`, servidor HTTP embebido en el puerto 8080) y puede:
  - **Enlazar cuenta** (email),
  - **Añadir addons** (URLs de `manifest.json` de Stremio — se cargan como
    filas nuevas del catálogo en caliente),
  - **Añadir APIs** (`NOMBRE=clave`, guardadas para integraciones).
- **El código es la única llave.** El QR lo incluye en la URL
  (`…:8080/?code=K7QW9Z`) y por eso abre el panel ya desbloqueado; si alguien
  escribe `http://{ip-del-tv}:8080/` a mano, solo ve el campo del código.
  Sin el código correcto **no se lee ni se escribe nada del televisor**: ni el
  propio código, ni la cuenta, ni los addons, ni las claves. `GET /status`
  responde `403`, y los formularios fallan sin tocar los ajustes.
- **Freno a la fuerza bruta**: 5 fallos seguidos desde la misma IP bloquean a
  ese cliente 60 s (`429`). Las peticiones están acotadas (16 KB de cabecera,
  64 KB de cuerpo → `413`) y se responden con `no-store`, `no-referrer`,
  `nosniff` y `X-Frame-Options: DENY`.
- Todo se valida y se persiste en `SettingsStore`; el Inicio se recarga
  automáticamente al recibir cambios. El estado en Ajustes es en vivo:
  servidor, cuenta, nº de addons y APIs, si las claves están cifradas y la
  última acción recibida.
- Limitación honesta: el panel viaja **sin TLS** por la red local. En una
  Wi-Fi ajena (hotel, cafetería) cualquiera puede intentar el código; por eso
  hay límite de intentos y el código se genera con `SecureRandom`. Úsalo en tu
  red de casa.
- QR generado con **ZXing** (`com.google.zxing:core:3.5.3`).

## 🎬 «Por estudio» (como «Browse by studio» de sweetfox.app)

Sección del Inicio **«Por estudio»** — inspirada en el *Browse by studio* de
[sweetfox.app](https://sweetfox.app/) — y bloque **«Estudios»** dentro de la
pestaña Plataformas (fusión de ambos modos de explorar):

- Tira/rejilla con los **24 estudios** del original: DreamWorks Animation,
  Marvel, LAIKA, Lionsgate, Blumhouse, NEON, Legendary, 20th Century, New
  Line, Walt Disney, Illumination, Pixar, Universal, TOHO, A24, Focus
  Features, Columbia, Searchlight, Studio Ghibli, Amblin, Lucasfilm, DC,
  Paramount y Warner Bros. (logos del CDN público de TMDB).
- Al elegir un estudio se carga su **catálogo real** desde
  `https://sweetfox.app/company/{id}` (parser tolerante del HTML del
  servidor) con filtros *Todas / Películas / Series* y orden
  *Populares / Mejor valoradas / Recientes* (como las pestañas de la web).
- Los títulos (`tmdb:{id}`) se enriquecen con la meta de Nuvio (reparto,
  géneros, certificación) y sus episodios con Cinemeta. Caché por estudio.
- Igual que en Plataformas: sin valoración en la ficha → sin nota en la app
  (caso real: «Avengers: Doomsday» aparece como `Movie2026`, sin nota).

## 🎯 «Solo en {plataforma}» (como «Only on» de flystream.net)

Sección del Inicio inspirada en el *Only on Netflix* de
[flystream.net](https://flystream.net/): título dinámico **«Solo en Netflix /
HBO Max / Disney+…»** con una **tira de logos que actúa de selector**
(Netflix, Prime Video, HBO Max, Disney+, Apple TV+, Paramount+, Hulu + chip
**«Más ›»** que abre la pestaña Plataformas). Al elegir un logo se carga su
catálogo real (cinejoy.pk + Nuvio, con caché por plataforma) en una fila de
tarjetas con cierre **«Ver todo»** → catálogo completo de esa plataforma.

## 📁 Estructura del proyecto

```
A1/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml      # leanback, banner TV, sin touchscreen
│       │   ├── java/com/omnitv/app/
│       │   │   ├── MainActivity.kt      # ComponentActivity + keep-screen-on
│       │   │   ├── OmniTvApp.kt         # NavHost + pestañas + rutas
│       │   │   ├── data/
│       │   │   │   ├── Models.kt            # Video, Episode, MediaRow, StreamSource
│       │   │   │   ├── Profile.kt           # perfiles + avatarRes (8 caras)
│       │   │   │   ├── ProfileStore.kt      # persistencia de perfiles (SharedPreferences)
│       │   │   │   ├── SettingsStore.kt     # ajustes + vinculación (cuenta/addons/APIs)
│       │   │   │   ├── CatalogRepository.kt # catálogo demo + caché remoto + episodios
│       │   │   │   ├── StremioClient.kt     # protocolo de addons Stremio (Cinemeta/Nuvio)
│       │   │   │   ├── RemoteCatalog.kt     # filas remotas, plataformas, estudios, búsqueda
│       │   │   │   ├── CinejoyClient.kt     # catálogo por plataforma (cinejoy.pk)
│       │   │   │   ├── SweetFoxClient.kt    # catálogo por estudio (sweetfox.app)
│       │   │   │   ├── ScrapeText.kt        # lectura del HTML: un dato ausente no se inventa
│       │   │   │   ├── Providers.kt         # 17 plataformas (logos TMDB)
│       │   │   │   ├── Studios.kt           # 24 estudios (logos TMDB)
│       │   │   │   ├── NetPolicy.kt         # URLs aceptadas (https; http solo en la LAN)
│       │   │   │   ├── Doh.kt               # DNS de Cloudflare por HTTPS (red de seguridad)
│       │   │   │   ├── SecretBox.kt         # cifrado de secretos (Android Keystore AES-GCM)
│       │   │   │   ├── PairingServer.kt     # servidor web :8080 (sockets)
│       │   │   │   ├── PairingRouter.kt     # reglas del panel: sin código no hay datos
│       │   │   │   ├── PairingPortal.kt     # HTML del panel (bloqueado y desbloqueado)
│       │   │   │   └── PairingHttp.kt       # lectura HTTP, formularios, límite de intentos
│       │   │   ├── player/
│       │   │   │   ├── PlayerScreen.kt      # reproductor estilo Xuper TV (Media3)
│       │   │   │   └── PlayerUi.kt          # vidrio, píldoras, menús, EPG chip
│       │   │   └── ui/
│       │   │       ├── theme/               # Color, AppType (tipografía), Theme
│       │   │       ├── components/          # Cards, PillButton, HeroBanner, OnlyOnSection…
│       │   │       │                        # ProfileComponents, StudioSection
│       │   │       └── screens/             # Perfiles, Home, Detail, Plataformas,
│       │   │                                # StudioCatalog, Search, Settings…
│       │   └── res/
│       │       ├── drawable/                # tv_banner, iconos y 8 caras de avatar
│       │       ├── mipmap*/                 # icono adaptativo (lupa con 6 puntos)
│       │       ├── values/                  # strings, colors, themes
│       │       └── xml/                     # backup_rules / data_extraction_rules
│       └── test/java/com/omnitv/app/data/   # tests unitarios de JVM (sin Android)
├── .github/workflows/ci.yml             # tests + assembleDebug en cada push
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

# Tests unitarios de JVM (red, panel de vinculación y parsers)
./gradlew testDebugUnitTest
```

La integración continua (`.github/workflows/ci.yml`) ejecuta los tests y
`assembleDebug` en cada push y pull request.

> **Emulador:** crea un AVD con perfil *Android TV (1080p)*, API 22+ (recomendado 34).
> La app solo aparece en el launcher de TV (categoría `LEANBACK_LAUNCHER`).

## 🔐 Seguridad y datos guardados

Lo que la app promete es lo que hace:

| Dato | Dónde vive | Cifrado | Copia de seguridad |
| --- | --- | --- | --- |
| Claves de API (`NOMBRE=clave`) | `SharedPreferences` `omni_tv_settings` | **AES-256-GCM con clave del Android Keystore** (`SecretBox`) en Android 6.0+ | Excluidas |
| Cuenta enlazada, addons y addons de streams | `omni_tv_settings` | No (URLs y email) | Excluidas |
| Código de emparejamiento | `omni_tv_settings` | No | Excluido |
| Perfiles y *Mi lista* | `SharedPreferences` de perfiles | No | Incluidas (no contienen secretos) |

- **`allowBackup` sigue activo**, pero `res/xml/backup_rules.xml` (API 30-) y
  `res/xml/data_extraction_rules.xml` (API 31+) **excluyen el fichero
  `omni_tv_settings.xml`** tanto de la copia en la nube como del traslado a un
  dispositivo nuevo: los secretos nunca salen del televisor.
- El cifrado usa una clave que **no sale del Keystore**; el valor guardado es
  hexadecimal (`longitud-IV · IV · texto cifrado + etiqueta GCM`). Las claves
  antiguas en texto plano se **migran** al primer arranque.
- **Android 5.1 (API 22) no tiene Keystore AES**: allí las claves se guardan sin
  cifrar y la interfaz lo dice («Sin cifrar») en lugar de prometer un cifrado
  que no existe. Desde Android 6.0 se muestra «Cifradas en el televisor».
- Si el dato cifrado no se puede abrir (copia restaurada en otro aparato, dato
  alterado), la app arranca **sin claves** en vez de con basura: hay que
  volver a introducirlas.
- El panel de vinculación y la política de red (`NetPolicy`) se describen en
  sus secciones. `http://` sin cifrar nunca sale de la red local.

## 🌐 DNS de Cloudflare (DoH)

En Android TV hay routers de operador, cajas y Wi-Fi de hotel donde el DNS
falla, filtra o responde lento: los addons se quedan cargando sin decir por
qué. Omni TV resuelve los nombres con el **DNS de Cloudflare por HTTPS**
(DNS over HTTPS), que viaja cifrado y no depende del DNS del router.

- **Endpoint**: `https://cloudflare-dns.com/dns-query` con
  `Accept: application/dns-json` (el JSON documentado de 1.1.1.1), consultas
  `A` y `AAAA`. Sin librerías nuevas: `HttpURLConnection` + `org.json`.
- **Cuándo**: solo como **red de seguridad**. Cada cliente (Cinemeta/Nuvio,
  addons de streams, cinejoy, sweetfox) intenta primero su conexión normal; si
  el nombre no resuelve, reintenta resolviendo por Cloudflare.
- **Solo `https://`**. Un `http://` nunca sale por el resolutor público:
  `NetPolicy` solo lo permite en la red local, donde no hace falta.
- **El certificado se valida contra el nombre original**, no contra la IP
  (SNI + verificador del sistema). Saber la IP de Cloudflare no permite que
  nadie se haga pasar por el servidor.
- **Si DoH falla** (sin red, puerto bloqueado, respuesta rara), la app se queda
  como estaba: sin datos. Nunca se pintan datos de un tercero.
- **Caché** en memoria (64 nombres) con el TTL que anuncia el DNS, acotado
  entre 30 s y 5 min. Los errores (`Status != 0`, CNAME, texto) se descartan.
- **Interruptor**: Ajustes → **Red → «DNS de Cloudflare»** (activo por
  defecto). Apagado = comportamiento clásico, solo el DNS del sistema.

## ⌨️ Buscar con el mando (teclado propio)

Con un mando y sin teclado físico, buscar era el punto más frágil de la app: el
campo recibía el foco, se pulsaba OK y **no pasaba nada** —el IME de Android TV
no se abre de forma fiable desde Compose, y varía según el fabricante—. Se
resolvió con un teclado de la propia app.

- **Cuatro filas de caracteres** (37 teclas): `1234567890` · `QWERTYUIOP` ·
  `ASDFGHJKLÑ` · `ZXCVBNM`, más ESPACIO / BORRAR / LISTO en una quinta fila.
  Mayúsculas y con Ñ, para escribir nombres propios sin pelear con el mando.
  Sin teclas de tildes: se resuelven en la búsqueda (ver abajo), y cada tecla
  que sobra son varias pulsaciones de D-pad de menos.
- **Se abre solo** al entrar en Buscar (el foco va al campo y de ahí a la
  primera tecla), con **animación de entrada** desde abajo. Se cierra con
  LISTO o con **Atrás**, y en ambos casos el foco vuelve al campo.
- **Se superpone** a la mitad inferior en vez de empujar el contenido: con 342 dp
  de alto no cabe debajo del buscador sin dejar los resultados en una franja
  inútil. Así se sigue viendo lo que va saliendo mientras se escribe; al cerrar,
  la lista completa ocupa la pantalla.
- **Teclas de 54 dp** (por encima del mínimo de 48 para un objetivo de mando),
  filas centradas en columna para que el D-pad baje en vertical y no en diagonal.
- **La búsqueda ignora acentos y mayúsculas**: «accion» encuentra «Acción» y
  «PELICULAS» encuentra «Películas» (`SearchText`). Es lo que hace prescindible
  el teclado de tildes.
- **No captura teclas globalmente**: el foco vive dentro del teclado, así que un
  teclado físico o uno Bluetooth siguen escribiendo en el campo sin estorbar, y
  las teclas del reproductor siguen funcionando donde deben.

El modelo (`TvKeypad`) es lógica pura y se prueba con tests de JVM: qué escribe
cada tecla, que BORRAR con el campo vacío no rompa nada, que no se acumulen
espacios (pulsar ESPACIO dos veces con el mando es lo más habitual) y el tope de
64 caracteres.

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
- **Tipografía de 10 pies**: pesos altos, interletraje cerrado y jerarquía muy
  marcada (54 / 40 / 27 / 21 / 18 / 15 sp). El cuerpo son 21 sp, no los 16 de
  móvil: a 2–3 m del sofá, 16 sp no se lee. El mínimo de la app son 15 sp.
- **Foco siempre visible**: anillo blanco de 3 dp (no solo cambio de fondo) en
  tarjetas, pestañas, chips, interruptores y botones, más escala de 1,08 y
  sombra al enfocar. Regla única: **foco = blanco, seleccionado = azul**; si el
  relleno es claro (botón primario, pestaña activa) el anillo va oscuro.
- **Sistema de diseño** (`ui/theme/Dimens.kt`): cuatro radios de esquina
  (4 / 8 / 14 / 22 dp) más cápsula, una escala de espaciado y un solo grosor de
  foco. Antes había dieciséis radios y cuatro grosores distintos elegidos a ojo.
- **Un componente por patrón**: `TvChip` sustituye las cuatro copias de chip que
  había (sugerencias, filtros de plataforma, filtros de estudio, temporadas) con
  tres rellenos y dos colores distintos.
- **Contraste**: el texto atenuado usa `TextMuted` (≈ 6:1 sobre el fondo); el
  gris `TextTertiary` (2,5:1) se queda solo para adornos no textuales.
- **El mando manda**: cada pantalla entra con el foco donde el usuario espera
  («Reproducir» en la ficha, el campo en Buscar, el primer perfil, el héroe en
  Inicio), y las filas sin acción propia no son focos alcanzables.
- **Teclado propio, no el del sistema**: el buscador trae su teclado en pantalla
  (`TvKeyboard`). En Android TV el IME no se abre de forma fiable con Compose, y
  el usuario acababa pulsando «Atrás» a ciegas; con teclado propio se ve siempre
  y responde al D-pad.
- **Zona segura**: 48 dp de margen (el overscan del 5 % de los paneles) para
  botones, textos y rejillas; rejillas de 5 columnas y héroe proporcional al
  alto de pantalla.

## 📄 Licencia

MIT — los streams de demostración pertenecen a sus respectivos autores
(Blender Foundation, Google sample bucket, Apple, Mux) y se usan solo con fines
de prueba.
