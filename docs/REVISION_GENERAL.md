# Revisión general del proyecto — Omni TV

**Fecha:** 17 de noviembre de 2026 · **Rama revisada:** `arena/30f67343-a1` (base `fa4b364`)
**Alcance:** todo el repositorio — 209 archivos versionados, 19 899 líneas Kotlin en `main`, 251 tests unitarios, 3 workflows de CI, 15 documentos, 4 puentes externos.

---

## 1. Veredicto

**El proyecto está en buen estado y es compilable/publicable hoy.** No encontré errores de compilación, recursos rotos ni fallos de seguridad graves en la app. La arquitectura de datos es notablemente disciplinada (un solo cliente HTTP, política de red centralizada, cifrado de secretos, panel de vinculación con sesiones). Los dos últimos runs de CI sobre `main` están **verdes** (`CI` 6m26s y `Generar APK` 4m35s, hace ~7 h).

Los problemas reales están en tres frentes, ninguno bloqueante:

1. **Una función de seguridad cableada a medias** (`onPreviewTab` muerto) — el gate de PIN existe pero no se invoca.
2. **Dos puentes de desarrollo escuchan en `0.0.0.0` sin autenticación**, a diferencia del tercero que sí la exige.
3. **La documentación de diseño describe la barra superior anterior**, no la sidebar que entró en el último commit.

---

## 2. Qué verifiqué (y qué no pude)

| Comprobación | Resultado |
|---|---|
| `python3 tools/check_build_readiness.py` | ✅ OK (1 aviso: sin keystore de producción → firma debug) |
| `python3 tools/check_android_tv.py` | ✅ Contrato TV OK (leanback, banner 16:9, sin launcher móvil) |
| `node --test` puentes nuvio / cloudstream / ffmpeg | ✅ 1 / 2 / 4 tests, 0 fallos |
| CI en GitHub (`gh run list`) | ✅ últimos runs de `main` en `success` |
| Versiones de las actions (`checkout@v7`, `setup-java@v6`, `upload-artifact@v7`, `gradle/actions@v6`, `softprops@v2`) | ✅ todas existen y son las vigentes |
| Referencias a recursos (`R.drawable/mipmap/string`) | ✅ ninguna rota |
| APIs por encima de `minSdk 22` (`java.time`, `Base64`, `List.of`…) | ✅ ninguna |
| TODO/FIXME/HACK pendientes | ✅ cero |

**No pude ejecutar** (el entorno no tiene JDK ni Android SDK): `gradlew testDebugUnitTest`, `lintDebug`, `assembleRelease` (R8/shrink) y nada relativo a dispositivo. Esas tres cosas las cubre el CI en la nube, que está verde. **El foco D-pad, el overscan y la reproducción real siguen pendientes de validación en televisor**, como ya advierte el propio `check_android_tv.py`.

---

## 3. Lo que está bien (y conviene no tocar)

- **`NetPolicy`** es sólida y honesta: rechaza `userinfo` (`usuario:clave@host`), solo acepta IPv4 decimal canónico (sin ceros a la izquierda, sin hex), cubre IPv6 de loopback/ULA/link-local y **nunca resuelve DNS** para decidir.
- **`AddonHttp`** como único cliente HTTP: tope de 4 MB, redirecciones revalidadas con `NetPolicy`, `POST` no se reenvía, y las credenciales (`Authorization`, `X-API-Key`, `X-Bridge-Token`) **no cruzan a otro origen**. DoH solo se activa tras `UnknownHostException`, no ante un 404.
- **Panel de vinculación**: la invariante "sin código válido o sesión vigente no se lee ni se escribe nada" se cumple. Código de 6 caracteres con `SecureRandom`, comparación en tiempo constante, cookie `HttpOnly`/`SameSite=Strict` ligada a IP + huella del código, `AttemptGuard` con memoria acotada (512 clientes), rechazo de origen cruzado antes de validar el código, 413/429, y `no-store`/`nosniff`/`DENY`.
- **`SecretBox`**: AES-256-GCM con clave del Keystore, migración automática desde texto plano, y en API 22 (`sin Keystore AES`) la UI **lo dice** en vez de prometer cifrado inexistente. Los backups excluyen `omni_tv_settings.xml` en ambas políticas (`backup_rules` y `data_extraction_rules`).
- **PIN parental**: SHA-256 con salt de 16 bytes, comparación en tiempo constante, y `PinAttemptGuard` **a nivel de aplicación** — evita el agujero clásico de reiniciar el contador cerrando y reabriendo el diálogo.
- **`StreamResponse.sanitizeHeaders`**: bloquea cabeceras peligrosas (`host`, `cookie`, `content-length`…), saltos de línea y limita a 16 cabeceras / 2048 caracteres.
- **Reproductor**: un `ExoPlayer` por `video.id`, `release()` en `onDispose`, guardado de progreso al pausar y cada 10 s, scrobble de Trakt con umbral de 10 s para evitar marcados espurios.
- **Tests**: 251 casos concentrados donde más se rompe en silencio (parsers, `NetPolicy`, pairing, ranking, skip segments). `PairingRouterTest` con 28 casos es un ejemplo a seguir.
- **CI**: usa `pipefail` (sin él un fallo de Gradle pasaría por bueno), anota errores de lint y fallos de test, y valida el proyecto en segundos antes de gastar 10 min de Gradle.

