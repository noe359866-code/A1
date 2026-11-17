# Foco D-pad y mando a distancia: auditoría y convenios

**Fecha:** 2026-11-16. Estado: **auditoría estática**. Ningún cambio de comportamiento
D-pad se ha validado en un dispositivo Android TV real; el comportamiento final del
foco (overscan, reciclado de tarjetas, perfiles de mando distintos) requiere pruebas
en pantalla.

## Infraestructura existente (ya en el repo)

| Pieza | Archivo | Qué hace |
|---|---|---|
| `TvKeyCodes`, `DpadRepeatGate`, `PlayerScrubRates`, `ConfirmKeyUpGuard` | `data/TvDpadLogic.kt` | Lógica pura JVM-testeable: mapeo de mandos Android TV, límite de repetición (75 ms horizontal / 105 ms vertical), aceleración de seeks, protección contra `ACTION_UP` huérfano |
| `RequestTvFocus`, `dpadRepeatThrottle`, `consumeStrayConfirmKeyUp`, `ProvideHorizontalTvBringIntoView`, `bringIntoViewIfChildrenAreFocused`, `LocalActiveTabFocus`, `LocalTabContentFocus`, `LocalSuppressContentAutoFocus` | `ui/components/TvFocus.kt` | Primitivas de foco: petición con reintentos por fotograma, throttle de flechas mantenidas, anti-focus-fantasma, saltos de scroll con margen seguro |
| `TvScaffold` + `TopTabBar` (sidebar) + `SidebarItem` | `ui/components/Components.kt` | **Barra lateral flotante**: compacta de 76 dp, expande a 224 dp al recibir el foco. No empuja el contenido (se superpone con `zIndex`), así que expandirse no recompone las rejillas. `OK` sobre la pestaña activa → contenido; `◀` desde el borde del contenido → barra (`focusProperties { left = activeTabFocus }`) |
| `SeekBar` interactivo | `ui/components/Components.kt` | `◀/▶` sin bloqueo: pasos 10/20/30/60 s con repetición; `OK` confirma |
| Teclado en pantalla (`TvKeyboard`, `TvTextField`, `TvKeypad`) | `ui/components/` | Foco atrapado en modo panel; en buscador, salidas explícitas `↑` (chips/pestañas), `↓` (sugerencias), `→` (rejilla) vía callbacks de borde |

## Patrón de restauración de foco (todas las pantallas)

Al volver del detalle/reproductor, el destino se compone de nuevo y se restaura el
foco con **claves de restauración** (`rememberSaveable`): `video:<id>`,
`chip:<label>`, `sugg:<text>`, `ep:<season>:<number>`, `hero:play|info|list`,
`row:<rowKey>:<videoId>`, `onlyOn:…`, `studio:…`, etc. La pantalla reintenta
`restoreFocus` durante unos fotogramas; si el objetivo aún no existe, cae al primer
elemento con foco de la pantalla.

`↑` desde los elementos del borde superior (chips, hero, primer ajuste) lleva a la
pestaña activa (`focusProperties { up = activeTabFocus }`), no a un sitio
arbitrario.

## Hallazgo corregido (código, esta rama)

**`DetailScreen`: la restauración «volver del reproductor al episodio» se daba por
fallida demasiado pronto en series/anime con catálogo remoto.**

- Al montar, `full.episodes` está vacío mientras `RemoteCatalog.fetchDetail(video)`
  está en curso, así que ningún `EpisodeRow` existe y `restoreFocus` nunca se adjunta.
- Antes: tras 6 reintentos se enfocaba «Reproducir» y se marcaba
  `mountRestoreDone = true`, con lo que **la llegada de los episodios nunca
  disparaba un nuevo intento**. El usuario volvía a la cabecera en vez de al episodio.
- Además, `selectedSeason` arrancaba siempre en 1 y la ventana `visibleEpisodes` (20)
  no garantizaba que el episodio a restaurar estuviera renderizado.

Corrección (en `DetailScreen.kt`):

1. `selectedSeason` se inicializa desde la clave de restauración `ep:S:N`.
2. `visibleEpisodes` se amplía (mínimo 20) para cubrir el episodio restaurado en su
   temporada.
3. Si el restore falla con `full.episodes` aún vacío y `video.isRemote`, se enfoca
   «Reproducir» como foco provisional **sin** marcar `mountRestoreDone`, de modo que
   el `LaunchedEffect(video.id, full.episodes.size)` se reejecuta al llegar el
   detalle y reintenta el salto al episodio.

