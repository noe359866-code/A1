# Catálogo de anime: Jikan + Kitsu

Omni TV incorpora una pestaña **Anime** con un catálogo exclusivo de series,
películas, OVA y ONA de anime. No mezcla el catálogo general de Cinemeta/Nuvio:
sus fichas vienen de dos bases de datos públicas de metadatos.

## Fuentes

| Proveedor | Base | Endpoints | Formato |
|-----------|------|-----------|---------|
| **Jikan v4** (MyAnimeList no oficial) | `https://api.jikan.moe/v4` | `/top/anime`, `/seasons/now`, `/anime?q=…`, `/anime/{id}/full`, `/anime/{id}/episodes` | `{ data: […] }` |
| **Kitsu JSON:API** | `https://kitsu.app/api/edge` | `/trending/anime`, `/anime` (`filter[text]`, `sort=-averageRating`), `/anime/{id}`, `/anime/{id}/episodes` | `{ data: […], included: […] }` |

Documentación oficial: [Jikan v4](https://docs.api.jikan.moe/) y
[Kitsu JSON:API](https://hummingbird-me.github.io/api-docs/).
Jikan limita a **3 peticiones/segundo y 60/minuto**; `JikanClient` separa sus
llamadas al menos 350 ms y usa un read timeout de 5 s. Kitsu acepta como
máximo 20 elementos por página. Cada sección, búsqueda y ficha tiene además un
tiempo límite de coroutine para que un Jikan caído no bloquee a Kitsu.

Ambas son **APIs de metadatos, no addons de streams**: no devuelven enlaces de
vídeo. La app no inventa fuentes ni sustituye un título por otro.

## Arquitectura

- `AnimeModels.kt`: ids, equivalencias de clasificación por edad y contrato
  común (`AnimePage`, `AnimeEpisodePage`).
- `JikanClient.kt` / `KitsuClient.kt`: clientes HTTP y parsers puros y
  tolerantes. Solo usan `AddonHttp` (política `NetPolicy`, cuerpo máximo 4 MB,
  redirecciones validadas y DoH tras fallo DNS).
- `AnimeCatalog.kt`: filas curadas, búsqueda combinada, paginación, caché de
  15 minutos y ficha completa con episodios reales.
- `AnimeScreen.kt`: pestaña dedicada con filtro «Todos / Jikan / Kitsu», filas
  horizontales, rejilla completa y botón «Cargar más anime».
- `RemoteCatalog`: delega `fetchDetail()` de ids `mal:`/`kitsu:` en
  `AnimeCatalog` e incluye `AnimeCatalog.search()` en la búsqueda global.
- `Components.kt`: con seis pestañas, la barra superior compacta las no
  seleccionadas a icono y despliega la etiqueta de la pestaña activa/enfocada.

Todas las fichas se registran en `CatalogRepository`, de modo que la ficha,
«Mi lista», «Continuar viendo», el buscador y el reproductor usan el mismo id.

## Identificadores

| Origen | Id de navegación | Id de stream preferido |
|--------|------------------|------------------------|
| Jikan | `mal:{mal_id}` | `tt…` si `/full` trae un enlace IMDb externo; si no, `mal:{id}` |
| Kitsu | `kitsu:{id}` | `kitsu:{id}` |

`Video.type` es siempre `ANIME`; `remoteType` es `movie` para películas de anime
y `series` para el resto, para que los addons y puentes existentes reciban el
tipo Stremio correcto. Los episodios usan `streamId` con la forma
`{prefijo}:{temporada}:{episodio}`.

`StreamHub` consulta primero `/stream/series/…` (o `/stream/movie/…`) y, si el
id es nativo de anime y no hay enlaces, reintenta con `/stream/anime/…`.
`CloudstreamBridge` y el puente Nuvio reciben temporada/episodio correctos
incluso con ids de tres segmentos como `kitsu:7442:1:2`. Para reproducir con
ids nativos, un addon debe declarar `idPrefixes` con `mal:`/`kitsu:` y el tipo
`series` o `anime`. Si solo hay addons IMDb/TMDB configure un puente
Cloudstream, que resuelve por título. Sin addons/bridges configurados la ficha
se abre con sus metadatos y el reproductor muestra «sin fuentes disponibles».

## Contenido seguro y perfiles

- Jikan: `sfw=true` en todas las consultas; además se descartan `Rx` y géneros
  explícitos (`Hentai`, `Erotica`).
- Kitsu: `filter[ageRating]=G,PG,R` en `/anime`; además se descartan en cliente
  `nsfw: true` y `R18` (el filtro `nsfw` del servidor no está permitido en
  `/anime` y `/trending/anime` puede ignorar el fieldset).
- Equivalencias: `G → TP`, `PG → 7+`, `PG-13 → 12+`, `R/R17 → 16+`,
  `R+/Rx/R18 → 18+`. Un dato ausente queda vacío, no se inventa.
- Los perfiles infantiles solo ven `TP`/`7+`, igual que en el resto de la app.

## Degradación y caché

Un proveedor caído no vacía el otro: la pestaña muestra los títulos disponibles
y `lastError` explica qué API falló (incluido HTTP 429 de Jikan). Las
búsquedas y fichas se cachean 15 minutos; `SettingsStore.revision` invalida la
caché cuando cambian ajustes/addons. Jikan puede no estar accesible desde
algunas redes o durante mantenimiento: Kitsu sigue siendo la fuente de respaldo
y hay un botón «Reintentar».

## Verificación

Tests JVM nuevos: parsers de Jikan/Kitsu, ids/clasificaciones, episodios,
codificación de URLs y compatibilidad de reproducción con Cloudstream/Nuvio.
CI ejecuta además el contrato Android TV, los puentes Node, compilación
debug/release y lint. **Pendiente**: comprobación en un Android TV real con
mando, foco D-pad y reproducción contra addons/bridges reales.
