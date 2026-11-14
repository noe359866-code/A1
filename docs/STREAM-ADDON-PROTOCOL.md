# Protocolo de entrega de streams de Omni TV

**«Omni Streams»** es el addon interno de Omni TV. Su única función es
**entregar enlaces de reproducción** (`streams`) de *otros proyectos* —
apps y servicios de terceros como StreamFlix Reborn, Torrentio, Comet,
MediaFusion o cualquier fork que exponga su API de enlaces.

El addon nunca extrae contenido por sí mismo: **solo retransmite** lo que
los proyectos externos entregan, con la misma filosofía que los addons de
streams de Stremio.

## Cómo entregar streams a Omni TV

Cualquier proyecto puede entregar streams añadiendo su URL desde
**Ajustes → Vincular → 🎬 Añadir addons de streams** (o desde el panel web
del QR). Omni TV le pedirá los enlaces de cada título con:

```
GET {base}/stream/{tipo}/{id}.json     ← forma estándar (Stremio)
GET {base}/stream/{tipo}/{id}          ← forma simple (alternativa)
```

Parámetros:

| Parámetro | Valores | Ejemplo |
|---|---|---|
| `{tipo}` | `movie` \| `series` | `series` |
| `{id}` | id del contenido (IMDb `tt…`, TMDB `tmdb:…`) y, para episodios, `id:temporada:episodio` | `tt7366338:1:2` |

### Forma 1 — Protocolo Stremio (recomendada)

```json
{
  "streams": [
    {
      "name": "MiProyecto · 1080p",
      "title": "Título del capítulo\n[ES] Servidor principal",
      "url": "https://cdn.miproyecto.example/playlist.m3u8"
    },
    {
      "name": "MiProyecto · 720p",
      "url": "https://cdn.miproyecto.example/video.mp4",
      "quality": "720p"
    }
  ]
}
```

Campos reconocidos:

- `url` (obligatorio) — enlace directo reproducible (`.mp4` o `.m3u8`). Los
  campos solo-torrent (`infoHash` sin `url`) se ignoran.
  - `https://` se acepta siempre.
  - `http://` **solo vale si el host está en tu red local**: una IP privada
    (`10.x`, `172.16–31.x`, `192.168.x`, `169.254.x`, `127.x`), `localhost`
    o un nombre `*.local`. Es el caso de un puente casero en
    `http://192.168.1.20:8787`.
  - Un enlace `http://` hacia una IP pública o un dominio de internet se
    descarta al entrar (la app no lo muestra ni lo reproduce). Si tu CDN no
    tiene TLS, sirve los enlaces por LAN o pon un proxy `https://` delante.
  - Se rechazan además las URLs con usuario y contraseña (`user:pass@host`).
- `name` / `label` — nombre del servidor (se muestra en el menú
  multi-servidor del reproductor).
- `title` — descripción adicional (calidad, idioma…).
- `quality` — `2160p / 1080p / 720p / 480p` (si no viene, se deduce).
- `ytId` / `infoHash` / `fileIdx` — se ignoran (sin motor de torrents ni
  YouTube embebido).

### Forma 2 — Simple

```json
{
  "streams": [
    { "label": "Servidor 1", "url": "https://cdn.miproyecto.example/v.mp4", "quality": "1080p" }
  ]
}
```

También se acepta un array directo `[{ "label", "url" }]` sin envoltorio.

## Comportamiento del addon interno

1. Al abrir un título remoto, **Omni Streams consulta en paralelo** todos
   los proyectos configurados (10 s máx. por proyecto).
2. Normaliza y deduplica los enlaces y los pasa al **reproductor
   multi-servidor** (menú de servidores, fallback automático si un enlace
   cae, calidad/audio/subtítulos cuando Media3 los detecta).
3. Si ningún proyecto entrega enlaces, el reproductor muestra un mensaje profesional
   **"Sin fuentes disponibles"** y sugiere configurar un addon en Ajustes — la app
   nunca reproduce contenido falso ni inventa streams.

## Ejemplo con StreamFlix Reborn y afines

Los proyectos tipo StreamFlix llevan su lógica en `providers/` +
`extractors/`. Para entregar sus streams a Omni TV basta con que su fork
exponga un endpoint mínimo:

```
GET {base}/stream/movie/tt1375666.json
GET {base}/stream/series/tt0944947:1:1.json
```

devolviendo la lista de enlaces que sus `extractors` ya resuelven, en
cualquiera de las dos formas anteriores. Desde el móvil (panel del QR) se
añade la URL de esa API y Omni TV empezará a reproducir esos streams con
todo el menú multi-servidor del reproductor.

**Puente de referencia listo para copiar** (Python y Kotlin, con el punto
de enganche para tus resolvers y guía de cableado CloudStream-style):
[`tools/omni-stream-bridge/`](../tools/omni-stream-bridge/README.md).
