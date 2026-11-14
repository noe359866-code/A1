# Investigación — Soporte Addons Stremio + Plugins Nuvio All-in-One

**Fecha:** 2026-11-14  
**Manifest analizado:** `https://raw.githubusercontent.com/D3adlyRocket/All-in-One-Nuvio/refs/heads/main/manifest.json` (4 chunks, ~70 providers)  
**Objetivo:** que Omni TV soporte addons Stremio clásicos **y** los plugins de Nuvio (All-in-One).

---

## 1. Fetch — qué hay en el manifest Nuvio

No es un addon Stremio estándar. Estructura:

```json
{
  "name": "All-in-One-Nuvio",
  "version": "1.0.0",
  "scrapers": [
    {
      "id": "hianime",
      "name": "HiAnime",
      "description": "multi-server anime streaming…",
      "version": "1.0.0",
      "supportedTypes": ["movie","tv"],
      "filename": "providers/hianime.js",
      "enabled": true,
      "formats": ["m3u8"],
      "logo": "https://…/hianime.png",
      "contentLanguage": ["ja","en"],
      "hasSettings": true,
      "limited": false
    }, …
  ]
}
```

- **~68 scrapers** en 4 categorías: anime (AllAnime, HiAnime, AniZone, AnimePahe…), internacional (VixSrc, VidSrc, Videasy, Torrentio), hindi/desi (HDHub4u, VegaMovies, MoviesDrive, DesiFlix) y live/otros (KissKH, OnlyKDrama, Mapple…).
- Cada scraper: `providers/*.js` que corre en Nuvio (JS) → busca en web y devuelve links HTTP directos (mp4/mkv/m3u8/mpd). No es torrent por defecto, es HTTP/HLS.
- Campos: `id`, `name`, `description`, `author`, `supportedTypes`, `formats`, `logo`, `contentLanguage`, `enabled`, `hasSettings`, `limited`.

**Diferencia clave:**

| Tipo | Instalación | Formato | Qué aporta |
|------|-------------|---------|------------|
| **Stremio Addon** | `https://addon.com/manifest.json` → resources catalog/meta/stream | JSON Stremio (`id`, `resources: ["catalog","meta","stream"]`, `catalogs[]`, `types[]`, `idPrefixes[]`) | Catálogos + fichas + streams (HTTP/Torrent) |
| **Nuvio Plugin Repo** | `https://raw.githubusercontent.com/…/manifest.json` con `scrapers:[]` | Lista de scrapers JS | Streams HTTP vía scrapers; no da catálogo (Nuvio usa Cinemeta + catálogos propios) |

Nuvio soporta ambos: **Addons** (uno a uno, manifest URL) y **Plugins** (repositorio de scrapers). Un addon como `nuviostreams.hayd.uk` o `torrentio.strem.fun` da streams; un repo como `All-in-One-Nuvio` da 68 scrapers a la vez.

> Nota: El archivo analizado es el **repo**. El endpoint público del addon Nuvio Streams original (`https://nuviostreams.hayd.uk`) sigue declarando `{"id":"org.nuvio.streams","resources":["stream"],"types":["movie","series"],"idPrefixes":["tt","tmdb"]}` — retirado en abril 2026 pero replicable.

---

## 2. Stremio addon protocol — soporte actual vs necesario

**Soporte actual (StremioClient.kt + RemoteCatalog.kt):**

- `GET /catalog/{type}/{id}[/{extras}].json → {metas:[…]}` ✓
- `GET /meta/{type}/{id}.json → {meta:{…}}` ✓
- `parseVideo()` mapea a `Video` ✓
- `fetchManifest()` genérico ✓
- `loadCustomAddons()` carga 2 catálogos por addon, 20 items ✓
- DoH fallback + NetPolicy `https`/`http://lan` ✓

**Faltaba:**

