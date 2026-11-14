# Omni TV — Streaming para Android TV

<p align="center">
  <strong>Experiencia premium para Android TV</strong><br>
  Kotlin · Jetpack Compose · Media3 ExoPlayer · Diseño tvOS
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20TV-0A84FF?style=for-the-badge" alt="Android TV">
  <img src="https://img.shields.io/badge/Kotlin-2.0.20-7F52FF?style=flat-square" alt="Kotlin">
  <img src="https://img.shields.io/badge/Compose-BOM%202024.10-4285F4?style=flat-square" alt="Compose">
  <img src="https://img.shields.io/badge/Media3-1.4.1-34A853?style=flat-square" alt="Media3">
  <img src="https://img.shields.io/badge/minSdk-22-000000?style=flat-square" alt="minSdk">
</p>

---

## Visión

Omni TV es una aplicación **exclusiva para Android TV / Google TV** con estándares de producción: sin contenido de demostración, sin datos falsos y sin atajos. Todo el catálogo proviene de fuentes reales (Cinemeta, Nuvio, cinejoy, sweetfox) y los enlaces de reproducción se resuelven exclusivamente a través de addons configurados por el usuario.

**Principios:**

- **Cero demo:** ningún vídeo, imagen o texto de prueba en la app instalada.
- **Diseño tvOS:** fondo #0B0B0F, acento #0A84FF, tipografía de 10 pies, foco D-pad de 3dp.
- **Privacidad local:** addons y claves nunca salen del televisor; backups excluidos por sistema.
- **Red honesta:** HTTPS siempre; HTTP solo en LAN; DoH opcional con validación de certificado.

---

## ✨ Características

| Área | Detalle |
|------|---------|
| **Solo Android TV** | `leanback` requerido, `touchscreen` opcional, `LEANBACK_LAUNCHER` + banner 320×180 |
| **Diseño Apple TV** | Superficies #16161C, tarjetas con escala 1.08 y borde blanco, botones píldora, velos degradados |
| **Navegación D-pad** | Pestañas tvOS, `focusRestorer`, foco inicial inteligente en cada pantalla |
| **Perfiles Netflix** | Selector con avatares ilustrados, modo infantil, Mi Lista y Continuar viendo por perfil |
| **Reproductor Xuper TV** | EPG Ahora/A continuación, multi-servidor, ABR HLS, audio/subs/calidad, zoom, ±10s, sleep timer |
| **Catálogo real** | Cinemeta + Nuvio + addons del usuario + cinejoy (plataformas) + sweetfox (estudios) |
| **Búsqueda con mando** | Teclado en pantalla propio (37 teclas), sin acentos, búsqueda insensible a mayúsculas |
| **Vinculación segura** | Servidor :8080 + QR + código de 6 caracteres, rate-limit, cifrado Keystore AES-GCM |

---

## 📺 Pantallas

| # | Pantalla | Descripción |
|---|----------|-------------|
| 0 | **Perfiles** | "¿Quién está viendo?" con avatares, KIDS chip, Gestionar/Añadir |
| 1 | **Inicio** | Héroe cinematográfico + filas dinámicas (Mi Lista, Continuar viendo, catálogo real, Solo en..., Por estudio) |
| 2 | **Películas** | Rejilla 5 columnas del catálogo real, contador y estados vacíos profesionales |
| 3 | **Plataformas** | 17 proveedores (Netflix, Disney+, HBO...) + 24 estudios (Marvel, Pixar, Ghibli...) con catálogos reales |
| 4 | **Buscar** | Campo + chips de género + teclado D-pad + resultados combinados local/remoto |
| 5 | **Detalle** | Fondo con backdrop + velos, match %, metadatos completos, episodios, reparto, Más como esto |
| 6 | **Reproductor** | Media3 con todos los controles tvOS: EPG, servidores, calidad, audio, subtítulos, aspecto, velocidad, timer |
| 7 | **Ajustes** | Tarjetas: Perfil, Vinculación (QR), Reproducción, Calidad, Red (DoH), Sistema |

---

## 🎮 Mando a distancia