---

## 4. Hallazgos

### 🔴 Alta — **ambas corregidas** (ver §7)

#### H1 · `onPreviewTab` nunca se invoca: el gate de PIN desde la navegación es código muerto

`OmniTvApp.kt:154` construye un gate que impide entrar en Ajustes desde la barra si el perfil es infantil y hay PIN:

```kotlin
onPreviewTab = { tab -> … if (!kidsPinGate) navController.selectTab(tab) }
```

Pero `TvScaffold` **acepta** `onPreviewTab` (`Components.kt:290`) y lo **ignora**: pasa `onPreviewSelect = null` de forma literal (`Components.kt:348`), y `TopTabBar` nunca llama a `onPreviewSelect` en ningún punto.

**Impacto:** el código sugiere una protección que no está activa. Hoy **no hay agujero** porque `SettingsScreen.kt:158` vuelve a exigir el PIN con su propio `PinDialog`; el resultado es una defensa redundante de la que solo funciona una mitad.

**Recomendación (una de dos, no ambas):** o se cablea de verdad (`SidebarItem` → `onPreviewSelect` antes de `onSelect`, y `TvScaffold` lo propaga), o se elimina el parámetro y el bloque `kidsPinGate` para que nadie lea una garantía inexistente. Yo elegiría **eliminarlo**: el gate de `SettingsScreen` ya cubre el caso y un solo punto de control es más auditable que dos.

#### H2 · Puentes `nuvio` y `cloudstream` escuchan en `0.0.0.0` sin autenticación — **corregido**

| Puente | Host por defecto | Token/allowlist |
|---|---|---|
| `ffmpeg-bridge` | `127.0.0.1` | **exige `--token` fuera de loopback** (`parseConfig`, línea 198) |
| `nuvio-bridge` | `0.0.0.0` | ninguno |
| `cloudstream-bridge` | `0.0.0.0` | ninguno |

Ambos se documentan como `--host 0.0.0.0` en `docs/NUVIO_INVESTIGACION.md:41` y `docs/CLOUDSTREAM_BRIDGE.md:12`. Cualquiera en la misma red puede enviar `POST /scrape/{id}` o `POST /resolve`: el puente Cloudstream **lanza un proceso externo por petición** (`spawn(runtime, …)`) y el de Nuvio ejecuta JS de proveedores en un worker. Hay límites de concurrencia (`active >= 2/6`) y de tamaño (64 KB), así que no es una denegación trivial, pero es una superficie innecesaria en una LAN doméstica.

**Recomendación:** replicar el criterio de `ffmpeg-bridge` — exigir token cuando el host no sea loopback, y compararlo en tiempo constante (ya existe el patrón con SHA-256 en `authorized()`); opcionalmente, limitar por IP del televisor. El puente FFmpeg ya es el modelo correcto: úsese como plantilla.

### 🟠 Media

#### M1 · La documentación de diseño describe la barra superior, no la sidebar

El último commit sustituyó las pestañas superiores por una barra lateral de 76 dp que se expande a 224 dp con el foco. La documentación no se actualizó:

| Archivo | Qué dice ahora |
|---|---|
| `README.md:190` | «Barra de 6 pestañas: la activa/enfocada muestra etiqueta; el resto se compacta a icono» |
| `docs/DPAD_FOCUS.md:14` | «`TvScaffold` + `TopTabBar` + `TabPill` … preview con retardo de 150 ms» — **ni `TabPill` ni ese retardo existen** |
| `docs/APPLE_TV_DESIGN_REVIEW.md:39-40,71,154` | métricas de `TopTabBar`: padding superior 22→32 dp, ~86 dp de alto, línea separadora |
| `docs/ANIME_CATALOG.md:37` | «la barra superior compacta las no activas» |

`docs/DPAD_FOCUS.md` es el documento que alguien leería para tocar el foco D-pad; que describa un componente y un comportamiento inexistentes es el riesgo más alto de este bloque.

#### M2 · Nombres que ya no describen lo que hacen

