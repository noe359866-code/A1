# Revisión de la pantalla de Ajustes — diseño y funciones nuevas

**Fecha:** 17 de noviembre de 2026 · **Rama:** `arena/30f67343-a1`
**Objeto:** `app/src/main/java/com/omnitv/app/ui/screens/SettingsScreen.kt` (1 738 líneas
antes de esta ronda) y las piezas compartidas que usa (`SettingRow`, `TvToggle`,
`SettingsStore`).
**Método:** el mismo que en `REVISION_DISENO.md` — leer el código real, medir sobre el
canvas de TV (960×540 dp, densidad 2.0), contrastar contra la documentación oficial y
**no dar nada por bueno sin haberlo contado**. El informe anterior deja una lección
aprendida que aquí se aplica desde la primera línea: las conversiones de unidades se
hacen con la densidad real (2.0), no con 1.0.

---

## 0. Resumen ejecutivo

Ajustes **no** era una pantalla mala: ya era de dos paneles (lo que recomienda Android
para contenido jerárquico), ya tenía grupos con divisores visuales, ya restauraba el
foco al cerrar un diálogo y ya bloqueaba con PIN el acceso desde un perfil infantil.
Nada de eso había que inventarlo.

Lo que sí tenía eran **siete defectos medibles** y **una carencia grande**:

| # | Hallazgo | Gravedad |
|---|---|---|
| A1 | Dos categorías comparten icono (`INTEGRATIONS` y `NETWORK` usan `ic_server`) | 🟠 medio |
| A2 | Dos paradas de foco en la misma fila (la fila *y* su interruptor) | 🔴 alto |
| A3 | «Limpiar caché» vacía 4 cachés al primer OK, sin confirmación | 🔴 alto |
| A4 | La lista de categorías no cabe: 5 de 8 visibles | 🟠 medio |
| A5 | Subtítulos de hasta 4 líneas a 18 sp; 16 textos pasan de 2 líneas | 🟠 medio |
| A6 | El título «Ajustes» a 54 sp rompe la escala del resto de pestañas (40 sp) | 🟡 bajo |
| A7 | La fila «Saltar X automáticamente» se contradice con su propio clic | 🟠 medio |
| **B** | **No hay buscador**: 27 ajustes en 8 categorías, todo a memoria | 🔴 **alto** |

Se aplicaron los siete y se añadieron tres funciones nuevas: **buscador de ajustes**,
**restablecer preferencias** y **reactivar todas las fuentes de tramos**. Lo aplicado
está en §6; lo que se propone y **no** se hizo, con motivo, en §7.

---

## 1. Fuentes (investigación de esta ronda)

