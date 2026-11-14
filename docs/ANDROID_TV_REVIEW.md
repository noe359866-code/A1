# Revisión integral · Android TV

Fecha: 2026-11-14. Alcance: configuración Gradle, manifiesto y recursos, 57 archivos Kotlin de la app, navegación, reproductor, datos/red, vinculación, plugins experimentales, herramientas de puente, workflows y tests. Los documentos de investigación de terceros se revisaron como contexto, no como garantía de que sus servicios sigan disponibles.

## Conclusión

**El proyecto está planteado para Android TV / Google TV, no para móvil.** Leanback es obligatorio, el táctil es opcional, hay banner y `LEANBACK_LAUNCHER`, y no existe un iniciador `LAUNCHER` de móvil. El panel web que se abre desde el QR es una herramienta auxiliar para configurar el televisor; no es una segunda app móvil.

La revisión encontró diferencias importantes entre esa intención y la implementación. Se corrigieron los problemas de interacción y ciclo de vida que se detallan abajo. **No se considera terminado ni certificado para distribución hasta probarlo en un televisor/emulador con mando.**

## Correcciones aplicadas

### Mando, texto y foco

- Teclado propio en un **panel lateral**, tanto en búsqueda como al editar el nombre de un perfil. Ya no tapa el campo que se está escribiendo. No depende del IME del fabricante.
- Navegación explícita entre teclas: los bordes mantienen el foco dentro del teclado; bajar desde la última letra llega a LISTO. Atrás/LISTO devuelve el foco al campo sin reabrir el teclado inmediatamente.
- Búsqueda con rebote real de 350 ms; el contador de resultados ya no se superpone a la primera fila. Dos columnas mientras el teclado está abierto y cinco al cerrarlo.
- Avatares del editor en una fila desplazable; formulario con scroll y acciones alcanzables en el lienzo TV de referencia.
- Listas de perfiles desplazables horizontalmente cuando hay más perfiles de los que caben.
- Botón Volver integrado en la cabecera de plataformas/estudios, sin tapar el logo. Secciones de inicio con el margen seguro lateral de 48 dp.
- Rutas de Navigation con argumentos escapados: los ids de episodios y addons con `:`, `/`, `~`, espacios o Unicode no se pierden al navegar.

### Reproductor y segundo plano

- Transporte y ajustes en **dos filas**; los ajustes usan una fila horizontal desplazable. Antes las ocho píldoras y los botones de transporte competían por el mismo ancho y podían quedar fuera de pantalla.
- Etiquetas de servidor limitadas y truncadas; títulos largos no desplazan el reloj fuera de la cabecera.
- Menús de pistas con foco inicial, indicador de foco y scroll limitado a 220 dp. Información y menús no se apilan; «A continuación» sustituye al transporte.
- Foco inicial en Reintentar ante error y en Cancelar al terminar un título, para no activar por sorpresa el siguiente contenido.
- Teclas dedicadas Reproducir/Pausar/Detener y avance/retroceso gestionados con el mando multimedia; avance limitado a la duración conocida.
- El reproductor y el tráiler se pausan en `ON_PAUSE`. El progreso se guarda también al pasar a segundo plano y se recupera al volver a abrir un título desde el perfil.
- `KEEP_SCREEN_ON` ya no se aplica a toda la actividad: solo el vídeo activo mantiene despierto el televisor. Navegar o pausar no bloquea indefinidamente el descanso de pantalla.
- La franja se etiqueta como reproducción/siguiente/relacionado: **no se presenta como EPG real**, porque no hay XMLTV ni horarios de emisión.

### Vinculación, red y compilación

- El servidor de vinculación se abre al estar visible la actividad y se cierra en `onStop`; se enlaza a la dirección local del TV, con estado de error explícito si no hay red o no puede abrir el puerto.
- Ajustes muestra la versión compilada, no `1.0.0` fijado a mano.
- Lint detectó `putIfAbsent` (API 24) en DoH pese a `minSdk 22`; se sustituyó por una inserción compatible. El uso de la API marcada de Media3 UI queda declarado con `OptIn`, no oculto mediante un baseline.
- Cronet configura el directorio necesario para su caché de disco. Se eliminó la copia de cookies de clearance entre dominios distintos.
- StreamHub acepta también la forma simple documentada con un array JSON en la raíz.
- CI comprueba el contrato TV, ejecuta tests, compila debug y ejecuta lint. El recuento de tests ahora falla si no se ejecuta ninguno. Release también pasa tests y lint antes de empaquetar.