## Hallazgos abiertos (documentados, no corregidos a ciegas)

1. **Restore a elementos fuera de la ventana de un contenedor perezoso.**
   `LazyRow`/`LazyVerticalGrid` solo componen los elementos visibles; un
   `restoreFocus` sobre una tarjeta no realizada no encuentra nodo y el foco cae al
   fallback (hero / primer elemento). Afecta a: filas de Inicio (`SectionRow`,
   `Top10SectionRow`, `OnlyOnSection`, `StudioStripSection`), rejilla de
   Películas/Anime/Búsqueda, fila «Más como esto» en detalle, rejilla de
   catálogos de plataforma/estudio. `focusRestorer()` en las filas solo conserva el
   foco dentro del contenedor mientras vive; no atraviesa una navegación.
   **Arreglo honesto:** pasar la clave pendiente al contenedor para hacer
   `scrollToItem(index)` antes de pedir foco, o validar en dispositivo si el caso
   real molesta. No se implementó a ciegas (sin compilador ni dispositivo aquí).
2. **La rejilla de resultados del buscador se recompone con el tecleo** (búsqueda
   remota con debounce). Si la tarjeta enfocada desaparece del conjunto filtrado,
   Compose suelta el foco sin fallback explícito; el usuario sale con `↑`/`←` hacia
   el teclado, pero no hay garantía de destino inmediato. Requiere prueba en
   dispositivo.
3. **`dpadRepeatThrottle` envuelve pantallas completas** (Inicio, detalle,
   buscador, etc.) y consume repeticiones direccionales por encima de la cadencia
   permitida. Es deliberado; puede notarse "escalonado" en mandos que repiten muy
   rápido. Sin feedback de usuario en pantallas de mando reales, no se toca.

## Convenios para pantallas nuevas

- Elemento del borde superior de una pestaña → `focusProperties { up = activeTabFocus }`.
- Restaurar foco al volver → clave en `rememberSaveable` + `FocusRequester` adjunto
  condicionalmente (`pendingRestoreKey == key`) y fallback ordenado (contenido →
  elemento primario).
- Teclado/capa modal → encerrar el foco (direcciones en los bordes =
  `FocusRequester.Cancel`), botón `↑` solo para salir hacia arriba si procede, y
  `ConfirmKeyUpGuard` para el `ACTION_UP` que abre la capa.
- Reintentar la petición de foco por fotogramas (`RequestTvFocus` o bucle manual de
  6-8 `withFrameNanos`) — los contenedores perezosos pueden tardar en adjuntar.
- Nunca depender de que la tarjeta exacta esté compuesta; el fallback debe dejar la
  pantalla plenamente navegable.

## Verificación de esta tanda (honesta)