| Fuente | Regla que se usó |
|---|---|
| [Android settings design guidelines (AOSP)](https://source.android.com/docs/core/settings/settings-guidelines) | *«Users should be able to glance at settings screens and understand all of the individual settings and their values»*; **limitar a 10–15 elementos por pantalla**; *«avoid placing buttons inside a list item»*; iconos solo cuando aportan y **nunca el mismo icono para todos**; *«avoid generic titles»*; progressive disclosure («Advanced») **solo con 3+ elementos**; usar `switch` antes que `checkbox`; los botones de acción negativa (borrar, desinstalar) van **en gris**, no en color de acento |
| [Lists · Android TV](https://developer.android.com/design/ui/tv/guides/components/lists) | Tres variantes (1/2/3 líneas); la de 2 líneas es la que «evita sobrecarga cognitiva»; la de 3 líneas es decorativa y **solo para dar prominencia alta**; el control de selección puede ir delante o detrás; cuidado con fondos por ítem |
| [Layouts · Android TV](https://developer.android.com/design/ui/tv/guides/styles/layouts) | Dos paneles para contenido jerárquico ✅ (ya era el caso); overscan 48 dp lateral / **27 dp vertical**; no abusar de paneles |
| [Focus system · Android TV](https://developer.android.com/design/ui/tv/guides/styles/focus-system) | Matriz default/focused/pressed × enabled/disabled; el deshabilitado baja opacidad y **no debe parecer pulsable** |
| [TV App Quality · Google Play](http://docs.52im.net/extend/docs/api/android-50/distribute/essentials/quality/tv.html) | TV-OV: nada recortado; TV-DP: navegable con D-pad de 5 vías; TV-TC ≥16 sp |
| `docs/REVISION_DISENO.md` | Canvas 960×540, densidad 2.0, `FocusScale` 1.025/1.05/1.10, `AppSpacing.safeTop/safeBottom` ya aplicados |

La propia AOSP avala lo que ya estaba bien: *«duplicating a setting across entry points
is acceptable»* — por eso el enlace a «Configurar en Vincular (QR)» dentro de
Integraciones **no** se ha tocado.

---

## 2. Medición de la pantalla (números, no impresiones)

Canvas **960 × 540 dp**. Márgenes reales: `start = 48 + 84 = 132 dp`, `end = 48 dp`
→ fila de **780 dp**. Columna izquierda **330 dp** + 24 dp de separación → panel derecho
**426 dp** → dentro de una tarjeta (22 dp por lado) quedan **370 dp** de texto útil,
que a 21 sp son **~35 caracteres por línea** y a 18 sp **~41**.

Altura útil de la fila: `540 − 27 − 27 = 486 dp`. Restando cabecera de sección (48 dp)
y 20 dp de separación, el panel derecho **visible** es de **~376 dp**, un 70 % del
canvas.

### Contenido por categoría (contado en el código)

| Categoría | Fila(s) con control | Tarjetas | Botones | Alto estimado | Pantallas de scroll |
|---|---|---|---|---|---|
| PROFILE | 4 toggles | 3 | 2 | ~900 dp | 2,4 |
| PLAYBACK | 4 toggles | 1 | 0 | 456 dp | 1,2 |
| QUALITY | 2 filas de valor | 1 | 0 | 266 dp | 0,7 |
| SKIP | **9 toggles** | 1 | 0 | 1 147 dp | **3,1** |
| PAIRING | 0 | 1 | 1 | ~620 dp | 1,6 |
| INTEGRATIONS | 1 + N scrapers | **4** | 2 | ~1 100 dp | 2,9 |
| NETWORK | 1 toggle | 1 | 0 | 195 dp | 0,5 |
| SYSTEM | 3 filas + 1 acción | 1 | 1 | ~480 dp | 1,3 |

Dos desequilibrios claros, medidos contra la regla de «10–15 elementos»:

- **SKIP tiene 9 filas de interruptor + un pie de 3 líneas** en un panel de 376 dp
  visibles. Son **3,1 pantallas** de desplazamiento para una sola categoría.
- **INTEGRATIONS apila 4 tarjetas**, que es justo lo que la guía de layouts dice que
  no se haga («no abusar de paneles»). Cada tarjeta vuelve a empezar el patrón
  cabecera + contenido.

Y el panel izquierdo, con 8 categorías de 64 dp + 8 dp de separación y una cabecera de
114 dp, necesita **690 dp en 486 disponibles** → **solo 5 de las 8 categorías** se ven
sin desplazar. En una pantalla cuyo propósito es *«que de un vistazo se entienda todo»*,
que falten 3 de 8 categorías es un fallo de la propia regla.

### Subtítulos largos

Contados con el ancho real (41 caracteres por línea a 18 sp): de los literales de
interfaz, **16 pasan de dos líneas**, y el peor llega a **4 líneas** (164 caracteres,
sobre el cifrado del token de Trakt). La variante de lista de TV recomienda **dos
líneas**; la de tres es «decorativa».

---

## 3. Lo que ya está bien (verificado, no asumido)

- **Dos paneles**: exactamente lo que pide la guía de layouts para contenido jerárquico.
- **El valor actual se ve en el panel izquierdo** (`badgeText`): calidad, número de
  configuraciones, IP del DNS, versión… Es la regla «*provide a good overview*» de
  AOSP aplicada antes de leerla.
- **Foco**: `categoryRequesters` por categoría, `tabContentFocus` conectado al
  `TvScaffold`, `restoreAfterModal` al cerrar calidad/velocidad/Trakt/PIN, y 6
  reintentos por frame con `withFrameNanos`. Sólido.
- **Puerta de PIN** con tres tareas (`GATE`/`SET`/`CLEAR`) y salida por `onExit()` si se
  cancela la puerta — no deja al usuario encerrado.
- **No hay botones dentro de las filas**: `SettingRow` acepta `onClick` en la fila y el
  control va aparte. (El defecto A2 es lo contrario: el control *también* recibe foco.)
- **Un solo icono por tarjeta**, no un icono repetido en cada fila.

---

## 4. Hallazgos

### 🔴 A2 · Dos paradas de foco en la misma fila

Nueve filas de Ajustes son `SettingRow(onClick = …) { TvToggle(...) }`. El interruptor
tiene su propio `clickable` **y** su propio anillo de foco (`FocusScale.hero`, 40 dp de
alto). Resultado: con el D-pad hay **dos** destinos por fila, el anillo blanco de la fila
y el del interruptor se encienden por separado, y el usuario no sabe si OK va a activar
«Autoplay» (fila) o a mover el interruptor. La matriz de foco de Android TV contempla
`disabled` justamente para esto: un control que **no** es un objetivo.

### 🔴 A3 · Acción destructiva sin confirmación

`SystemSettingsContent` ejecutaba, en un solo clic:

```kotlin
RemoteCatalog.invalidateCache(); AnimeCatalog.invalidateCache()
NuvioPluginManager.clearCache(); NuvioScraperResolver.clearResultCache()
```

Cuatro cachés, sin preguntar, sin tamaño, sin confirmación y sin forma de deshacer. En
el mismo archivo ya existía `TvConfirmDialog` (usado en perfiles) y no se usaba aquí.

### A1 · Icono duplicado

`NETWORK("Red y Privacidad", R.drawable.ic_server, …)` y
`INTEGRATIONS("Puentes y Plugins", R.drawable.ic_server, …)`. La guía es explícita:
un icono que no distingue no informa. La tarjeta de NETWORK repetía el mismo glifo, así
que la categoría entera era «la de las pilas de servidores».

### A6 · Un título de 54 sp entre títulos de 40

«Ajustes» se dibujaba con `AppType.hero` (54 sp). Las otras cinco pestañas
(Películas, Anime, Plataformas, Buscar, Inicio) usan `screenTitle` (40 sp). En una
columna de 486 dp de alto, 20 dp de diferencia se notan: es la única pantalla de la
navegación que rompe la escala.

### A4 / A5 / A7 · Densidad, subtítulos, contradicción

- 64 dp por categoría → 5 de 8 visibles.
- 16 subtítulos de más de 2 líneas en 370 dp de ancho.
- La fila «Saltar intro automáticamente» decía *«Activa "Tramos saltables" arriba para
  habilitar»* y a la vez, al pulsarla, **activaba** «Tramos saltables». El texto
  describía un obstáculo que el propio clic resolvía.

Además, `SkipKind.POST_CREDITS` existía en el modelo y el reproductor le daba color
propio en la barra, pero la pantalla recorría una lista literal de cuatro tipos y lo
dejaba fuera: **no había forma de activar el salto de la escena final desde Ajustes**.

---

## 5. Función nueva: buscador de ajustes

### El problema

Con 27 ajustes repartidos en 8 categorías cuyo reparto no es obvio (¿la velocidad está
en «Reproducción» o en «Calidad y Velocidad»? ¿el DNS en «Red» o en «Puentes»?), el
usuario tiene que **recordar** dónde vive cada cosa. La guía de AOSP lo dice al revés:
*«make settings easy to find»*.

### El diseño

- Índice declarativo de **25 entradas** (`SETTINGS_SEARCH_INDEX`), una por ajuste, con
  título, detalle y **sinónimos que la interfaz no usa** («oscurecer», «contraseña»,
  «caché», «fabrica», «cc»). El usuario busca con sus palabras, no con las nuestras.
- **Plegado de acentos** (`foldForSearch`): «subtitulos» encuentra «Subtítulos», «nino»
  encuentra «niño». Sin esto, la mitad de las búsquedas en español fallarían.
- Búsqueda por **todas las palabras** (`words.all { … }`), no por frase exacta: «pin
  perfil» encuentra «Control parental» y «Código de emparejamiento».
- **Panel a pantalla completa** con el teclado propio de TV (`TvKeyboard`,
  `embedded = true`), resultados arriba y teclado abajo. No es un capricho: en el panel
  derecho (426 dp) no caben campo + lista + teclado sin bajar de 12 sp, y en TV escribir
  exige el teclado de la app porque el IME del sistema no se conduce con el mando.
- **Los resultados no ejecutan el ajuste**: llevan a su categoría. Es honesto: activar
  «salto automático de créditos» sin ver el resto de la tarjeta deja al usuario sin
  contexto. Cada resultado muestra en versalitas a qué categoría va.
- **Atajo de mando**: tecla de color azul (`KEYCODE_PROG_BLUE`, 186). Se registra en la
  raíz de la pantalla con `onPreviewKeyEvent`, así que no le quita el OK a ninguna fila
  y funciona con el foco donde esté. No se abre si el PIN parental está pendiente
  (`!gated`): el buscador también revela qué ajustes existen.
- **Vuelta de foco**: al cerrar el buscador, el foco vuelve a la categoría activa en vez
  de quedarse huérfano.
- Entrada visible: una fila «Buscar en Ajustes» con `ic_search` en la parte alta del
  panel izquierdo, que **no** es una categoría (abre el teclado, no cambia de panel).

### Verificación del índice

| Categoría | Entradas | Categoría | Entradas |
|---|---|---|---|
| PROFILE | 2 | PAIRING | 4 |
| PLAYBACK | 4 | INTEGRATIONS | 4 |
| QUALITY | 2 | NETWORK | 2 |
| SKIP | 3 | SYSTEM | 4 |

**25 entradas** para 8 categorías y ~27 ajustes visibles: ninguna categoría se quedó sin
representación.

---

## 6. Aplicado en esta ronda

| # | Hallazgo | Estado | Qué se hizo |
|---|---|---|---|
| A1 | Icono duplicado | ✅ | Nuevo `ic_network.xml` (globo + meridianos); `NETWORK` deja de compartir `ic_server` con `INTEGRATIONS`, también en su tarjeta |
| A2 | Dos paradas de foco | ✅ | `TvToggle(focusable: Boolean = true)`: con `focusable = false` se anula `canFocus` y el clic, y el interruptor queda como **indicador** de la fila. Las 9 filas de Ajustes lo usan; en edición de perfiles (donde la fila no es pulsable) sigue siendo `true` |
| A3 | Borrado sin confirmar | ✅ | `TvConfirmDialog` antes de limpiar caché, y botón deshabilitado tras limpiar (`PillButton(enabled = false)` de la ronda anterior) con línea de estado |
| A4 | 5 de 8 categorías | ✅ | Categoría de 64 → **48 dp** (el mínimo de mando) y separación de 8 → 4 dp: **6 de 8 visibles** y el total baja de 690 a 572 dp |
| A5 | Subtítulos excesivos | ✅ (parcial) | Se acortaron los peores textos fijos (fuentes de tramos, descripción de la caché) y los nuevos textos se escribieron ≤ 2 líneas. **No** se recortó el texto explicativo de PIN/Trakt: es información de seguridad y se prefiere scroll a ambigüedad (ver §7) |
| A6 | Título a 54 sp | ✅ | `AppType.screenTitle` (40 sp), igual que el resto de pestañas |
| A7 | Texto contradictorio | ✅ | «Al activarlo se activan también los "Tramos saltables"» — describe lo que el clic hace de verdad |
| A8 | `POST_CREDITS` inalcanzable | ✅ | `SkipKind.entries` en lugar de una lista literal de cuatro |
| B1 | Sin buscador | ✅ **nuevo** | Ver §5: índice de 25 entradas, plegado de acentos, panel con teclado propio y atajo de mando |
| B2 | Sin restablecer | ✅ **nuevo** | `SettingsStore.resetPreferences()`: devuelve reproducción, saltos y plugins desactivados a fábrica y **no toca** perfiles, PIN, addons, claves ni Trakt. Dice cuántos ajustes cambiaron |
| B3 | Fuentes sin reactivar | ✅ **nuevo** | «Activar todas las fuentes» cuando falta alguna, para no ir una por una |

### Cambios en piezas compartidas (y por qué)

- **`TvToggle(focusable)`** — no existía forma de decir «este control no es un destino
  del mando». Se añade con valor por defecto para no tocar los dos usos de
  `EditProfileScreen`.
- **`TvKeyCodes.isSearchShortcut` / `KEYCODE_PROG_BLUE`** — en `TvDpadLogic`, que es
  lógica pura y testeable con JVM, no en la pantalla.
- **`SettingsStore.resetPreferences()`** — devuelve el número de ajustes cambiados para
  poder decir «se cambiaron 4 ajustes» en vez de un «hecho» vacío.

---

## 7. Propuesto y **no** hecho (con motivo)

Estas son las funciones que un Ajustes de referencia tiene y este no. No se hicieron en
esta ronda por una razón concreta, no por falta de tiempo:

| Propuesta | Por qué no ahora |
|---|---|
| **Caché con tamaño real** («142 MB») antes de borrar | `RemoteCatalog`/`AnimeCatalog` son cachés **en memoria** (`HashMap`), no en disco. No hay bytes que enseñar: mostrar «0 MB» sería peor que no mostrarlo. Requiere medir `cacheDir` de Cronet y Coil por separado |
| **Copiar/restaurar ajustes** (exportar a JSON) | Necesita selector de fichero o SAF en TV y decide qué hacer con las claves cifradas. Es una función de producto, no un arreglo |
| **Ajustes por perfil** (hoy casi todo es global) | `ProfileStore` ya guarda `autoPlayNext` por perfil y `PlayerScreen` combina ambos (`SettingsStore.autoNext && profile.autoPlayNext`). Extenderlo a calidad/velocidad/subtítulos toca el reproductor: otra tarea |
| **Vista «Advanced» colapsable** | La guía lo permite **solo con 3+ elementos ocultos y una línea que los nombre**. En SKIP las 9 filas son igual de relevantes; esconder 4 no reduce el problema, lo esconde |
| **Partir SKIP en dos categorías** | Subiría a 9 categorías en un panel que ya no cabe. La solución estructural es el buscador (hecho) y, más adelante, un SKIP con subsecciones |
| **Editar addons/claves desde el TV** | El diseño actual **deliberado** es que eso se hace desde el panel web por QR (teclado de TV para URLs largas es hostil). No es una carencia |
| **Iconos por fila dentro de las tarjetas** | La guía dice lo contrario: iconos solo cuando distinguen. En una lista homogénea de interruptores, 9 iconos son ruido |

---

## 8. Honestidad sobre la verificación

- **No se ha compilado.** Este entorno no tiene JDK ni Android SDK: no se puede ejecutar
  `./gradlew assembleDebug`, ni lint, ni tests de Compose. Lo que sí se verificó:
  balance de llaves (`0` en los cuatro ficheros tocados), paréntesis (`−2` en
  `SettingsScreen.kt`, **idéntico a `HEAD`**: viene de texto en español, no de esta
  ronda), imports nuevos presentes (`heightIn`, `TvKeyboard`, `TvKeypad`, `GlassFill`,
  `TvConfirmDialog`) y sin duplicados, y que ningún `SystemSettingsContent()` con la
  firma antigua quedaba sin actualizar.
- **`check_build_readiness.py`** → «Listo para compilar» (1 aviso, el de la keystore).
  **`check_android_tv.py`** → contrato de TV OK. **`node --test`** → 16/16.
- **No se ha ejecutado en un televisor.** Los riesgos reales que quedan:
  1. La altura de `CategoryItem` a 48 dp depende de que el icono (36 dp) y el texto
     (19 sp, `lineHeight` 24) quepan con 6 dp de relleno. Debería, pero es lo primero
     que hay que mirar en pantalla.
  2. El teclado `embedded` mide `10 + 4·34 + 3·3 + 10 ≈ 165 dp` de alto; en el panel de
     búsqueda va después de un `Spacer(weight 1f)`, así que debería quedar abajo. Si el
     dispositivo tiene overscan agresivo, conviene comprobar que la fila de acciones
     («ESPACIO», «LIMPIAR») no se recorta.
  3. La tecla azul (`KEYCODE_PROG_BLUE = 186`) no existe en todos los mandos. Es un
     **atajo**, no la única vía: la fila «Buscar en Ajustes» sigue estando en el panel.
- Que el CI compile `assembleDebug` + `assembleRelease` (R8) y pase lint en el próximo
  push es lo que confirmará que no hay error de compilación.