### Segunda pasada · integración y corrección de errores

- **Extractores cableados**: `EmbedRefiner` conecta `ExtractorRegistry` con
  `PlaybackResolver`. `StreamSource` y `StreamLink` llevan `subtitles`, y
  `StreamMediaSource` registra esos subtítulos en el `MediaItem` (mismo host,
  para no filtrar cabeceras privadas). El matching de dominios ahora compara
  el host real, no sub cadenas de la URL.
- **Un único cliente HTTP**: `AddonHttp.getText`/`getTextWithMeta` sustituyen a
  los `HttpURLConnection` propios de cinejoy/sweetfox, Jsoup en extractores y
  al OkHttp heredado de Cronet. Redirecciones, 4 MB, DoH y `NetPolicy`
  se aplican en un solo sitio. `getJsonWithStatus` también reintenta por DoH.
- **Cronet/Coil corregidos**: `CronetClient` devuelve `ByteArray` (antes
  corrompía JPEG al pasar por `String`), limita hilos/cuerpo y valida
  redirecciones; `cronetSupport()` instala el fetcher en el `ImageLoader`
  singleton. Se eliminó `WebViewResolver` (JS sin timeout/cancelación).
- **Caché y errores**: plataformas/estudios con TTL de 15 min, un `Mutex` por
  id e invalidación por `SettingsStore.revision`. Home y Buscar distinguen
  «sin resultados» de «no se pudo consultar el catálogo».
- **Vinculación**: `/save` rechaza `Origin` ajeno y `Sec-Fetch-Site:
  cross-site`; nuevas cabeceras `Cross-Origin-*-Policy`. El código sigue en la
  URL del QR (pendiente de mover a POST/cookie si se expone a redes de invitados).
- **Detalle**: episodios por tandas de 20 con «Mostrar más», en lugar de
  componer toda la temporada en un `Column`.
- **Release**: `app/build.gradle.kts` admite `OMNITV_KEYSTORE_*`; sin secretos
  mantiene debug para sideload. `release.yml` decodifica el keystore y anuncia
  el modo de firma. Ya no es obligatorio publicar con clave de depuración.
- **Limpieza**: fuera `Provider.kt`, `SafeGoResolver.kt`, `WebViewResolver.kt`,
  dependencias `jsoup`/`okhttp` y el `OkHttpClient` muerto de `cronetSupport`.
  Documentación histórica corregida. Tests nuevos para refiner, registro,
  cabeceras/subtítulos, `getText` y origen cruzado.
- **Avisos del compilador**: condición siempre verdadera en `EditProfileScreen`
  y `LocalLifecycleOwner` obsoleto migrados a `androidx.lifecycle.compose`.
  CI ya compila también `assembleRelease` en cada push.

## Hallazgos que siguen abiertos

| Prioridad | Zona | Hallazgo / trabajo necesario |
|---|---|---|
| Alta | Validación en dispositivo | No hay TV ni emulador conectado en este entorno. Falta comprobar foco, overscan, teclado, Home/Atrás, HLS/DASH, audio/subtítulos externos y consumo de memoria en API 22 y en una TV actual. Sigue siendo el bloqueo principal. |
| Media | Distribución | Ya se admite keystore de producción (`OMNITV_KEYSTORE_*`); **hay que configurar los secretos y verificar el APK firmado** antes de distribución pública. Sin ellos el release sigue siendo debug/sideload. |
| Resuelta | Red / addons | Un único `AddonHttp` para addons, puentes, scraping y extractores. Cronet valida redirecciones y cuerpo, y ya no hay clientes paralelos sin política. |
| Resuelta | Plugins / extractores | `ProviderRegistry`, `Provider`/`Server`, resolvers de captcha y `WebViewResolver` eliminados. Los 6 extractores sí están cableados vía `EmbedRefiner`; sin motor JS embebido. |
| Resuelta | Catálogo | Sin títulos demo; cachés de plataforma/estudio con TTL, bloqueo por id e invalidación por revisión. |
| Resuelta | Errores remotos | `CatalogPage.status`, `RemoteCatalog.lastError` y `searchError` distinguen fallo de red/429 de una búsqueda sin resultados. |
| Resuelta | Streams / Media3 | Headers por fuente, subtítulos externos (VTT/SRT/TTML) y HLS/DASH llegan a Media3. Sigue **sin soporte torrent** (infoHash/magnet se descartan por diseño). |
| Media | Infantil | El filtro local TP/7+ no es un control parental: cualquier usuario puede cambiar de perfil y no hay PIN. Las clasificaciones remotas no se tratan como una política parental completa. |
| Parcial | Seguridad local | Añadido rechazo de orígenes cruzados en `/save` y cabeceras `Cross-Origin-*`. Quedan: código en la URL del QR, HTTP en LAN y claves en claro solo en API 22. Revisar exposición en redes de invitados/VPN. |
| Resuelta | WebView / Coil | `WebViewResolver` eliminado; `CronetFetcher` instalado en el `ImageLoader` singleton y corregido para no corromper imágenes binarias. |
| Resuelta | UI / memoria | Detalle compone episodios por tandas de 20. El héroe y los textos con escala grande aún necesitan prueba de overscan/accesibilidad (incluida en «Validación en dispositivo»). |
| Baja | Mantenimiento | Código experimental, docs históricos y avisos del compilador corregidos. Persiste el aviso de Gradle 8.9 (y versiones Kotlin/Media3/Compose de 2024) y 9 ramas `arena/*` huérfanas en GitHub. |