| Tecla | Navegación | Reproductor |
|-------|-----------|-------------|
| **Centro** | Abrir / Escribir tecla | Mostrar controles |
| **◀ ▶** | Moverse en fila | ±10s (controles ocultos) / navegar |
| **▲ ▼** | Cambiar de fila | Mostrar controles / menús |
| **Atrás** | Volver / Cerrar teclado | Cerrar menú → ocultar → salir |

---

## 🎬 Reproductor profesional

Basado en Media3 ExoPlayer con investigación de Xuper TV:

- **EPG:** Franja AHORA / A CONTINUACIÓN con programa siguiente
- **Multi-servidor:** Conmutación automática conservando posición
- **Adaptativo:** HLS ABR + selector manual (Auto / 1080p / 720p...)
- **Pistas:** Audio, subtítulos, calidad vía `DefaultTrackSelector`
- **Aspecto:** Ajustar / Llenar / Zoom / 16:9 / 4:3 / 21:9
- **Transporte:** ⏪10s / 10s⏩, barra con buffer, D-pad directo
- **Autoplay / Auto-next:** Cuenta atrás 10s con miniatura, botón Siguiente
- **Extras:** Velocidad (0.75×–2×), sleep timer (15/30/60m), calidad preferida

---

## 👤 Perfiles

- Selector obligatorio al iniciar
- Estado aislado por perfil: progreso (95% = visto, <2% = descartado) y Mi Lista
- Infantil: filtra solo TP/7+ automáticamente (sin contenido externo en modo kids hasta que el catálogo traiga clasificación)
- CRUD completo con validación, persistido en `SharedPreferences` JSON
- 8 avatares vectoriales originales · cambio rápido desde barra superior

---

## 🌐 Catálogo y plataformas

| Fuente | Contenido |
|--------|-----------|
| **Cinemeta** `v3-cinemeta.strem.io` | Películas/series populares, mejor valoradas, top IMDb (`tt…`) |
| **Nuvio** `catalog.nuvio.tv` | Tendencias, Netflix/Disney+, documentales, familia (TMDB) |
| **cinejoy.pk** | 17 plataformas con catálogo por proveedor (parser HTML tolerante) |
| **sweetfox.app** | 24 estudios con catálogos reales (parser con filtros y orden) |
| **Addons usuario** | Manifests Stremio añadidos vía QR/panel web |

> Todos los parsers son tolerantes: si la web cambia, la sección aparece vacía con mensaje profesional — nunca con datos falsos.

---

## 🔗 Addons de streams — StreamHub

`StreamHub` orquesta los enlaces de reproducción:

- **Protocolo:** `GET {base}/stream/{type}/{id}.json` (Stremio) o `GET {base}/stream/{type}/{id}` (simple) — ver `docs/STREAM-ADDON-PROTOCOL.md`
- **Configuración:** Ajustes → Vincular → añadir URLs de streams
- **Flujo:** consulta paralela (timeout 10s), deduplicación, límite 24 enlaces, paso al reproductor multi-servidor
- **Política de red:** solo `https://` hacia internet; `http://` únicamente en LAN privada — validado por `NetPolicy`
- **Sin fallback demo:** si no hay enlaces, mensaje claro "Sin fuentes disponibles · configura un addon"

Puente de referencia: `tools/omni-stream-bridge/` (Python + Kotlin) — conecta tus resolvers en `resolve()`.

---

## 🔐 Seguridad

| Dato | Almacenamiento | Cifrado | Backup |
|------|---------------|---------|--------|
| Claves API `NOMBRE=clave` | `omni_tv_settings` | AES-256-GCM Keystore (Android 6+) | **Excluido** |
| Cuenta / addons / streams | `omni_tv_settings` | No (URLs) | **Excluido** |
| Código emparejamiento | `omni_tv_settings` | No | **Excluido** |
| Perfiles / Mi Lista | `omni_tv_profiles` | No | Incluido |

