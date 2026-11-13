# Investigación · Reproductor estilo Xuper TV

Fuente: análisis de las páginas públicas de Xuper TV (reproductor IPTV para
Android TV / Fire TV, evolución de Magis TV) en noviembre de 2026.

## Qué es Xuper TV

Reproductor IPTV de alto rendimiento para Android TV, Fire TV, TV Box y
móvil. No incluye contenido propio: carga listas M3U / Xtream Codes del
usuario. Se distingue por su rendimiento, su interfaz limpia y un reproductor
integrado «inteligente».

## Funciones del reproductor (las que replicamos)

| Función | Descripción en Xuper TV | Implementación en Omni TV |
| --- | --- | --- |
| **EPG en tiempo real** | Muestra «qué se emite ahora y qué viene después» sin pausar la corriente | Franja *AHORA / A CONTINUACIÓN* en la barra superior del reproductor |
| **Multi-servidor** | Cada canal/título tiene varios enlaces; conmuta a otra ruta si la principal falla, con recuperación automática | `StreamSource` por vídeo (Servidor 1/2), selector de servidor y **cambio automático ante errores** conservando la posición |
| **Streaming adaptativo HD/4K** | Motor de bitrate adaptativo; calidad según el ancho de banda | Media3 con HLS/ABR + **selector de calidad** (Automática / 1080p / 720p…) con `DefaultTrackSelector` |
| **Relación de aspecto y zoom** | Ajusta zoom y relación de pantalla | Modos *Ajustar · Llenar · Zoom · 16:9 · 4:3 · 21:9* (`AspectRatioFrameLayout`) |
| **Subtítulos y audio** | Varias pistas de audio, subtítulos activables y tamaño ajustable | Selectores de **pista de audio** y **subtítulos** (activar/desactivar y elegir idioma) vía TrackSelection |
| **Retroceder / avanzar** | Rewind y fast forward con el mando | Botones **⏪ 10 s / 10 s ⏩** y atajos ◀▶ del D-pad |
| **Control optimizado para mando** | Botones grandes, navegación 100 % para mandos de TV | Controles grandes de vidrio, foco con escala, menús emergentes navegables con D-pad |
| **4K/HDR** | Streaming en ultra alta definición con HDR10 | Badges de calidad/formato en cabecera y panel de info |
| **Catch-up TV / timeline** | Revisar programas pasados con línea de tiempo | Barra de progreso con búfer y tiempos (la lógica EPG queda plasmada en la franja de programación) |

## Otras características del ecosistema Xuper TV (fuera del reproductor)

- Listas M3U / M3U8 / JSON y Xtream Codes, favoritos y carpetas
- EPG completo, multi-pantalla, Chromecast, reproductores externos (VLC/MX/MPV)
- Controles parentales con PIN, modo oscuro, arranque < 2 s
- Versiones separadas Móvil y TV (interfaz «a 10 pies»)

## Fuentes

- https://iptvhubzone.com/app/xuper-tv — ficha del producto (EPG en tiempo
  real, catch-up TV, multi-pantalla, 4K/HDR, control para mando, M3U/Xtream)
- https://xupertvs.net/ — características (modo especial para TV, adaptativo,
  reproductor avanzado, bajo buffering)
- https://xuper-tv-apk-1.modcombo.com/ — detalle de funciones (streaming
  adaptativo, EPG, controles parentales, reproductores externos)
- https://thexuper.com/ — comparativa y funciones (M3U, EPG, Chromecast,
  subtítulos, VOD, multi-servidor con varios enlaces por canal)
- https://xuper.one/ — grabación, multi-enlace, ajustes del reproductor
  (zoom, relación de aspecto, servidores alternativos, subtítulos)
- https://tvxuper.com/ — motor de bitrate adaptativo y conmutación multi-servidor