- `TopTabBar` (`Components.kt:144`) es ahora una **barra lateral**. Renombrar a `TvSidebar` / `SidebarNav`.
- `LocalActiveTabFocus` / `TabContentFocusTarget` / `LocalSuppressContentAutoFocus` y sus KDoc en `TvFocus.kt:41-62` siguen hablando de «barra superior», «pestaña activa» y «`Abajo` en la barra». El mecanismo es correcto (lo verifican las 6 pantallas, que registran su primer foco con `SideEffect { tabContentFocus?.focus = … }`), pero se lee al revés.
- `FocusScale.tab` documentado como «pestañas y filas secundarias» (`Dimens.kt`).

#### M3 · La sidebar reduce el ancho útil del contenido: validar overscan y rejilla

Con la sidebar el contenido pierde entre 76 y 224 dp. Sumado a `AppSpacing.screenMargin = 48 dp`, el margen izquierdo efectivo va de ~124 a ~272 dp en 1080p. La rejilla de 5 columnas de Películas y el `HeroBanner` son los candidatos a apretarse o recortarse con overscan real. **Requiere validación en televisor**; no es medible desde el código y por eso no lo marco como alta.

#### M4 · Código muerto acumulado tras el cambio de navegación

| Elemento | Ubicación |
|---|---|
| `if (railHasFocus) trailing() else trailing()` — ambas ramas idénticas | `Components.kt:212-217` |
| `SidebarItem.onFocus` — se invoca pero es `{ }` en el único llamador | `Components.kt:204`, `223` |
| `onPreviewSelect` — declarado, nunca invocado | `Components.kt:152` |
| `GlassDark` — token de color sin uso | `Color.kt:69` |
| `TraktService.isLinked()` — sin llamadas | `TraktService.kt:28` |
| `TraktProtocol.ItemKind` — enum sin uso | `TraktProtocol.kt:46` |

Ninguno rompe nada; todos son ruido que envejece mal y confunde en la próxima revisión.

#### M5 · Sin cobertura de UI: justo lo que acaba de cambiar

251 tests, casi todos en `data/`. No hay ningún test de Compose ni de navegación; `RoutesTest` tiene solo 2 casos. El cambio del último commit (sidebar + transferencia de foco) es precisamente la capa sin red de seguridad automatizada: si el `SideEffect` de una pantalla dejara de registrar su `FocusRequester`, el CI seguiría verde y **`OK` en la sidebar no movería el foco**.

**Recomendación de bajo coste, por orden:** (1) `RoutesTest` con ids raros (`:` `/` `~` no ASCII, episodios anime), (2) test JVM de `StreamHub.stremioId`, (3) si se quiere cubrir Compose, `createComposeRule` para `TvScaffold` verificando que tras `onTabSelected` el foco cae en el contenido. Todo eso corre en JVM, sin emulador.

### 🟡 Baja

- **B1 · Textos sin `strings.xml`.** `res/values/strings.xml` solo tiene `app_name`; el resto está en Kotlin. Coherente con un producto monolingüe en español, pero conviene dejarlo escrito como decisión, no como olvido.
- **B2 · `usesCleartextTraffic="true"` a nivel de plataforma.** `network_security_config` no expresa rangos IP y el límite real lo impone `NetPolicy`, que está bien probado. El riesgo es futuro: cualquier código nuevo que use `URL`/`OkHttp` directo sin pasar por `AddonHttp` queda fuera de la política. Reforzar con un test que "grepée" que no aparecen `HttpURLConnection`/`openConnection` fuera de `AddonHttp` y `Doh` sería barato.
- **B3 · Versionado.** `versionName = "1.3.0"`, `versionCode = 4`; las releases publicadas son `v1.2.0` y la rodante `latest` (etiqueta fija que se reescribe). Funciona, pero conviene recordar que **`versionCode` debe subir** en cada publicación o Android rechazará la actualización.
- **B4 · Historial de git.** Todo el árbol entró en un único commit (`fa4b364`), cuyo mensaje describe solo el sidebar. El historial no permite rastrear por qué cada pieza está como está; para un proyecto con auditorías en `docs/` es una pérdida notable.
- **B5 · Marco legal.** La app no incluye fuentes: resuelve enlaces vía addons del usuario y extractores de embeds (Mixdrop, Streamtape, Dood, Voe…). El README declara MIT y «sin demo», lo cual ayuda, pero conviene un aviso explícito de que el usuario es responsable de las fuentes que configure.

---

## 5. Plan propuesto

