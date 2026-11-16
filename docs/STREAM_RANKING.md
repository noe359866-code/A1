# Ranking de fuentes de reproducción

**Actualizado:** 2026-11-16. Implementado en `app/src/main/java/com/omnitv/app/data/StreamRanker.kt`.

## Qué resuelve

La opción **Calidad preferida** de Ajustes (Auto/4K/1080p/720p/480p) se guardaba
pero **no tenía ningún efecto** en la reproducción. Ahora `StreamHub` ordena las
fuentes mejor-primero antes de entregarlas al reproductor, inspirándose en el
*Stream ranking* de VortX (VortXTV/VortX, `engine/StreamRanking.kt`) pero reducido
a señales fiables para Omni TV:

- Resolución detectada en `StreamSource.quality` (o en la etiqueta si falta):
  2160/4K/UHD → 40 · 1440/2K → 35 · 1080 → 30 · 720 → 20 · 480 → 10 · 360 → 5 ·
  desconocida → 15.
- Con una preferencia concreta (p. ej. «720p»), esas fuentes encabezan la lista
  (+1000 puntos de bonus); el resto conserva su orden relativo.
- La ordenación es **estable**: a igualdad de puntuación se mantiene el orden de
  aparición de los addons.
- Se limita después a 24 fuentes (como antes).
- Si varios addons entregan la **misma URL**, se conserva la variante con mejor
  calidad detectada (`StreamHub.dedupeByUrlBestQuality`): mantiene etiqueta y
  cabeceras de la primera aparición y registra la calidad máxima vista, para
  que el ranking no puntúe a ciegas ni se pierdan `Referer`/cabeceras necesarias.

## Qué deliberadamente NO se puntúa

HDR/DV, audio (Atmos, TrueHD…), «remux», seeders, tamaño ni tipo de fuente:
los addons de Omni TV no exponen esos datos de forma fiable. Puntuar a ciegas
sería inventar calidad (a diferencia del ranker completo de VortX, de unas 1.200 líneas,
calibrado para su propio modelo de datos).

## Detalles

- `StreamSource` ganó un campo `quality: String = ""` (compatible en origen con
  todos los constructores existentes).
- Fallback de texto en `plugins/Extractor.kt::parseQuality`: la cadena vacía
  devolvía `«2160p»` (BUG: `"".contains("")` emparejaba siempre); ahora devuelve
  `HD`.
- Pruebas en `app/src/test/java/com/omnitv/app/data/StreamRankerTest.kt` (sin
  ejecutar aquí: no hay JDK en el entorno; pendiente de CI).
