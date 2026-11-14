# Streamflix Reborn — Análisis para plugins Omni TV

**Repo:** `https://git.gay/streamflix-reborn2/streamflix` — 2.811 commits, 21 MiB, 99.7% Kotlin, Leanback Android TV + Mobile, topics: android-tv, leanback, kotlin, streaming

**Fecha fetch:** 2026-11-14 (último commit `e1e18879` 2026-11-10: SafeGo/UPCaptcha nativos, Turbovio extractor)

---

## 1. Arquitectura que nos sirve

### Providers/ — ~38 proveedores scraping
Cada `*Provider.kt` (CB01, EuroStreaming, AnimeUnity, StreamingCommunity, FilmPalast, SerienStream…) implementa mismo contrato implícito:
```kotlin
interface Provider {
  val name: String; val lang: String; val supportedTypes: Set<String>
  suspend fun search(query: String): List<Video>
  suspend fun getCatalog(type: String, genre: String?, page: Int): List<Video>
  suspend fun getDetail(id: String): VideoDetail // con episodios/servers
  suspend fun getServers(videoId: String, episodeId: String?): List<Server> // url embed
}
```
- **Jsoup + Cronet/OkHttp** para HTML, con headers TLS browser-like (evita 403 en imágenes)
- **TMDb fallback**: si el proveedor no trae episodios, consulta TMDb para seasons

### Extractors/ — ~30 hosts extractores
Cada `*Extractor.kt` (Mixdrop, Streamtape, DoodLa, Voe, Filemoon, Rpmvid, Turbovio, BigWarp, Closeload, DeltaBit…) :
```kotlin
interface Extractor {
  fun canHandle(url: String): Boolean // domain match
  suspend fun extract(url: String): String? // → mp4/m3u8 directo + headers
}
```
- Regex + API calls (`/api/...`, `eval` unpacker, `m3u8` playlist)
- Algunos necesitan `Referer`/`User-Agent` o cookies de sesión
- **Prioridad:** `Direct > HLS > m3u8 > MP4` — Streamflix ordena servers y pone Mixdrop segundo si Maxstream tiene captcha (evita popup al inicio)

### Resolvers/ — SafeGo & Uprot (lo más valioso para TV)
- **SafeGoResolver**: numeric captcha (4-6 dígitos) → dialog nativo con keypad, cache de cookies/sesion, mutex anti-overlap, deletion, TV D-pad + mobile keypad
- **UprotResolver**: UPCaptcha image-grid (3x3), multi-round, rate-limit, blockedId filter, param `interactive` para decidir si mostrar diálogo o deferir a playback
- **AltchaResolver** (SerienStream): bypass automático background de Altcha challenge sin WebView; WebView solo fallback con ad-filter
- **WebView hardened**: bloquea popunders, notification prompts, overlays, ad hosts — misma lista en TV y Mobile; evita skip a next server cuando placeholder dispara error durante captcha

### Utils/
- `AADecoder`, `AesHelper`, `CryptoAES` (JS obfuscation)
- `CacheUtils` (LRU + TTL por provider)
- `ArtworkLoader/Repair` (TMDb images con TLS fix)

---

## 2. Qué hay realmente en Omni TV (estado 2026-11-14)

No copiamos scraping completo: el catálogo es Stremio/Nuvio y la reproducción
es `StreamHub`. De la investigación solo queda lo que está **cableado**:

1. **Extractores Kotlin** (`app/src/main/java/com/omnitv/app/plugins/`):
   `Mixdrop`, `Streamtape`, `DoodStream`, `Voe`, `Turbovio` y `Rpmvid`.
   `ExtractorRegistry` compara el host real (no `url.contains(...)`), cachea
   éxitos y valida el enlace con `NetPolicy`. Todos usan `AddonHttp`.
2. **`EmbedRefiner`** (`data/`): adapta esos extractores a `StreamSource`.
   Si un addon entrega la página de un embed, se resuelve a MP4/HLS directo y
   se propagan cabeceras y subtítulos externos hasta Media3
   (`StreamMediaSource`). Si el addon ya entrega un enlace directo, no toca nada.
3. **Cronet para imágenes**: `CronetClient` devuelve bytes (no texto UTF-8) y
   `cronetSupport()` instala el fetcher en el `ImageLoader` singleton de Coil
   desde `MainActivity`. Solo para hosts con bloqueo 403.
4. **Puentes externos**: Nuvio, Cloudstream y FFmpeg siguen en `tools/`; la TV
   no ejecuta JS de repositorios ni carga DEX.

**Eliminado por no estar conectado:** el contrato `Provider`/`Server`,
`ProviderRegistry`, `SafeGoResolver`/`UprotResolver`, `WebViewBlocker` y el
`WebViewResolver` con JavaScript. No se añadió `jsoup` como dependencia: el
cliente HTTP común hace el scraping de cinejoy/sweetfox y de los embeds.

---

## 3. Fetch — limitaciones

`git.gay` TLS falló para `git clone`/`archive.zip` (gnutls_handshake), pero HTML fetch OK para `providers/`, `extractors/`, `utils/` listings y commit history. Se infirió contrato de nombres, commit messages y estructura; implementación es adaptación limpia, no copia literal (licencia Streamflix AGPL-like).

Este documento se conserva como **contexto histórico**, no como lista de
funcionalidades pendientes. Para el contrato vigente ver
`docs/STREAM-ADDON-PROTOCOL.md` y `README.md`.
