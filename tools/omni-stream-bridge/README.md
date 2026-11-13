# 🌉 Omni Stream Bridge

Puente **mínimo y listo para copiar** para que cualquier proyecto tuyo
(tipo StreamFlix: `providers/` + `extractors/`) entregue sus streams a
**Omni TV** por el protocolo
[`docs/STREAM-ADDON-PROTOCOL.md`](../../docs/STREAM-ADDON-PROTOCOL.md).

```
tools/omni-stream-bridge/
├── bridge.py              ← servidor Python (stdlib, sin dependencias)
├── android/OmniStreamServer.kt  ← drop-in Kotlin para apps Android
└── README.md              ← esta guía
```

## Protocolo (resumen)

```
GET /stream/{tipo}/{id}.json     ← forma estándar (Stremio)
GET /stream/{tipo}/{id}          ← forma simple (alternativa)
```

| Parámetro | Valores | Ejemplo |
|---|---|---|
| `{tipo}` | `movie` \| `series` | `series` |
| `{id}` | `tt…`, `tmdb:…`, episodios `id:temporada:episodio` | `tt0944947:1:1` |

Respuesta (superforma — válida para las dos formas):

```json
{"streams":[
  {"name":"MiServidor","label":"MiServidor","title":"1080p · ES",
   "url":"https://cdn…/playlist.m3u8","quality":"1080p"}
]}
```

Solo `url` es obligatoria (`http/https`, `.mp4` o `.m3u8`). Máx. 24
enlaces; los torrents (`infoHash`) se ignoran.

## Opción A — Python (servidor en tu PC/VPS)

```bash
python3 bridge.py --port 8787
```

Cablea tu proyecto en la función **`resolve(kind, video_id)`** del propio
archivo (el bloque está marcado con `PUNTO DE ENGANCHE`):

```python
def resolve(kind, video_id):
    # kind: 'movie' | 'series'  ·  video_id: 'tt1375666', 'tt0944947:1:1'…
    enlaces = mis_extractores(kind, video_id)   # ← tu lógica aquí
    return [{"label": "Servidor 1", "url": enlaces[0], "quality": "1080p"}]
```

## Opción B — Kotlin (dentro de tu app Android)

Copia `android/OmniStreamServer.kt` en tu proyecto (cambia el `package`)
y pásale tu resolver en el constructor. Para un proyecto tipo
**CloudStream/StreamFlix** (providers con `loadLinks`):

```kotlin
val server = OmniStreamServer(port = 8787) { kind, id ->
    val links = mutableListOf<OmniStream>()
    val provider = misProviders.firstOrNull { it.canHandle(kind, id) }
    // loadLinks(data, isCasting, subtitleCallback, callback) → recoge
    // cada ExtractorLink que llegue al callback:
    provider?.loadLinks(
        data = id,                       // 'tt0944947:1:1'…
        isCasting = false,
        subtitleCallback = { /* ignorado por el protocolo */ }
    ) { extractorLink ->
        links.add(
            OmniStream(
                label = extractorLink.name,          // nombre del servidor
                url = extractorLink.url,             // http(s) directo
                quality = extractorLink.quality?.toString(),
                title = extractorLink.source
            )
        )
    }
    links
}
server.start()
```

Arráncalo desde tu `Application`/`MainActivity` (y páralo en `onDestroy`).
El teléfono debe estar encendido y en la misma red Wi-Fi que la TV.

## Conectar Omni TV

1. Averigua la IP del servidor (`http://<ip>:8787`).
2. En Omni TV: **Ajustes → Vincular → 🎬 Añadir addons de streams** →
   pega la URL (una por línea) → **Guardar**. (También desde el panel web
   del QR, en el móvil.)
3. Abre cualquier título remoto: «Omni Streams» le pedirá los enlaces a tu
   puente y aparecerán en el **menú multi-servidor** del reproductor como
   `MiServidor · 1080p`.

## Probar de punta a punta (curl)

```bash
# Forma estándar
curl http://127.0.0.1:8787/stream/movie/tt1375666.json
curl "http://127.0.0.1:8787/stream/series/tt0944947:1:1.json"

# Forma simple
curl "http://127.0.0.1:8787/stream/series/tt0944947:1:1"

# Estado
curl http://127.0.0.1:8787/health
```

Respuesta esperada:

```json
{"streams":[{"name":"Demo · Big Buck Bunny","label":"Demo · Big Buck Bunny",
"title":"series:tt0944947:1:1 · sustituye…","url":"https://…/BigBuckBunny.mp4",
"quality":"720p"}, …]}
```

## Notas

- **Red**: el puente escucha en `0.0.0.0` para que la TV lo alcance por
  LAN. Úsalo solo en redes de confianza (no hay autenticación).
- **Tiempos**: Omni TV espera respuesta en ~6–8 s por addon; si tu
  scraping tarda más, cachea enlaces por id (`cacheMaxAge` es informativo).
- **Formatos**: `.mp4` y `.m3u8` (HLS). Los enlaces que no sean
  `http/https` se descartan automáticamente.
- **Múltiples fuentes**: puedes montar varios puentes (uno por proyecto) y
  añadir todas sus URLs en Omni TV — «Omni Streams» los consulta en
  paralelo y mezcla los resultados con la etiqueta de cada uno.