- `resources` como objetos `{name:"stream", types:[…], idPrefixes:[…]}` además de strings — algunos addons declaran granular.
- `catalogs[].extra[]` con `name/isRequired/options/optionsLimit` — necesario para validar `search=`, `genre=`, `skip=` antes de pedir.
- `catalogs[].extraRequired` legacy + `behaviorHints` (`adult`, `p2p`, `configurable`).
- `idPrefixes`/`types` filtering en cliente — evitar pedir series a addon solo-movies.
- Pagination `skip=` real (no solo top).
- Streams con `behaviorHints` (`bingeGroup`, `filename`, `videoHash`, `proxyHeaders`) y debrid.

**Implementado en esta rama:**

- Parser robusto `StremioManifest` que tolera `resources` string/obj y `catalogs` con `extra`/`extraSupported`/`extraRequired`.
- `AddonCapability` (catalog/meta/stream/subtitles) derivado de resources.
- `StreamHub` unificado: resuelve de **todos** los addons que provean `stream`, no solo `streamAddons` separados — `streamAddons` sigue útil para proyectos custom puros.
- Normalización de extras: `search`, `genre`, `skip` codificados como `?search=…&skip=…` dentro del path JSON (variante Stremio v3).

---

## 3. Plugins Nuvio — arquitectura en Omni TV

Como los scrapers son JS (`providers/hianime.js` etc.), no se pueden ejecutar directo en Kotlin sin motor JS. Estrategia:

1. **Fetchear y mapear** el repo: `NuvioPluginManager.fetchRepo(url)` → lista `NuvioScraper` con metadatos + `filename` + `formats` + `logo`. Cache con revision.
2. **Persistir** repo URLs en `SettingsStore.nuvioPlugins` (lista de manifest URLs, igual que `customAddons`). Política NetPolicy idéntica.
3. **Resolver** vía puente: si los scrapers se necesitan ejecutar, se delega a una instancia compatible Nuvio/bridge local (`http://lan:8787`) que sí ejecuta JS — ya permitido por NetPolicy (`http://192.168.x:8787`). `NuvioScraperResolver` construye `POST /scrape/{scraperId}` con `{type, id, imdb/tmdb}`. Si no hay bridge, se informa y se usan addons Stremio normales.
4. **UI:** Ajustes muestra conteo de plugins/scrapers y permite activar/desactivar por idioma/formato; el panel web del QR tiene nuevo textarea “Plugins Nuvio”.

Esto deja la puerta abierta a embeber `Duktape`/`QuickJS` más tarde sin romper API.

---

## 4. Cambios en código (resumen)

- `StremioManifest.kt` (nuevo): modelos + parser tolerante.
- `NuvioPlugin.kt` (nuevo): `NuvioScraper`, `NuvioPluginManifest`, `NuvioPluginManager` + `NuvioScraperResolver`.
- `StremioClient.kt`: `parseManifest()`, `supportsType()`, `catalogExtraSupported()`, normalización extras.
- `SettingsStore.kt`: `nuvioPlugins: List<String>` + `saveNuvioPlugins()`.
- `PairingPortal/Router/Server`: nuevo campo `nuvioPlugins` + splitUrls con NetPolicy, status JSON incluye `plugins`.
- `RemoteCatalog.kt`: usa `StremioManifest` para filtrar catálogos con `search` y soporta pagination.
- `StreamHub.kt`: descubre addons `stream` de `customAddons` + `streamAddons` + plugins Nuvio (bridge).
- `SettingsScreen.kt`: nueva card “Plugins Nuvio” con grid y estados.

---

## 5. Pruebas manuales

```kotlin
// Añadir vía QR panel:
// https://v3-cinemeta.strem.io/manifest.json  → catálogos
// https://torrentio.strem.fun/manifest.json    → streams (si p2p ok)
// https://raw.githubusercontent.com/D3adlyRocket/All-in-One-Nuvio/refs/heads/main/manifest.json → 68 scrapers
```

Al guardar, `RemoteCatalog.needsReload()` dispara `refresh()` → nuevas filas “HiAnime · top” etc. si el addon expone catálogo, o Streams disponibles en `StreamHub` si expone `stream`.

---

## 6. Próximos pasos (fuera de alcance inmediato)

- Motor JS embebido (QuickJS Android) para ejecutar `providers/*.js` offline.
- Cache de streams con TTL y prioridad por calidad/idioma.
- Descarga de `providers/*.js` individuales para inspección en Ajustes.

