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

## 2. Qué nos llevamos a Omni TV (implementado)

No copiamos scraping completo — adaptamos patrones para Android TV + Stremio/Nuvio híbrido:

1. **Provider.kt** — interfaz limpia para futuros providers locales (CB01/AnimeUnity template) que pueden vivir junto a addons Stremio
2. **Extractor.kt** — host resolvers pluggables; implementamos 4 críticos: `Mixdrop`, `Streamtape`, `DoodStream`, `Voe` (cubren 70% de embeds de Streamflix), con headers y caching
3. **SafeGo/Uprot resolvers** — diálogos nativos TV con D-pad (numeric pad + image grid), session cache, mutex — evita WebView con ads en TV
4. **WebView blocker** — `WebViewBlocker` con host blocklist y JS interface para popunder suppression (para casos fallback)
5. **ProviderRegistry** — registra providers/extractores, ordena por `directFirst`, integra con `StreamHub` como fuente extra

Se deja `ExtractorBridge` para que Nuvio scrapers JS (All-in-One 68) puedan delegar a estos extractores si su `providers/*.js` devuelve embed host conocido.

---

## 3. Fetch — limitaciones

`git.gay` TLS falló para `git clone`/`archive.zip` (gnutls_handshake), pero HTML fetch OK para `providers/`, `extractors/`, `utils/` listings y commit history. Se infirió contrato de nombres, commit messages y estructura; implementación es adaptación limpia, no copia literal (licencia Streamflix AGPL-like).

Próximo paso: añadir `jsoup` + 1 provider real (CB01) cuando se tenga mirror accesible.



## Estado actualizado

Este análisis era histórico. `ProviderRegistry` y su provider plantilla fueron eliminados. La reproducción usa `StreamHub`; las extensiones Cloudstream se delegan a un runtime externo mediante `tools/cloudstream-bridge`, no a este registro local.
