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

## Hallazgos que siguen abiertos

| Prioridad | Zona | Hallazgo / trabajo necesario |
|---|---|---|
| Alta | Validación en dispositivo | No hay TV ni emulador conectado en este entorno. Falta comprobar foco, overscan, teclado, Home/Atrás, HLS, audio/subtítulos y consumo de memoria en API 22 y en una TV actual. |
| Alta | Distribución | Release se firma con la clave debug. Sirve para sideload de confianza, **no como firma de producción ni para una distribución pública fiable**. No se ha publicado un APK ni un release. |
| Alta | Red / addons | Las redirecciones de `HttpURLConnection` y Cronet no se revalidan con `NetPolicy` en cada salto; varios clientes leen cuerpos sin límite. Falta un cliente común con límites, redirecciones seguras y cancelación de E/S. |
| Alta | Plugins / Nuvio | `ProviderRegistry`, extractores y resolvedores no están conectados al flujo principal de reproducción. `NuvioPluginManager.refreshAll()` sigue teniendo una lista vacía/TODO. No deben anunciarse como integración completa. |
| Alta | Catálogo | `CatalogRepository` todavía contiene títulos, listas y perfiles de demostración con progreso ficticio. `withDemoFallback` y `DemoStreamAddon` siguen en el código aunque el flujo principal no los usa. Esto contradice la promesa histórica de «cero demo»; no se han borrado datos de usuario ni cambiado el catálogo en esta revisión. |
| Media | Streams | Solo `SettingsStore.streamAddons` alimenta StreamHub; un manifest añadido como `customAddons` no se usa automáticamente aunque declare `stream`. `StremioManifest`/`NuvioScraperResolver` no están integrados en esa selección. |
| Media | Streams / Media3 | Los headers y subtítulos externos que devuelven los extractores no viajan en `StreamSource` hasta Media3. El formato se deduce de `.m3u8`; no hay DASH ni soporte torrent. |
| Media | Búsqueda / caché | Los fallos remotos se degradan a listas vacías, sin distinguir ausencia de resultados de falta de red. Las cachés de plataforma/estudio no caducan ni ofrecen reintento; una recarga concurrente puede publicar una revisión antigua. |
| Media | Infantil | El filtro local TP/7+ no es un control parental: cualquier usuario puede cambiar de perfil y no hay PIN. Las clasificaciones remotas no se tratan como una política parental completa. |
| Media | Seguridad local | El panel usa HTTP en LAN y el código viaja en la URL del QR. Tiene límite de intentos, pero falta endurecimiento CSRF/origen y una revisión de exposición en redes de invitados/VPN. Las claves solo se cifran en reposo a partir de API 23. |
| Media | WebView / Coil | `WebViewResolver` no está conectado al flujo principal y necesita revisión de hilo principal, timeout y cancelación. `CronetFetcher`/`cronetSupport` tampoco están instalados en un `ImageLoader` de la app; Coil no recibe automáticamente ese supuesto fallback 403. |
| Baja | UI / memoria | Detalle compone todos los episodios de una temporada en un `Column`; temporadas largas deberían usar una lista perezosa. El héroe y los textos con escala de fuente grande necesitan pruebas de overscan/accesibilidad. |
| Baja | Mantenimiento | Hay imports/código experimental sin uso, avisos de versiones y documentación histórica que afirma más funciones de las que realmente están cableadas. Gradle 8.9 genera aviso de versión antigua en CI. |

## Validación

- `python3 tools/check_android_tv.py`: **correcto** localmente.
- `python3 -m py_compile tools/check_android_tv.py tools/omni-stream-bridge/bridge.py`: correcto.
- `git diff --check`: correcto.
- CI final del código corregido (`03719b4`): **[ejecución 43554360259](https://github.com/noe359866-code/A1/actions/runs/43554360259), correcta**. Contrato TV, **98 tests / 0 fallos**, `assembleDebug` y `lintDebug` pasan. Los errores de lint detectados durante la revisión (API 24 y opt-in de Media3) se corrigieron; no se creó un baseline para ocultarlos.
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
