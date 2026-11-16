# Paridad con referencias: SkyStream, VortX y Nuvio

**Actualizado:** 2026-11-16. Auditoría honesta de qué tienen esos proyectos que Omni TV no tiene,
qué se ha implementado ya, y qué queda fuera y por qué. **Omni TV es MIT y código original**:
SkyStream y Nuvio son GPL-3.0, VortX es GPL-3.0. Ningún archivo, asset ni fragmento de esos
proyectos se ha copiado; las funciones se reimplementan desde las APIs públicas y la documentación.

## Resumen ejecutivo

| Veredicto | Qué |
|---|---|
| **Ya implementado** | Sincronización **Trakt** opcional (código de dispositivo, inicio/pausa/fin) — nuevo en esta rama |
| **Ya estaba** | Skip intro/créditos con 4 fuentes + traducción Kitsu→MAL; Continuar viendo; Mi Lista; perfiles; QR; StreamHub; anime Jikan/Kitsu |
| **Decisión documentada** | Motor Nuvio/CloudStream **embebido** → puentes externos (no JS en el APK) |
| **Roadmap** | SIMKL/AniList/MAL, Discover con filtros, historial, Ratings multi-proveedor |
| **Fuera de alcance / no se hará** | Downloads, torrents/P2P en el APK, sync con la nube de Nuvio, copiar GPL |

## Matriz de características

| Característica | SkyStream (Flutter) | VortX (Swift/Rust) | Nuvio (GPL-3.0) | Omni TV | Estado |
|---|---|---|---|---|---|
| Addons Stremio (catálogo/meta/stream) | ✓ | ✓ | ✓ | ✓ | **Paridad** |
| Repositorios Nuvio (`scrapers[]`) | motor JS embebido | vía bridges | motor nativo | puente JS externo (`tools/nuvio-bridge`) | **Decisión**: nunca JS en el TV; el puente lo ejecuta fuera |
| Extensiones CloudStream | motor `.cs3` embebido | — | parcial | puente externo (`tools/cloudstream-bridge`) | **Decisión**: mismo criterio de seguridad |
| Saltar intro/créditos | anime-skip + intro-db | nativo | ✓ intro | **4 fuentes** (SkipDB, TheIntroDB, IntroDB, AniSkip) + Kitsu→MAL automático | **Superada** (ver [SKIP_SEGMENTS.md](SKIP_SEGMENTS.md)) |
| Sincronizar progreso con **Trakt** | ✓ (tr-client propio compilado) | ✓ (servicio propio) | ✓ nativo | ✓ **esta rama** (claves del usuario, sin clave de la app) | **Implementado** (sin probar contra trakt.tv en vivo; ver límites) |
| SIMKL / AniList / MAL como rastreadores | ✓ (los cuatro) | SIMKL sí, Trakt sí | Trakt sí | ✗ | **Roadmap P1** (mismo patrón que Trakt) |
| Descubrir/Explorar con filtros (TMDB/AniList) | ✓ (TMDB + AniList) | Discover | filas TMDB | filas del catálogo + filtro en rejilla Películas | **Roadmap P3** (ya se acepta `TMDB=`; falta la pantalla) |
| Continuar viendo | ✓ | ✓ | ✓ | ✓ (fila en Inicio + progreso por perfil) | **Paridad** |
| «A continuación» (Up Next tras terminar) | ✓ | ✓ (permanece en Continue Watching) | ✓ | parcial: auto-next con cuenta atrás, sin fila «Up Next» | **Roadmap P2** |
| Mi Lista / colecciones | ✓ (library + bookmarks) | ✓ Collections | ✓ | Mi Lista por perfil | **Paridad** (collections ricas: fuera) |
| Historial de visionado explícito | ✓ | ✓ | ✓ | ✗ (solo progreso por id, sin pantalla de historial) | **Roadmap P2** |
| Ratings multi-proveedor (IMDb/RT/Metacritic…) | parcial | ✓ | vía addons | un rating por título | **Roadmap P4** (coste de APIs/scraping) |
| Perfiles (infantil, PIN) | ✓ | ✓ | ✓ | ✓ (infantil + PIN parental + Mi Lista aislada) | **Paridad** |
| Gestión remota desde el móvil | app móvil propia | no | app móvil | **panel QR** en la misma LAN (addons, plugins, claves, cuenta) | **Paridad funcional** distinta por diseño |
| Importar cuenta Stremio (biblioteca) | — | ✓ | vía NuvioSync | ✗ | **Roadmap P4** (necesita investigación de API) |
| Sync con la nube de Nuvio | — | — | ✓ (cuenta, delta) | ✗ | **No**: obliga a enviar progreso/config a un servicio de terceros sin consentimiento explícito; si un día se quiere, exige decisión del usuario y aviso claro |
| Debrid (Real-Debrid, TorBox…) | vía addons | vía addons + engine | vía addons Comet/AIOStreams | vía addons (Torrentio/AIOStreams/MediaFusion) | **Paridad** (sin resolver in-app) |
| Usenet / indexadores | — | ✓ (beta) | — | ✗ | **No** (infra y legalidad fuera de alcance) |
| Descargas offline | ✓ | ✓ (móvil) | ✓ | ✗ | **No sin sign-off** (alcance grande) |
| Torrents/P2P en el APK | ✓ (flutter_torrent_server) | ✓ (Full) | vía addons | ✗ nunca | **No** |
| Top Shelf / widgets | — | tvOS | — | ✗ (plataforma Apple) | **N/A** |
| Escaneo de medios locales (Jellyfin…) | ✓ | ✓ (AetherBridge parcial) | parcial | ✗ | **No sin sign-off** |

