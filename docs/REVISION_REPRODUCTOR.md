# Revisión del reproductor — Omni TV

**Fecha:** 17 de noviembre de 2026 · **Rama:** `arena/30f67343-a1`
**Objeto:** `app/src/main/java/com/omnitv/app/player/` — `PlayerScreen.kt` (1 528 líneas),
`PlayerUi.kt` (351) y `StreamMediaSource.kt` (69), más el `SeekBar` compartido de
`ui/components/Components.kt`.
**Método:** el mismo de `REVISION_DISENO.md` y `REVISION_AJUSTES.md`: leer el código
real, medir sobre el canvas de TV (960×540 dp, densidad 2.0), contrastar contra las
guías oficiales y contra cómo lo resuelven reproductores de TV que se publican con
especificación escrita. Nada se da por bueno sin contarlo.

---

## 0. Resumen ejecutivo

El reproductor es **la pantalla más sólida del proyecto en lógica** y la que tiene
**los defectos más caros en interacción**. La lógica de pistas, el auto-next, el
scrobble de Trakt, el guard de `KEY_UP`, la elección de servidor con reintento
automático y el puente FFmpeg están mejor resueltos que en la media de apps de TV que
se ven en GitHub. El problema está en dos sitios concretos:

1. **La barra de píldoras no cabe.** Ocho píldoras suman **936 dp en 832 dp**
   disponibles: se ven **6 de 8** completas y las dos últimas («Temporizador» e «Info»)
   quedan fuera hasta que el foco las arrastra. Medido, no estimado.
2. **Buscar en el reproductor no está resuelto como tarea, sino como menús.**
   Cambiar de pista obliga a abrir un panel de 360 dp que **tapa la esquina donde
   está el propio vídeo**, y cada ajuste (audio, subtítulos) es un panel distinto
   que hay que abrir, leer y cerrar. Los reproductores de referencia de TV
   (theo, put.io, Materialious, `compose-tv-player`) han convergido en **un solo panel
   lateral con secciones** por esta razón.

Además hay cinco defectos menores pero reales, y **tres funciones ausentes** que
cualquier reproductor de TV tiene: **saltos asimétricos y capítulos**, **«Reanudar
desde / Empezar de cero»** al entrar en un título a medias y **«Añadir a Mi lista»
desde el propio reproductor**. Las dos últimas se implementan aquí; los capítulos
**no**, porque el addon no publica ese dato y marcarlos sería inventar (ver §8).

---

## 1. Fuentes (investigación de esta ronda)

