# Saltar intro, resumen y créditos — para todos los usuarios

**Estado:** implementado en esta rama. Activado por defecto, **sin clave API y sin servidor propio**.

## Por qué funciona para todo el mundo

Las tres bases de datos comunitarias permiten **lectura anónima**:

| Fuente | Endpoint de lectura | Identidad | Clave para leer |
|---|---|---|---|
| SkipDB | `GET https://api.skipdb.tv/api/segments` | IMDb + temporada/episodio + `duration` (segundos) | No |
| TheIntroDB v4 | `GET https://api.theintrodb.org/v4/media` | TMDB (preferido) o IMDb + `duration_ms` | No (opcional) |
| IntroDB | `GET https://api.introdb.app/segments` | IMDb + temporada/episodio o `is_movie` | No |

Consecuencias de diseño:

- **No se incluye ninguna clave en el APK.** Una clave compartida sería un secreto público, iría contra las condiciones de TheIntroDB y quemaría el cupo de un solo usuario para todos.
- Cada televisor consulta **desde su propia IP**, así que los límites anónimos son por hogar, no un cupo global que se agota entre todos los usuarios.
- No hay backend que mantener ni coste por usuario: la app habla directo con las APIs.
- Quien tenga cuenta puede añadir `THEINTRODB=su_clave` en el panel QR; solo se envía a TheIntroDB, donde sube el límite de lecturas. `SKIPDB` e `INTRODB` se guardan cifradas para un futuro envío de tiempos (hoy no se usan: sus lecturas ya son abiertas).

## Comportamiento en el reproductor

- Aparece un botón **«Saltar intro / resumen / créditos / avance»** con foco del mando mientras el tramo está activo; visible aunque los controles estén ocultos.
- El **salto automático** es opcional y por tipo (Ajustes → Saltar intro y créditos). Por defecto está apagado: el usuario decide.
- Un tramo marcado `out-of-range` por SkipDB (la duración del stream no encaja) se muestra con `(?)` y **nunca** se salta automáticamente.
- Créditos sin fin declarado saltan al final del vídeo; los tramos se recortan a la duración real.
- Se consulta una vez por episodio, esperando a conocer la duración para que SkipDB/TheIntroDB elijan la versión correcta del stream.

## Datos y validación

- TheIntroDB: arrays por tipo; `start_ms: null` = principio, `end_ms: null/0` = hasta el final, `0/0` = «confirmado que no hay ese tramo».
- SkipDB: `segments.{intro,recap,outro,preview}` con `match`, `confidence` y `intro_length_estimate_ms` (mediana de la temporada).
- IntroDB: objetos por tipo (ms o segundos) y banderas `no_intro/no_recap/no_outro`; `post_credits` se trata como escena final, no como avance.
- Fusión: por tipo gana **SkipDB → TheIntroDB → IntroDB**, descartando datos dudosos si hay uno fiable; se conservan varios tramos del mismo tipo si no se solapan.
- Se descartan tramos de menos de 3 s, con fin anterior al inicio, fuera del vídeo o por encima del máximo del tipo.
- Caché: 6 h con datos, 10 min sin datos, enfriamiento de 60 s por fuente ante HTTP 429.

## Límites honestos

- SkipDB e IntroDB **solo aceptan IMDb**: un título que llega únicamente con id `tmdb:…` se consulta solo en TheIntroDB. No se inventa la conversión.
- Los tiempos son comunitarios: pueden no encajar con un corte distinto (logo extra, versión extendida). Por eso se envía la duración y se marca la incertidumbre.
- Sin temporada/episodio no se consulta TV; sin id IMDb/TMDB no se consulta nada.
- No hay envío de tiempos desde el televisor en esta versión.

## Atribución y licencias

- **SkipDB**: datos ODbL 1.0 con atribución (mostrada en Ajustes). Su término de reciprocidad afecta a quien use SkipDB para nutrir una base de datos privada de tramos; **el uso de solo lectura para mostrar los tiempos al usuario no lo activa**. Omni TV no almacena ni redistribuye una base derivada: consulta la API en el momento.
- **IntroDB**: integración en reproductores permitida, incluida uso comercial razonable; atribución apreciada (incluida). Prohibido el rastreo masivo: la app hace una consulta por reproducción, nunca descarga la base completa.
- **TheIntroDB**: API gratuita; la clave opcional es del usuario final, nunca una clave de aplicación compartida.

## Pruebas

`./gradlew :app:testDebugUnitTest` cubre los tres formatos JSON, extremos nulos, centinela `0/0`, segundos vs milisegundos, incertidumbre por duración, prioridad de fusión, recorte a la duración, construcción de URLs por fuente y el cliente HTTP (cabeceras opcionales, lectura sin clave y distinción de HTTP 429) contra un servidor real en loopback.
