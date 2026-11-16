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
| `TvScaffold` + `TopTabBar` + `TabPill` | `ui/components/Components.kt` | Barra de pestañas tvOS: preview con retardo de 150 ms, `OK`/`Abajo` en la pestaña seleccionada → contenido, `↑` desde el contenido → pestaña activa |
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