## Lo implementado: Trakt opcional (esta rama)

**Qué hace**
- El usuario crea una app gratuita en `trakt.tv/oauth/applications` y guarda
  `TRAKT=tu_client_id` y `TRAKT_SECRET=tu_client_secret` en el panel Vincular (QR),
  cifradas como el resto de claves (Keystore AES-GCM).
- En Ajustes → Integraciones → **Vincular Trakt** aparece el código de dispositivo
  (`trakt.tv/activate`); al confirmarlo, el televisor queda vinculado y los tokens
  se guardan cifrados en `omni_tv_settings` mediante Android Keystore (AES-GCM).
- **Requisito de seguridad:** Trakt solo se vincula si el Keystore logra cifrar los
  tokens. En Android 5.1/API 22 o si el Keystore falla, no se persisten tokens
  en texto plano y la interfaz informa del problema. Los tokens plaintext de
  versiones anteriores solo se migran si pueden cifrarse inmediatamente; en
  caso contrario se eliminan y habrá que volver a vincular.
- El reproductor envía `scrobble/start` al empezar, `scrobble/pause` al pausar por
  ciclo de vida y `scrobble/stop` al salir o al terminar (≥80 % = visto en Trakt).
- Cambiar o borrar `TRAKT`/`TRAKT_SECRET` invalida los tokens (pertenecen a esa app).
- Sin credenciales o sin vincular, **cero llamadas de red a Trakt**. Un fallo de
  scrobble nunca interrumpe la reproducción.

**Límites honestos**
- No se ha probado contra trakt.tv en vivo (el sandbox no llega a la red de Trakt) ni
  en un TV físico; los tests cubren solo el protocolo (parsers, construcción de cuerpos).
- Un 400 con `error` distinto de `authorization_pending`/`slow_down` (p. ej.
  `invalid_client` por claves malas) se trata como error del flujo, no como
  «esperando» infinito; «sin vincular» solo se fuerza ante 400/401 del refresh
  (un 403/5xx se considera transitorio y conserva la vinculación).
- El `scrobble/start` solo se dispara a partir de 10 s de reproducción real; un
  seek/scrub o un «siguiente» accidental no marca nada. `stop` envía la posición real.
- Películas y series con IMDb/TMDB sí; **anime con ids MAL/Kitsu no se scrobblea**
  (Trakt no acepta esos ids) — fail-closed, nunca se marca la ficha equivocada.
- Solo scrobble de reproducción. **No** importa historial, no sube listas, no sincroniza
  «continuar viendo» bidireccionalmente.
- Los tokens viven en el televisor; no se replican entre dispositivos (a propósito).

**Archivos:** `data/TraktProtocol.kt` (puro), `data/TraktService.kt` (red), `data/SettingsStore.kt`
(blob sellado `traktEnc`), `player/PlayerScreen.kt` (llamadas), `ui/screens/SettingsScreen.kt`
(tarjeta + diálogo), `data/PairingPortal.kt` (ayuda en el panel), `data/TraktServiceTest.kt`.

## Roadmap propuesto (pendiente de tu visto bueno)

| # | Qué | Esfuerzo | Notas |
|---|---|---|---|
| P1 | SIMKL (código de dispositivo) + AniList (GraphQL) + MAL (OAuth PKCE) | alto | mismos moldes que Trakt; cada uno necesita claves propias |
| P2 | Historial explícito + fila «Up Next» en Inicio | medio | datos ya existen en el perfil; falta pantalla y fila |
| P3 | «Descubrir» con filtros (género/año/idioma) vía TMDB | medio-alto | reutiliza `TMDB=` ya soportado; pantalla nueva |
| P4 | Importar biblioteca Stremio; Ratings multi-proveedor | alto | APIs/scraping con sus límites y legalidad |
| P5 | Sync con la nube de Nuvio | alto | **requiere tu decisión**: implica enviar datos a terceros |

Explícitamente **fuera**: copiar código GPL de los clones; claves embebidas de
Trakt/TMDB/etc. en la app; torrents/P2P; Usenet; downloads sin sign-off.

## Verificación de esta rama

Estática solamente (no hay JDK ni TV en el entorno): balance sintáctico, `node --test`,
`tools/check_android_tv.py`. Los endpoints de Trakt **no** se han llamado desde aquí.
La integración se valida en dispositivo/CI antes de darla por hecha.