| Fase | Trabajo | Riesgo |
|---|---|---|
| **1 · Asegurado (esta semana)** | H1 (decidir: cablear o borrar), H2 (token obligatorio en los dos puentes, plantilla `ffmpeg-bridge`), M4 (limpieza de código muerto) | Nulo para la app; solo toca `tools/` y dos firmas de Composable |
| **2 · Documentación** | M1 + M2: renombrar `TopTabBar` → `TvSidebar`, actualizar KDoc de `TvFocus.kt` y reescribir `docs/DPAD_FOCUS.md` con el mecanismo real (`LocalTabContentFocus` + `LocalSuppressContentAutoFocus` + `SidebarItem`) | Nulo |
| **3 · Red de seguridad** | M5: tests de `Routes` y `stremioId`; opcionalmente `TvScaffold` con Compose UI test | Bajo |
| **4 · En televisor** | M3 (overscan y rejilla de 5 columnas con la sidebar), foco D-pad real, reproducción, ciclo de vida | Requiere dispositivo |

---

## 6. Conclusión

Omni TV es un proyecto **por encima de la media** en lo que suele fallar en apps de este tipo: política de red explícita y probada, un único cliente HTTP, secretos cifrados con Keystore y backups excluidos, panel de vinculación con sesión, CSRF, rate-limit y tamaños acotados, y un reproductor con ciclo de vida y guardado de progreso correctos. La deuda no es de diseño sino de **arrastre**: un parámetro que se quedó a medias al cambiar la navegación, dos puentes de desarrollo con menos protección que el tercero, y una documentación de diseño que aún dibuja la barra superior.

Ninguno de los hallazgos impide publicar el APK que hoy compila el CI. Con la fase 1 y 2 resueltas, el proyecto queda coherente entre lo que hace, lo que dice y lo que protege.

---

## 7. Correcciones aplicadas (17 nov 2026)

### H1 · `onPreviewTab` eliminado — un solo punto de control

Se retiró el parámetro muerto en vez de cablearlo: `TvScaffold.onPreviewTab`,
`TopTabBar.onPreviewSelect` y el bloque `kidsPinGate` de `OmniTvApp` ya no
existen. La navegación queda con un único camino (`onTabSelected`) y el PIN
parental de Ajustes lo sigue exigiendo `SettingsScreen` con su propio diálogo,
que es el punto auditable. También se colapsó el `if (railHasFocus) trailing()
else trailing()` y se quitó el import de `SettingsStore` que quedó sin uso.

*Archivos:* `OmniTvApp.kt`, `ui/components/Components.kt`.

### H2 · Tokens obligatorios en los puentes Nuvio y Cloudstream

Ambos puentes replican ahora el criterio que ya tenía `ffmpeg-bridge`:

| Aspecto | Comportamiento |
|---|---|
| Host por defecto | `127.0.0.1` (antes `0.0.0.0`) |
| Fuera de loopback | **no arranca** sin `--token` o `OMNI_NUVIO_TOKEN` / `OMNI_CLOUDSTREAM_TOKEN` |
| Rutas protegidas | *todas*, incluido `GET /health` |
| Comparación | `timingSafeEqual` sobre SHA-256 (como `ffmpeg-bridge`) |
| Cabeceras aceptadas | `Authorization: Bearer …` y `X-Bridge-Token` |
| Sin token + loopback | sigue abierto, para no romper el flujo local |

El lado Kotlin envía la credencial opcional como Bearer, reutilizando el patrón
de `MediaBridge`: clave `NUVIO_BRIDGE` / `CLOUDSTREAM_BRIDGE` en
**Ajustes → Añadir APIs**. `AddonHttp` no la reenvía a otro host tras una
redirección, así que el token no puede filtrarse a un CDN.

**Efecto secundario documentado:** quien hoy use `--host 0.0.0.0` deberá añadir
`--token`. Es intencional; el mensaje de arranque lo explica y los docs
(`docs/NUVIO_INVESTIGACION.md`, `docs/CLOUDSTREAM_BRIDGE.md`) están actualizados.

### Tests nuevos (9 casos, integrados en CI)

- `tools/nuvio-bridge/auth.test.mjs` (5): guard sin token, Bearer vs `X-Bridge-Token`
  vs credencial inválida, **el puente se niega a arrancar en `0.0.0.0` sin token**,
  compatibilidad en loopback, y 401/200 contra un servidor real.
- `tools/cloudstream-bridge/auth.test.mjs` (4): los mismos casos sobre `/health`
  y `/resolve`.

Los tests encontraron además un fallo real durante el desarrollo: si la cabecera
`authorization` llegaba duplicada (array), `startsWith` lanzaba `TypeError` y la
respuesta era **502 en vez de 401**. Corregido con una comprobación de tipo en
ambos puentes.

### Estado

`node --test`: 16/16 en los puentes · `check_build_readiness.py` ✅ ·
`check_android_tv.py` ✅. Las fases 2 (documentación de diseño) y 3 (tests de
`Routes`/`stremioId`) siguen pendientes, igual que la validación en televisor.