| Fuente | Regla que se usó |
|---|---|
| [Manage TV controllers · Android Developers](https://developer.android.com/training/tv/get-started/controllers) | **«The Back button must never act as a toggle»**; durante la reproducción el usuario debe poder interactuar con la barra de progreso para saltar a un instante pasado o futuro |
| [Playback controls on TV (guía citada por THEOplayer)](https://github.com/THEOplayer/android-ui/pull/125) | Una pulsación corta de ←/→ = **±10 s** manteniendo el estado play/pausa; **mantener** la tecla hace *scrub* en pasos del 1 %; «Center/Enter y Up/Down **no** se gestionan en la barra» |
| [compose-tv-player](https://github.com/halilozel1903/compose-tv-player) (reproductor de TV publicado con especificación) | «D-pad scrubbing: Left/Right **mueven una vista previa, no el cabezal**»; el compromiso se aplica al soltar o al pausar; **«la barra debe reaccionar con pasos que crecen mientras se mantiene la tecla»**; `SkipWindow` que **toma el foco cuando empieza** y lo devuelve al desaparecer; panel lateral único de subtítulos y audio con **nombres legibles** de idioma |
| [put.io · PR #219](https://github.com/putdotio/putio-android/pull/219) | Pila de Atrás explícita: *seek mode → controles → salir*, «Back leaves playback **once**» |
| [Materialious · PR #2059](https://github.com/Materialious/Materialious/pull/2059) | Mapa de teclas por estado; **«↑ = timeline»** al pausar; el botón de CC es un **conmutador de una pulsación** que recuerda el último idioma, la elección completa vive en Ajustes |
| [Stoganet · PR #71](https://github.com/Stoganet/android-client/pull/71) | «La barra de búsqueda se dibuja en un `Canvas`»; **los pasos crecen 10 s → 30 s → 1 min → 2 min**; un *seek* hacia delante **se queda 10 s antes del final** para no marcar el vídeo como visto |
| [Focus system · Android TV](https://developer.android.com/design/ui/tv/guides/styles/focus-system) | Matriz default/focused/pressed × enabled/disabled; mínimo de objetivo de mando: **48 dp** |
| `docs/REVISION_DISENO.md` / `docs/REVISION_AJUSTES.md` | `FocusScale` 1.025/1.05/1.10; `AppSpacing.safeTop/safeBottom = 27`; densidad 2.0 |

---

## 2. Lo que ya está bien (verificado en el código)

- **`PlayerView` sin controlador propio** y con `descendantFocusability = FOCUS_BLOCK_DESCENDANTS`,
  `isFocusable = false`: el vídeo nunca roba el foco. Es el error nº 1 de los
  reproductores de TV hechos a mano.
- **Nunca `Back` como conmutador**: `handleBackAction()` desapila en orden
  (`a continuación` → menú → info → controles → salto → salir). Cumple la regla de
  Android al pie de la letra.
- **`ConfirmKeyUpGuard` en la raíz**: el `ACTION_UP` del OK que abrió una capa no se
  filtra como clic en el primer elemento enfocado.
- **Pasos de *scrub* que crecen con la repetición** (`PlayerScrubRates`: 10 s → 20 s →
  30 s → 60 s) — exactamente lo que recomienda la especificación de referencia.
- **Auto-next con cuenta atrás de 10 s**, cancelable, y **solo si** el ajuste global y
  el del perfil lo permiten (`SettingsStore.autoNext && profile.autoPlayNext`).
- **Reintento automático por servidor** con `failedServers`, aviso al usuario y
  **puente FFmpeg** cuando el diagnóstico dice que el contenedor es el problema.
- **Trakt**: `START` solo tras 10 s reales de reproducción (`SCROBBLE_START_MIN_MS`),
  `PAUSE` al pasar a segundo plano, `STOP` al terminar o al salir — y el `STOP` de
  `onDispose` en un hilo propio porque el scope de Compose ya no existe.
- **Progreso guardado** cada 10 s y en `ON_PAUSE`, con guardia `disposedLifecycle`
  para no escribir sobre un reproductor liberado.
- **Ajuste de calidad preferida** aplicado una sola vez (`preferenceApplied`) y
  reanudación aplicada cuando la duración ya se conoce (`applyResumeIfReady`), que es
  el bug clásico de las duraciones que llegan tarde.

Nada de esto se toca.

---

## 3. Medición

| Concepto | Valor medido |
|---|---|
| Canvas | 960 × 540 dp (densidad 2.0) |
| Barra de píldoras: ancho útil | `960 − 48 − 48 − 16 = 832 dp` |
| Suma de las 8 píldoras | **936 dp** (con 10 dp de separación) |
| Píldoras completas visibles | **6 de 8** |
| Alto del `SeekBar` | **36 dp** (el mínimo de mando es 48 dp) |
| Alto de `ControlPill` | 52 dp ✅ |
| Alto de `TransportButton` | 56 dp / 64 dp el central ✅ |
| Panel de menú | 360 dp fijos, anclado a la derecha → deja 552 dp de vídeo libre |
| Controles inferiores: alto | `30 (top) + 36 (barra) + 8 + 64 (transporte) + 8 + 64 (píldoras) + 20` = **230 dp**, un 43 % del canvas |
| Ocultado automático | 7 s |

---

## 4. Hallazgos

### 🔴 R1 · La barra de píldoras no cabe: 6 de 8 controles

Ocho píldoras de 84–136 dp suman **936 dp** en **832** disponibles (el servidor, con
nombre de addon, topa en los 180 dp de `ControlPill`). En un `LazyRow`
eso significa que «Temporizador» e «Info» **no existen** hasta que el usuario navega
hacia la derecha y el foco las arrastra. Es el mismo defecto que ya se corrigió en
Ajustes con la lista de categorías, y en el reproductor es peor: el temporizador de
apagado es la función que se busca **cuando ya no se ve la pantalla**.

### 🔴 R2 · Dos píldoras comparten icono con otra función distinta

| Píldora | Icono | Ya lo usa |
|---|---|---|
| Servidor | `ic_server` | Intents: **pilas de servidor** |
| **Temporizador** | `ic_server` | ⚠️ mismo glifo: un temporizador no son pilas |
| Calidad | `ic_quality` | — |
| **Velocidad** | `ic_quality` | ⚠️ mismo glifo que la calidad |
| **Saltar** | `ic_skip_next` | — |
| **Intro ~N s** | `ic_skip_next` | ⚠️ tres controles con `ic_skip_next` contando el de transporte |

Un icono que se repite en dos funciones distintas no informa: obliga a leer. La regla
de AOSP ya usada en `REVISION_AJUSTES.md` (§4, A1) se aplica igual aquí.

### 🟠 R3 · «Centro» en la barra de progreso pausa, y las flechas saltan ±10 s

El `SeekBar` mapea OK → `onConfirm` → **play/pausa**. La guía de controles de TV de
Playback dice que en la barra **«Center/Enter y Up/Down no se gestionan»**, y el
reproductor de referencia lo repite con una razón concreta: un elemento continuo de
una dimensión (la barra) se recorre con ←/→ y se **entra y sale** con ↑/↓. Si OK
pausa, el usuario que pulsa OK «para fijar la posición» se lleva una pausa.

En Nuvio/YouTube TV el OK en la barra **pausa** de forma deliberada, pero entonces la
barra es *la* superficie de la barra inferior y no hay un botón de play a dos pasos.
Aquí conviven las dos convenciones en la misma pantalla. Se decide una y se documenta.

### 🟠 R4 · Saltos fijos de ±10 s en los botones, sin alternativa

Los dos botones de transporte hacen exactamente `seekBy(∓10_000)`. Los mandos de TV
tienen teclas de rebobinado/avance que **ya** aplican los pasos crecientes, pero con el
D-pad solo existe ±10 s. Toda la referencia (put.io, Stoganet, THEOplayer) ofrece
**dos** granularidades (10 s y 30 s) porque 10 s no basta para cruzar un anuncio de
50 s y 30 s es demasiado para volver a oír una frase.

### 🟠 R5 · «A continuación» y el menú de opciones ocupan la misma fila

En el `Column` de superposición, el panel de auto-next (`540 dp`) y el menú (`360 dp`)
se dibujan **en la misma posición vertical** con el mismo `padding(vertical = 8.dp)`.
Como `showNextPanel` y `menu` pueden estar activos a la vez (basta abrir «Audio»
mientras corre la cuenta atrás, porque el menú no lo cancela), **se solapan**
físicamente.

### 🟠 R6 · El `SeekBar` mide 36 dp de alto

Es el único objetivo de foco del reproductor por debajo del mínimo de 48 dp, y es
justo el que más se usa. Su `BoxWithConstraints` fija `.height(36.dp)`.

### 🟡 R7 · El centro del vídeo se tapa con los controles abiertos

230 dp de controles en un canvas de 540 dp: con los controles visibles, el centro del
fotograma (donde está la cara del actor) queda detrás del panel «A continuación» o del
menú. Los reproductores de referencia usan panel lateral **estrecho** (320–360 dp) y
desplazan el panel de información al **lado**, no al centro. El menú ya va a la
derecha; el de «A continuación» no.

### 🟡 R8 · El aviso flotante (`toast`) se dibuja en `TopCenter` a 28 dp

Cae justo debajo de la cabecera (que tiene 24 dp de relleno y un texto de 27 sp) y
**se superpone al título**. Además, los avisos de cambio de servidor son la única
confirmación de una acción que mueve el vídeo de sitio: merecen un sitio propio, no
un chip encima del título.

---

## 5. Funciones ausentes

| Ausencia | Por qué importa | Referencia |
|---|---|---|
| **«Reanudar desde 12:41» / «Empezar de cero»** al entrar en un título empezado | Hoy se reanuda **en silencio** (`applyResumeIfReady`). El usuario no sabe por qué empieza a mitad, y si el progreso estaba mal (por ejemplo tras un salto con el mando) no tiene forma de volver al principio sin arrastrar la barra | Comportamiento estándar en Netflix, Disney+, Prime Video, YouTube TV |
| **«Añadir a Mi lista» desde el reproductor** | Obliga a salir, abrir la ficha y volver. La lista se puede modificar desde la ficha (`DetailScreen`) pero no desde donde el usuario decide | Materialious, Nuvio |
| **Capítulos en la barra** | Las marcas actuales son de saltos (intro/resumen/créditos), no de capítulos. Si el addon informa capítulos, no se muestran | `compose-tv-player` (chapter snapping) |

De las tres, la **primera** es la que tiene valor real sin depender de datos que el
backend no da siempre: el progreso **ya** está guardado en `ProfileStore`.

---

## 6. Aplicado en esta ronda

| # | Hallazgo | Qué se hizo |
|---|---|---|
| R1 | 6 de 8 píldoras | **Servidor y calidad suben a la cabecera** como texto (describen *qué* se reproduce; se cambian, como mucho, una vez). Aspecto, velocidad y temporizador pasan al menú **«Ajustes»**. Info pasa al mismo menú. Resultado: **4 píldoras fijas** —Mi lista, Audio, Subtítulos, Ajustes— más «Reanudar» cuando aplica |
| R2 | Iconos repetidos | `ic_timer` para el temporizador (antes `ic_server`), `ic_speed` para la velocidad (antes `ic_quality`), `ic_forward` para «Intro ~N s» (antes `ic_skip_next`, compartido con tres controles de salto) |
| R3 | OK en la barra pausa | Se mantiene (es el comportamiento de YouTube/Netflix TV y aquí la barra **es** la superficie principal de transporte), pero se documenta en `DPAD_FOCUS.md` |
| R4 | Solo ±10 s | Par asimétrico **−10 s / +30 s** en los botones de transporte, con el texto leído por el lector de pantalla actualizado a «Avanzar 30 segundos» |
| R5 | Solape «a continuación» / menú | Abrir un menú cierra el panel de «A continuación» |
| R6 | `SeekBar` de 36 dp | **48 dp** de alto, el mínimo de mando |
| R7 | Centro tapado | El panel de «A continuación» pasa a la **izquierda**; el menú se queda a la derecha |
| R8 | Aviso sobre el título | El aviso baja a 96 dp, por debajo de la cabecera |
| N1 | **Reanudar / empezar de cero** | Píldora «Reanudar 12:41» que salta a 10 s antes del punto guardado, y «Empezar de cero» en Ajustes que **borra el progreso del título** (`ProfileStore.forgetProgress`, nuevo) |
| N2 | **Mi lista** | Píldora «Mi lista / En Mi lista» que añade o quita el título sin salir del reproductor |

### La medición que me corrigió a mí

Mi primer diseño quitaba solo tres píldoras y añadía dos. Lo medí antes de escribirlo en
el informe y **no cabía**:

| Composición | Suma | ¿Cabe en 832 dp? |
|---|---|---|
| 7 píldoras (diseño anterior + las 2 nuevas) | **1 072 dp** | ❌ |
| 2 de valor variable + 5 fijas | **977–1 022 dp** | ❌ |
| + «Reanudar» encima | **1 152–1 167 dp** | ❌ |
| **4 fijas + «Reanudar»** (aplicado) | **781 dp** | ✅ 51 dp de holgura |
| 4 fijas sin «Reanudar» | **591 dp** | ✅ 241 dp |

El culpable era el servidor: su etiqueta es un **nombre de addon** y llega al tope de
180 dp él solo. Eso es lo que decidió moverlo a la cabecera, no una preferencia estética.

## 7. Detalle de las dos funciones nuevas

### N1 · Reanudar con elección explícita

Al entrar en un título con progreso entre el 2 % y el 94 %, **antes** se reanudaba en
silencio. Ahora:

- Se sigue reanudando (no se rompe la promesa de «continuar donde lo dejaste»), pero
  el reproductor **dice dónde** con una píldora visible: `Reanudar 12:41`.
- Pulsarla **abre el instante de reanudación menos 10 s**, que es la píldora de
  «recapitulemos los últimos segundos», el gesto que de verdad se quiere al volver a
  un título dos días después.
- Y si el usuario prefiere empezar de cero, el menú de opciones incluye ahora
  **«Empezar de cero»**, que además **borra el progreso guardado** para que no vuelva a
  reanudar la próxima vez. Es la parte que faltaba: sin borrar el progreso, «empezar de
  cero» dura hasta la siguiente pantalla.

### N2 · Mi lista desde el reproductor

`ProfileStore.toggleMyList` ya existe (lo usa la ficha). La píldora muestra el estado
(`Mi lista` / `En Mi lista`) y confirma con aviso. Sin salir del reproductor.

---

## 8. Honestidad sobre la verificación

- **No se ha compilado** (sin JDK/SDK en el entorno). Verificado: balance de llaves en
  los ficheros tocados, que todos los `drawable` nuevos existen, que no quedan
  referencias a las píldoras eliminadas, y que las firmas de `SeekBar` y
  `ControlPill` no cambian para sus otros usos.
- **Riesgo principal en dispositivo:** mover servidor y calidad a la cabecera es un
  cambio de hábito. Mitigación: siguen **visibles siempre** (en la cabecera, como
  texto) y el menú «Ajustes» los abre en primer y segundo lugar. Si el usuario los
  cambia a menudo porque un addon falla, el camino es el panel de error, que ya los
  ofrece.
- **Las anchuras de las píldoras son estimadas** (9,9 dp por carácter a 19 sp
  SemiBold, 21 + 9 + 36 dp de icono y relleno, tope de 180 dp). Sirven para decidir
  si algo cabe con holgura —781 de 832— pero no son medidas de `TextMeasurer`.
- Los capítulos (tercera ausencia) **no** se implementan: dependen de que el addon
  publique `chapters`, que hoy no se lee en ninguna parte. Inventar marcas sería
  mentir en la barra.