- `backup_rules.xml` (API ≤30) y `data_extraction_rules.xml` (API ≥31) excluyen `omni_tv_settings.xml`
- Claves migradas automáticamente de texto plano → cifrado en primer arranque
- API 22 (sin Keystore AES): guardado sin cifrar con aviso honesto en UI
- Panel web: código de 6 caracteres `SecureRandom`, rate-limit 5 intentos → 60s bloqueo, límites 16KB/64KB, headers `no-store`/`nosniff`/`DENY`

---

## 🌐 DoH — DNS de Cloudflare

Para routers con DNS roto o filtrado (operadores, hoteles):

- Endpoint `https://cloudflare-dns.com/dns-query` (`application/dns-json`)
- Solo como red de seguridad tras fallo del DNS del sistema
- Solo `https://`; certificado validado contra el nombre original (SNI)
- Caché 64 entradas, TTL 30s–5min (anunciado por DNS)
- Interruptor en Ajustes → Red → DNS de Cloudflare (activo por defecto)

---

## ⌨️ Teclado para mando

Problema: el IME de Android TV no se abre de forma fiable desde Compose.

Solución: teclado propio superpuesto:

- 37 teclas (0-9, QWERTY+Ñ, Espacio/Borrar/Listo), 54dp mínimo táctil
- Apertura automática al enfocar Buscar, cierre con Listo/Atrás
- Superpuesto (no empuja contenido), animación desde abajo
- Búsqueda normalizada: "accion" → "Acción" (SearchText sin acentos)

---

## 🎨 Sistema de diseño

- **Paleta:** Background #0B0B0F · Surface #16161C · Elevated #1F1F26 · Accent #0A84FF · Success #30D158
- **Tipografía 10 pies:** hero 54sp · title 40sp · section 27sp · body 21sp · metadata 18sp · chip 15sp (mínimo legible a 3m)
- **Geometría:** radios 4/8/14/22dp + cápsula, espaciado 4–48dp, margen seguro 48dp, 5 columnas
- **Foco:** anillo 3dp blanco siempre visible + escala 1.08 + sombra; regla: foco=blanco, seleccionado=azul
- **Contraste:** TextMuted 6:1 para texto; TextTertiary solo para adornos
- **Componentes unificados:** PosterCard, PillButton, TvChip, TvToggle, HeroBanner, SectionRow — un patrón, una implementación

---

## 📁 Estructura

```
A1/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/omnitv/app/
│       │   ├── MainActivity.kt
│       │   ├── OmniTvApp.kt
│       │   ├── data/          # Models, Profile, Stores, Catalog, Stremio, Remote, Cinejoy, SweetFox, NetPolicy, Doh, SecretBox, Pairing
│       │   ├── player/        # PlayerScreen (Media3) + PlayerUi
│       │   └── ui/
│       │       ├── theme/     # Color, AppType, Theme, Dimens
│       │       ├── components/# Cards, Hero, Pills, Keyboard, Profile tiles
│       │       └── screens/   # Home, Movies, Search, Detail, Platforms, Settings...
│       └── res/drawable/ + mipmap/ + values/ + xml/
├── gradle/libs.versions.toml
├── docs/  STREAM-ADDON-PROTOCOL.md  XUPER-TV-RESEARCH.md
└── tools/omni-stream-bridge/
```

---

## 🚀 Compilación

```bash
# Requisitos: JDK 17, Android SDK 35, Android Studio Ladybug+
./gradlew installDebug          # Instalar en TV/emulador conectado
./gradlew assembleDebug         # APK debug
./gradlew testDebugUnitTest     # Tests JVM
./gradlew lint                  # Lint leanback
```

CI (`.github/workflows/ci.yml`): tests + assembleDebug en cada push/PR.

> **Emulador:** AVD Android TV (1080p), API 34 recomendado. App solo en launcher TV (`LEANBACK_LAUNCHER`).

---

## 📄 Licencia

MIT — Omni TV es software original. Logos de plataformas/estudios vía CDN público de TMDB. Contenido de catálogos pertenece a sus proveedores (Cinemeta/Nuvio/cinejoy/sweetfox).

---

<p align="center"><em>Omni TV — Diseñado para la distancia del sofá.</em></p>