## Validación

- `python3 tools/check_android_tv.py`: **correcto** localmente.
- `python3 -m py_compile tools/check_android_tv.py tools/omni-stream-bridge/bridge.py`: correcto.
- `git diff --check`: correcto.
- CI de la primera pasada (`03719b4`): **[ejecución 43554360259](https://github.com/noe359866-code/A1/actions/runs/43554360259), correcta**. Contrato TV, **98 tests / 0 fallos**, `assembleDebug` y `lintDebug` pasan. Los errores de lint (API 24 y opt-in de Media3) se corrigieron; no se creó un baseline.
- Segunda pasada: `python3 tools/check_android_tv.py` y `node --test` de los tres puentes Node correctos en local (7/7). CI final **[ejecución 43636914940](https://github.com/noe359866-code/A1/actions/runs/43636914940), correcta**: **175 tests / 0 fallos**, `assembleDebug`, `assembleRelease` (R8 con firma debug de reserva) y `lintDebug`; sin avisos del compilador. Se añadieron tests JVM para `EmbedRefiner`, `ExtractorRegistry`, `StreamResponse`, `AddonHttp.getText` y origen cruzado.
- Release `v1.2.0` ya publicado con `OmniTV-v1.2.0.apk`; esta pasada añade la firma de producción opcional sin cambiar el `versionCode`/`versionName` del árbol.
- Puente Python: prueba local de `/health`, `/stream/movie/tt1375666.json` y `/stream/series/tt0944947:1:2` contra un servidor temporal en loopback; las tres respuestas fueron correctas. Sigue siendo un puente de ejemplo, no un resolver de producción.
- CI conserva avisos de mantenimiento (por ejemplo, Gradle 8.9 antiguo); no son errores de compilación.
- El entorno local no tiene Java ni Android SDK, por eso la compilación y los tests Android se ejecutan en GitHub Actions, no se simulan localmente.
- No se ha probado reproducción con servicios externos ni se garantiza disponibilidad de cinejoy/sweetfox/Nuvio/addons de terceros.

### Pasada obligatoria en TV antes de distribuir

1. AVD Android TV 1080p (API 34/35) y, si se mantiene `minSdk 22`, un dispositivo/emulador API 22.
2. Instalación limpia: banner, selector de perfiles, creación/edición con mando y búsqueda con Ñ/espacios/borrado.
3. D-pad arriba/abajo/izquierda/derecha/OK/Atrás en todas las pantallas; ningún control fuera del área segura.
4. Home durante vídeo y tráiler: silencio/pausa; volver: posición guardada; pantalla de TV puede descansar normalmente al navegar.
5. MP4/HLS reales, cambio de servidor, pista de audio/subtítulos, error y reintento, siguiente episodio y temporizador.
6. QR desde otro equipo de la LAN, código incorrecto/bloqueo, cierre del puerto al salir de la app y ausencia de claves en backups.
7. Repetir con red lenta/sin red, escala de fuente grande y overscan activado.