- Balance de llaves/paréntesis de `DetailScreen.kt`: OK.
- `python3 tools/check_android_tv.py`: **contrato Android TV OK** (explícitamente
  "Pendiente de dispositivo: foco D-pad, overscan, ciclo de vida y reproducción
  reales").
- **No compilado** (sin JDK/Gradle en el entorno). **No ejecutado en dispositivo.**
  Las tres correcciones son de inicialización y de concurrencia de efectos
  (no cambian APIs ni llamadas de Compose), pero siguen sin validación en pantalla.
- Tests JVM existentes (`TraktServiceTest`/`StreamRankerTest`/`SkipKitsuMappingTest`/
  `PairingRouterTest`) no cubren UI; no se han ejecutado aquí.

## Estado tras la revisión de diseño (2026-11-17)

- **Barra lateral flotante.** `TvScaffold` ya no usa `Row` con peso: el contenido
  ocupa el ancho completo y la barra se dibuja encima (`Box` + `align(CenterStart)`
  + `zIndex(2f)`). Motivo: al expandirse de 76 a 224 dp dentro de un `Row`, el póster
  de Películas pasaba de **141,6 dp a 112,0 dp (−21 %)** y la rejilla se recalculaba
  en plena navegación.
- **El contenido reserva `AppSpacing.navRail` (84 dp)** al inicio: 8 dp de aire +
  76 dp de barra compacta. Cuando la barra crece, se superpone al contenido en lugar
  de desplazarlo. Aplicado en Inicio (filas), Películas, Anime, Plataformas, Buscar,
  Ajustes y las secciones `SectionRow` / `Top10SectionRow` / `OnlyOnSection` /
  `StudioStripSection`.
- **`FocusScale` consolidado** en los tres factores de la guía de foco de Android TV
  (1.025 / 1.05 / 1.10). Los nombres anteriores (`row`, `chip`, `tab`, `card`, `key`,
  `control`, `hero`) se conservan como alias, así que las 32 llamadas no cambian.
- **Pendiente de dispositivo:** el salto de foco barra → contenido se apoya en
  `LocalTabContentFocus`; cada pantalla registra su primer `FocusRequester` con
  `SideEffect`. Si una pantalla nueva no lo hace, `OK` en la barra no movería el foco
  y el CI no lo detectaría (no hay tests de UI).

## Estado tras la revisión de Ajustes (2026-11-17)

- **`TvToggle(focusable = ...)`.** Nuevo parámetro (`true` por defecto). Con `false`
  se anula `canFocus` **y** el `clickable`, y el interruptor pasa a ser solo el
  indicador del valor de su fila. Se usa en las 9 filas de Ajustes, donde la fila
  entera ya es el objetivo del mando: antes había **dos paradas de foco por fila** y
  dos anillos blancos encendiéndose por separado. En `EditProfileScreen` (la fila no
  es pulsable) sigue siendo `true`, que es su única parada.
- **Atajo de búsqueda: tecla azul del mando.** `TvKeyCodes.KEYCODE_PROG_BLUE = 186` y
  `isSearchShortcut(code)`, en `TvDpadLogic` (lógica pura, testeable en JVM). Se
  escucha con `onPreviewKeyEvent` en la raíz de `SettingsScreen`, que es ancestro de
  cualquier nodo enfocado, y no intercepta OK ni flechas. No se abre con el PIN
  parental pendiente (`!gated`).
- **Panel de búsqueda y foco.** Usa `TvKeyboard(embedded = true)`, el único modo del
  teclado con ganchos de borde: la primera fila de teclas sube al **último resultado**
  (o al botón «Cerrar» si no hay resultados) y la última fila baja al teclado. El foco
  inicial entra por el teclado (`RequestTvFocus`), y al cerrar el panel vuelve a la
  categoría activa.
- **Categoría de Ajustes: 48 dp.** Es el mínimo de objetivo de mando de la guía de TV.
  Con 6 dp de relleno vertical caben **6 de las 8** categorías sin desplazar (antes 5).
  Queda por validar en pantalla que el icono de 36 dp y el texto de 19 sp no se
  recorten a esa altura.
- **Foco con el buscador abierto.** `searchOpen` entra en `anyModalOpen`, así que el
  mecanismo de `restoreAfterModal` no compite con la vuelta de foco propia del panel.

## Estado tras la revisión del reproductor (2026-11-17)

- **La barra de píldoras ya no desplaza.** Quedan 4 fijas (Mi lista, Audio,
  Subtítulos, Ajustes) más «Reanudar» cuando el título venía a medias: **781 dp en el
  peor caso sobre 832 disponibles**. Servidor y calidad se movieron a la cabecera como
  texto —el servidor solo ya ocupaba hasta 180 dp— y aspecto, velocidad, temporizador e
  info viven en el menú «Ajustes», que se abre con **un nivel de anidamiento**: Atrás
  vuelve al menú padre (`parentMenu`), no cierra todo.
- **`Row` en lugar de `LazyRow`** para las píldoras: sin desplazamiento, el recorrido de
  foco es predecible de izquierda a derecha y ningún control «aparece» al llegar al
  borde. Se conserva `focusRestorer` para devolver el foco a la última píldora usada.
- **`SeekBar` de 48 dp.** Era el único control del reproductor por debajo del mínimo de
  mando (36 dp) y el más usado. El dibujo no cambia, solo el área que recibe el foco.
- **OK sobre la barra de progreso pausa/reanuda** (comportamiento de YouTube y Netflix
  TV; aquí la barra es la superficie principal de transporte). La guía de Playback
  recomienda *no* gestionar OK dentro de la barra, pero esa recomendación asume que la
  barra no es el control principal. Se documenta la decisión y se mantiene `↔` para
  recorrerla y `↑`/`↓` para entrar y salir.
- **Panel de «A continuación» a la izquierda, menú a la derecha.** Compartían fila y
  posición vertical: abrir un menú durante la cuenta atrás los solapaba. Ahora abrir un
  menú cierra el panel, y uno va a cada lado.
- **Aviso flotante a 96 dp** (antes 28): caía sobre el título de la cabecera.
