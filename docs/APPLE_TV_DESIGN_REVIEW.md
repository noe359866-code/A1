# Revisión de diseño · inspiración Apple TV / tvOS

> **⚠️ Estado:** este documento es de 2026-11-14 y describe una versión anterior. **La referencia vigente es la «Actualización 2026-11-16» (sección 10, final del documento)**, que indica qué hallazgos están corregidos y cuáles siguen pendientes.

Fecha: 2026-11-14. Rama: `arena/5218389f-a1` (HEAD `5c9a57c`). Alcance: tema, tokens, componentes, navegación, pantallas, reproductor, banner y recursos de `app/src/main`, contrastados con las [Human Interface Guidelines de Apple](https://developer.apple.com/design/human-interface-guidelines/) para tvOS. **No se modificó código de la app**: este documento es una auditoría de diseño.

---

## 1. Veredicto

**La app no es un clon de Apple TV; es un híbrido «10 pies» con fuerte inspiración tvOS.** La base visual (oscuro, acento azul, pestañas superiores, héroe tipo *Watch Now*, foco con escala + sombra, píldoras) sí se acerca al lenguaje de la app TV de Apple. Pero conviven, de forma explícita en el código y el README, otras influencias: **perfiles tipo Netflix**, **reproductor tipo Xuper TV**, secciones *Only on* / *Browse by studio* de flystream/sweetfox y un **teclado D-pad propio**.

Calificación como «diseño basado en Apple TV»: **7/10 en intención, 5/10 en fidelidad estricta a tvOS**. Es un buen diseño para Android TV, pero la etiqueta «Diseño Apple TV» del README es demasiado literal. La recomendación principal es renombrarla a **«Inspirado en tvOS, adaptado a Android TV»** y documentar el híbrido.

Lo más sólido: paleta oscura coherente, jerarquía tipográfica de 10 pies, foco siempre visible, navegación por pestañas con restauración de foco, márgenes laterales conservadores y estados vacíos honestos.

Lo más alejado de tvOS: **no hay parallax ni imágenes por capas** (el efecto firma de Apple TV), la barra de pestañas no sigue la métrica de altura/posición ni se oculta al desplazarse, la escala tipográfica sigue las guías de Android TV y no las de tvOS, no hay materiales translúcidos reales ni variantes de alto contraste, y Ajustes es una columna densa en lugar de una vista dividida.

> Muchas de esas diferencias son **correctas** porque el objetivo es Android TV / Google TV, no tvOS. Copiar el *Top Shelf*, el icono por capas, Siri/dictado o el botón *Menu* sería un error de plataforma. Se separan más abajo las desviaciones que sí conviene corregir de las que conviene mantener.

---

## 2. Fuentes consultadas

- [Layout · tvOS safe area, grids y padding](https://developer.apple.com/design/human-interface-guidelines/layout.md): 60 pt arriba/abajo, 80 pt a los lados; 40 pt entre columnas; 100 pt mínimos entre filas con título; contenido parcialmente visible para indicar scroll.
- [Focus and selection](https://developer.apple.com/design/human-interface-guidelines/focus-and-selection.md): foco direccional en tvOS, hasta cinco estados visuales, elevación/iluminación/animación, preferir efectos del sistema.
- [Images · parallax y layered images](https://developer.apple.com/design/human-interface-guidelines/images.md): el parallax es central en tvOS; imágenes por capas obligatorias en el icono y muy recomendadas en contenido enfocable.
- [Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars.md): altura 68 pt, borde superior a 46 pt, barra translúcida, pestaña seleccionada opaca con sombra, se oculta al desplazar en vistas simples y vuelve a foco con *Menu*.
- [Typography](https://developer.apple.com/design/human-interface-guidelines/typography.md): tvOS 29 pt por defecto, 23 pt mínimo, SF Pro, Dynamic Type, evitar pesos finos.
- [Color](https://developer.apple.com/design/human-interface-guidelines/color.md) y [Materials](https://developer.apple.com/design/human-interface-guidelines/materials.md): colores de sistema adaptables a claro/oscuro/alto contraste; materiales con blur/vibrancy, no solo rellenos con alpha.
- [Text fields](https://developer.apple.com/design/human-interface-guidelines/text-fields.md): «Minimize text entry in your tvOS apps».

---

## 3. Matriz de cumplimiento

| Criterio tvOS | Referencia Apple | Implementación en Omni TV | Valoración |
|---|---|---|---|
| Zona segura | 60 pt vertical / 80 pt horizontal | `AppSpacing.screenMargin = 48.dp` horizontal; `TopTabBar` con `top = 22.dp`, `bottom = 14.dp`; separador de 2 dp | **Parcial**. 48 dp lateral es más conservador que 80/1920 pt; el margen superior de 22 dp queda por debajo del ~5 % recomendado en Android TV y muy lejos de tvOS. |
| Barra de pestañas | 68 pt de alto, borde a 46 pt, translúcida, selección opaca + sombra, se oculta al scroll | `TopTabBar` pegada al borde superior, ~86 dp de alto, fondo opaco `Background`, línea separadora, siempre visible; selección `GlassFillStrong`, foco blanco | **Inspirada, no fiel**. Buenos estados de foco/selección; falta translucidez, sombra de selección, ocultado al scroll y retorno con *Menu*. |
| Foco | Parallax, elevación, iluminación, animación, hasta 5 estados | Escala por jerarquía tokenizada (`FocusScale` 1.02–1.12), anillo de 3 dp blanco/oscuro, sombra animada 18 dp en `PosterCard`, `hasFocus` en `SettingRow`, estado *pressed* (×0.94) en controles principales (2026-11-16) | **Bien resuelto para Android TV, distinto de tvOS**. Falta parallax, iluminación y estado *unavailable*. |
| Imágenes por capas | Obligatorias en icono, recomendadas en pósters | `AsyncImage` simple en `PosterCard`/`HeroBanner`; `tv_banner.xml` vector estático 320×180 | **Mayor desviación visual**. No hay `.lcr`, capas ni paralaje. Es esperable en Android TV, pero conviene no venderlo como tvOS exacto. |
| Tipografía | SF Pro, 29 pt base, 23 pt mínimo, Dynamic Type | `FontFamily.SansSerif`; `AppType.body = 21.sp`, `metadata = 18.sp`, `chip = 15.sp`; 14 `TextStyle(...)` literales fuera del tema, con 14/16/17/19/20/22/26/28 sp | **Escala Android TV, no tvOS**. Internamente además no está unificada: `PlayerUi.EpgChip` usa 14 sp por debajo del mínimo declarado de 15 sp. |
| Color / materiales | Colores de sistema adaptables, Liquid Glass / blur / vibrancy | Hex fijos tipo *systemBlue dark* (`#0A84FF`), `GlassFill` = `#28787880` plano, `Theme.kt` solo configura `darkColorScheme` | **Coherente pero rígido**. Sin modo claro, sin alto contraste, sin blur real; el «vidrio» es un alpha, no un material. |
| Navegación | Pestañas visibles, pocas, etiquetas, SF Symbols, estado preservado | 5 pestañas con etiqueta + icono vector, `selectTab` con `saveState`/`restoreState`, `focusRestorer()` en filas, foco inicial por pantalla | **Alineado**. Es de lo mejor del diseño. |
| Rejillas | 2–9 columnas, 40 pt horizontales, 100 pt verticales, simetría al cortar | 5 columnas, 20 dp horizontales, 24 dp verticales; `contentPadding(end = 48.dp)` para insinuar más contenido | **Funcional, más denso**. 5 columnas entra en la recomendación; el espacio vertical es aproximadamente la mitad en términos relativos. |
| Entrada de texto | Minimizar, dictado, búsquedas recientes, autocompletar | Teclado propio de 37 teclas en panel lateral, sin dictado ni recientes; emparejamiento descargado al móvil por QR | **Correcto el QR, mejorable la búsqueda**. El teclado es excelente para D-pad, pero es la única vía de texto. |
| Ajustes | Vista dividida, barra fija, revelación progresiva | `SettingsScreen.kt` de 626 líneas, `Column` con scroll vertical, 10 tarjetas, QR, 18 `StatusLine` | **Denso para 10 pies**. Es la pantalla que menos se parece a Apple TV. |
| Perfiles | tvOS delega el usuario al sistema / Control Center | Selector «¿Quién está viendo?» tipo Netflix con avatares y PIN | **Híbrido deliberado**, no Apple TV. Está bien documentado como «Perfiles Netflix». |

---

## 4. Fortalezas concretas

1. **Sistema de foco legible a distancia.** `AppStroke.focus = 3.dp`, anillo blanco sobre superficies oscuras y oscuro sobre botones claros; `PosterCard` añade `shadow(18.dp)` y escala 1.08. Es una traducción sensata del «elevación + sombra» de Apple al no existir el efecto del sistema en Compose para TV.
2. **Jerarquía y contraste de texto.**`TextPrimary` sobre `Background` da **19.64:1**, `TextSecondary` **6.02:1**, `TextMuted` **8.88:1**, `Accent` **5.39:1**, `AccentGreen` **9.72:1**. El equipo ya retiró `TextTertiary` del texto real (**2.15:1**) y lo dejó solo para adornos; decisión alineada con [Color](https://developer.apple.com/design/human-interface-guidelines/color.md).
3. **Tokens centralizados donde llegan a usarse.** `AppRadius` se usa 69 veces y `AppType` 118 veces; `Dimens.kt` y `AppType.kt` están excelentemente documentados con el «antes/después» de la limpieza.
4. **Navegación y restauración.** `selectTab` preserva estado, `focusRestorer()` devuelve el foco a la última tarjeta y cada pantalla pide foco inicial en la acción principal (Reproducir, Buscar, Calidad). Es exactamente el tipo de previsión que pide el modelo de foco direccional.
5. **Héroe y velos.** `HeroBanner` usa alto proporcional (`60 %` hasta 470 dp), tres gradientes y ancho máximo de texto de 640 dp. Es una interpretación convincente de *Watch Now* sin depender de assets de Apple.
6. **Plataforma correcta.** Leanback obligatorio, banner 320×180, `LEANBACK_LAUNCHER`, pantalla horizontal y sin launcher móvil. El diseño «Apple» se aplica **dentro** de una app Android TV, que es lo correcto.

---

## 5. Hallazgos priorizados

### Alta · corrige antes de seguir presumiendo «Apple TV»

| # | Hallazgo | Evidencia | Recomendación |
|---|---|---|---|
| A1 | **Etiqueta engañosa** | `README.md:5,27,38` dice «Diseño tvOS» / «Diseño Apple TV», pero `HomeScreen.kt:39`, `ProfileSelectScreen.kt`, `XUPER-TV-RESEARCH.md`, `OnlyOnSection.kt` y `StudioSection.kt` declaran Netflix, Xuper TV, flystream y sweetfox | Cambiar a «Inspirado en tvOS · adaptado a Android TV» y añadir una línea que reconozca el híbrido. |
| A2 | **Margen superior insuficiente** | `Components.kt:126`: `padding(top = 22.dp, bottom = 14.dp)` en la barra persistente; `HomeScreen`/`MoviesScreen` añaden solo `Spacer(12.dp)` | Subir a **32–48 dp** o aplicar `WindowInsets`/`safeDrawing` para overscan. Mantener 48 dp lateral. Es un riesgo real de recorte en televisores. |
| A3 | **Escala tipográfica no unificada** | 14 `TextStyle(...)` literales fuera del tema; `PlayerUi.kt:282` usa **14.sp** en `EpgChip`, `PlayerUi.kt:213` 16.sp, `OnlyOnSection.kt:244` 17.sp, `SettingsScreen.kt:558` 22.sp, `DetailScreen.kt:570` 28.sp | Mapear todo a `AppType`; eliminar el 14 sp; configurar `MaterialTheme.typography` en `Theme.kt` con la escala TV para que futuros componentes Material 3 no hereden tipografía de móvil. |
| A4 | **Foco sin estados completos** | `PosterCard`, `PillButton`, `TabPill`, `TvChip`, `ProviderLogoChip`, `StudioTile` usan solo reposo/foco/selección; escalas dispares 1.05 / 1.06 / 1.07 / 1.08; duraciones mezcladas (`tween(150)` vs `animate*AsState` por defecto) | Usar `AppStroke.focus` (hoy **0 usos**) en lugar de `3.dp` literal; unificar escala o documentar la jerarquía; añadir *pressed* y *disabled/unavailable* para acercarse a los cinco estados de [Focus and selection](https://developer.apple.com/design/human-interface-guidelines/focus-and-selection.md). |

### Media · mejora de fidelidad y accesibilidad

| # | Hallazgo | Evidencia | Recomendación |
|---|---|---|---|
| M1 | **Sin parallax ni iluminación** | Una sola `AsyncImage` por tarjeta; `tv_banner.xml` vector plano | Si se quiere «efecto Apple» sin capas reales, añadir un `graphicsLayer` con leve `rotationX/rotationY` + brillo al enfocar; documentar que es una aproximación, no tvOS parallax. No intentar `.lcr` en Android. |
| M2 | **Materiales falsos** | `GlassFill = Color(0x28787880)` es alpha plano; no hay blur/vibrancy | Donde el control va sobre arte (reproductor, ficha), usar un `Brush`+dimmer o blur real si el dispositivo lo soporta; si no, renombrar el token a `TranslucentFill` para no prometer «vidrio». |
| M3 | **Sin variantes de apariencia** | `Theme.kt` solo `darkColorScheme`; `colors.xml` y `Color.kt` fijos | Añadir al menos un conjunto *increase contrast* (bordes 1 dp → 2 dp, `TextSecondary` → `TextMuted`, `GlassFill` más opaco). Apple pide adaptarse a claro/oscuro/alto contraste; en Android TV basta con respetar alto contraste y escala de fuente. |
| M4 | **Ajustes demasiado denso** | `SettingsScreen.kt` 626 líneas, `Column` con `verticalScroll`, QR de 176 dp, 10 `SettingsCard`, 18 `StatusLine` y 13 `SettingRow` con `AppType.metadata` | Vista dividida: categorías a la izquierda, detalle a la derecha; o colapsar «Vinculación», «Addons», «Compatibilidad» en tarjetas expandibles. Apple mantiene la barra fija en *split view*. |
| M5 | **Texto como única vía de búsqueda** | `TvKeyboard.kt` 37 teclas; sin búsquedas recientes, sin dictado, sin autocompletado | Añadir «Búsquedas recientes» antes de escribir y, si el TV lo permite, `RecognizerIntent`/micrófono; mantener el teclado como respaldo. El QR ya hace bien esto para configuración. |
| M6 | **Rejilla apretada y proporciones dispares** | `MoviesScreen` 5 columnas, 20 dp / 24 dp; tarjetas ~157×200 dp. En filas `PosterCard` por defecto es 260×150 dp. `SearchScreen` alterna 150/210 dp | Subir vertical a 32–40 dp para que escala 1.08 + sombra 18 dp no invadan la fila siguiente; decidir un único aspecto (16:9 landscape o 2:3 póster) y derivarlo del token. |
| M7 | **Contraste del chip enfocado justo** | `TvChip` foco = `Accent` + `TextPrimary`: **3.65:1** (pasa 3:1 para texto grande, queda lejos del 6:1 del resto) | Usar `OnLight` sobre el azul enfocado o aclarar el acento; es el estado con peor contraste de la app. |
| M8 | **Barra de pestañas no se comporta como tvOS** | Siempre visible, opaca, sin sombra de selección, sin ocultado al scroll, sin retorno con *Menu* | No copiar *Menu* (Android usa Atrás), pero sí valorar sombra en la pestaña seleccionada y ocultar la barra al bajar en Home para ganar vertical. |

### Baja · limpieza del sistema de diseño

| # | Hallazgo | Evidencia |
|---|---|---|
| B1 | Tokens muertos: `AppStroke` **0 usos**, `FocusBorder` **0**, `Success` **0**; `AppSpacing` solo `screenMargin` (4 usos) mientras hay 40 `48.dp` literales. | `Dimens.kt`, `Color.kt`, grep de uso. |
| B2 | Deriva XML/Kotlin: `colors.xml` `surface_elevated=#FF1E1E26` vs `Color.kt` `SurfaceElevated=#FF1F1F26`; `background_soft` sin uso. | `res/values/colors.xml`, `ui/theme/Color.kt`. |
| B3 | `README.md` promete «tarjetas con escala 1.08» pero pestañas 1.06, píldoras 1.05 y estudios 1.07. | `Components.kt`, `OnlyOnSection.kt`, `StudioSection.kt`. |
| B4 | `Color.kt` documenta `TextTertiary` como 2,5:1; medido es **2,15:1**. La conclusión (no usar para texto) sigue siendo correcta. | Cálculo WCAG incluido abajo. |
| B5 | `Theme.kt` no define `typography` ni `shapes`; cualquier `MaterialTheme.typography.bodyMedium` futuro caería en escala móvil. | `ui/theme/Theme.kt`. |

---

## 6. Contraste medido (WCAG, sobre fondos de la app)

| Token | Background | Surface | SurfaceCard | SurfaceElevated |
|---|---:|---:|---:|---:|
| `TextPrimary` #FFFFFF | 19.64 | 18.02 | 18.18 | 16.38 |
| `TextSecondary` #8E8E93 | 6.02 | 5.53 | 5.58 | 5.02 |
| `TextMuted` #AEAEB2 | 8.88 | 8.15 | 8.22 | 7.41 |
| `TextTertiary` #48484A | 2.15 | 1.97 | 1.99 | 1.79 |
| `Accent` #0A84FF | 5.39 | 4.94 | 4.98 | 4.49 |
| `AccentGreen` #30D158 | 9.72 | 8.91 | 8.99 | 8.10 |
| `Danger` #FF453A | 5.77 | 5.29 | 5.34 | 4.81 |

Extras: `OnLight` sobre `SurfaceBright` **17.25:1** (botón primario, excelente); `TextPrimary` sobre `Accent` **3.65:1** (chip enfocado, ver M7); `Accent` sobre `SurfaceElevated` **4.49:1**, rozando el umbral de 4.5:1 para texto normal.

---

## 7. Lo que **no** conviene copiar de Apple TV

Estas diferencias son decisiones correctas y deben permanecer:

- **Top Shelf / icono por capas / parallax del sistema**: Android TV usa `android:banner` estático y no expone el motor de capas de tvOS. El `tv_banner.xml` vector 320×180 es lo correcto.
- **Siri / botón *Menu* / Siri Remote**: el input es D-pad + Atrás + teclas multimedia. `PlayerScreen.kt` ya gestiona `MediaPlayPause`, `MediaRewind`, `MediaFastForward` y `DirectionCenter`, que es el equivalente adecuado.
- **SF Pro y SF Symbols**: no se distribuyen en Android; `FontFamily.SansSerif` + vectores propios es la adaptación razonable y ya está documentada en `AppType.kt`.
- **60/80 pt literales**: son puntos tvOS sobre un lienzo de 1920×1080 pt. Aplicarlos sin convertir a `dp` rompería el layout Android TV. El margen de 48 dp es más conservador en horizontal.
- **Liquid Glass**: es un material de sistema de Apple; un alpha plano es más barato y predecible en TV Box con GPU modesta, siempre que no se llame «vidrio» en la documentación.
- **Vista dividida obligatoria**: es una recomendación, no un requisito; en `minSdk 22` y con Compose, un `Column` con scroll puede ser más robusto. Pero Ajustes merece una revisión por densidad (M4).

---

## 8. Plan recomendado

1. **Rápido (sin riesgo de compilación)**: reetiquetar README, corregir `TopTabBar` a 32–48 dp de padding superior, eliminar el 14 sp de `EpgChip`, usar `AppStroke.focus`, borrar/alinear tokens muertos y `colors.xml`.
2. **Medio**: unificar los 14 `TextStyle` literales en `AppType`, configurar `MaterialTheme.typography`, añadir búsquedas recientes, subir espaciado vertical de rejillas y oscurecer el texto del chip enfocado.
3. **Diseño**: rediseñar Ajustes en dos paneles o con tarjetas colapsables; decidir un único aspecto de póster; añadir una aproximación de iluminación/tilt al foco si se quiere más «Apple».
4. **Validación imposible aquí**: no hay TV/emulador en el entorno. Antes de cerrar cualquier cambio visual hay que probar overscan, escala de fuente grande, alto contraste, foco D-pad en todas las filas y solapamiento de tarjetas al enfocar en API 22 y en un TV actual.

---

## 9. Conclusión

Omni TV **sí tiene una identidad visual de salón cuidada y consistente**, y toma de tvOS lo más transferible: oscuro, acento único, pestañas, héroe editorial, foco evidente y jerarquía de 10 pies. Lo que le falta para ser «diseño Apple TV» en sentido estricto es el **parallax por capas**, la **métrica y comportamiento de la tab bar**, la **tipografía SF Pro/Dynamic Type**, los **materiales con blur** y la **vista dividida en Ajustes**; varios de ellos no deberían implementarse tal cual en Android TV.

La acción más importante no es gráfica sino de **honestidad documental**: llamarlo «inspirado en tvOS, adaptado a Android TV» y corregir los cuatro puntos altos (A1–A4). Con eso el diseño queda alineado con lo que realmente es, sin perder lo que ya funciona bien.

---

## 10. Actualización 2026-11-16 · estado real tras las pasadas

La auditoría se redactó contra una versión anterior. Contra el código actual, estas
correcciones ya están aplicadas (verificadas hoy):

| Antes (hallazgo) | Estado actual |
|---|---|
| A1 · Etiqueta «Diseño Apple TV» | **Corregido**: README y docs usan «Inspirado en tvOS · adaptado a Android TV» y reconocen el híbrido (perfiles Netflix, reproductor Xuper TV). |
| A2 · Margen superior de la barra | **Parcial→corregido**: `TopTabBar` pasó de 22 a **32 dp** (Components.kt:175). Sigue por debajo del 5 %/48 dp teórico; validar en TV con overscan real. |
| A3 · Escala tipográfica no unificada | **Corregido**: cero `TextStyle(...)` y cero `<n>.sp` literales fuera de `AppType.kt`/`Theme.kt`. `MaterialTheme.typography` y `shapes` configurados; mínimo de la escala = `chip` 15 sp. |
| A4 · Foco sin estados / tokens muertos | **Corregido**: `AppStroke.focus` usado (PlayerUi, Components); `FocusBorder`/`Success` eliminados; `AppSpacing` usado. Estados explícitos de foco ya consistentes (blanco sobre oscuro / anillo oscuro sobre blanco). **Pressed implementado** (2026-11-16, noche): los controles principales responden con contracción ×0.94 (`collectIsPressedAsState`) aunque `indication = null`. *Disabled* sigue sin variante visual propia: pendiente. |
| B1 · Tokens muertos | **Corregido** (AppStroke/AppSpacing en uso; `Success` eliminado; el color verde es `AccentGreen`). |
| B2 · Deriva `colors.xml` | **Corregido**: `colors.xml` solo declara `background`, `surface`, `ic_launcher_background`, coincidentes con `Color.kt`. |
| B4 · Documentación de `TextTertiary` | **Corregido**: KDoc ya dice 2,15:1 y «solo adornos»; los usos actuales son `background` (divisores), nunca texto. |
| B5 · `Theme.kt` sin typography/shapes | **Corregido**: `OmniTvTheme` define `Typography` y `Shapes` mapeando la escala TV. |
| M2 · «Materiales» sin blur | **Documentado**: `GlassFill`/`GlassFillStrong` llevan KDoc explícito de que son alpha planos, no vidrio real. Renombrado no aplicado por difusión (21 usos). |
| M4 · Ajustes denso (una columna) | **Corregido**: Ajustes es ya una **vista dividida** (categorías a la izquierda con foco restaurador + panel de detalle a la derecha, `SectionHeader`, tarjetas por categoría). |
| M7 · Chip enfocado 3,65:1 | **Corregido**: `TvChip` enfocado = `Color.White` + texto `Background` (~19:1). |
| M8 · Barra de pestañas | Sin cambios deliberados: siempre visible, con restauración de foco y etiqueta en la pestaña activa/enfocada. La ocultación al scroll y la métrica tvOS (68 pt) no se copian a propósito. |

**También corregido hoy**: README decía «8 avatares» pero `AVATAR_COUNT = 12`;
«Success #30D158» → `AccentGreen`; «no hay PIN» en perfil infantil (hoy hay PIN de
4 cifras con límite de fallos); `TopTabBar` 28 → 32 dp.

### Sigue pendiente (requiere decisión o dispositivo)

| # | Pendiente | Bloqueo |
|---|---|---|
| M1 | Parallax/iluminación al enfocar | Necesita validación en TV real (API 22 y TV actual); aproximación `graphicsLayer` posible pero no medible aquí |
| M3 | Variante de alto contraste / respeto a escala de fuente grande | Feature opt-in (p. ej. `increaseContrast`); decisión de producto |
| M5 | Búsquedas recientes en la búsqueda | Añade almacenamiento local; decisión de producto |
| M6 | Espaciado vertical de rejillas (32–40 dp) y aspecto único de póster | Cambio visual ciego (sin emulador); probar con overscan |
| — | Colisión de escala en foco (1.05/1.06/1.07/1.08) | **Unificada con nombre (2026-11-16, noche)**: token `FocusScale` en `Dimens.kt` (row 1.02 · chip 1.05 · tab 1.06 · card 1.07 · key 1.08 · control 1.10 · hero 1.12) y README alineado; la escala literal previa se eliminó por completo |
| — | `GlassFill`/`GlassFillStrong` siguen siendo alpha y no hay modo claro ni alto contraste | M2/M3, ver arriba |
| — | `SettingsScreen`/`PlayerScreen`/`Components.kt` siguen siendo archivos de >1400 líneas | Refactor de estructura, fuera del alcance de esta auditoría |

**Conclusión actualizada.** La app conserva una identidad de salón consistente
(oscuro, acento único, pestañas, héroe editorial, foco evidente) y la mayoría de
los hallazgos altos/medios de la auditoría ya están aplicados o documentados.
Lo que queda son **decisiones de producto y validación en hardware**, no bugs.

## 11. Actualización 2026-11-16 · segunda pasada (medición token a token)

Barrido completo de literales y tokens contra la escala vigente. Aplicado hoy:

| # | Hallazgo | Estado |
|---|---|---|
| C1 | `LiveAccent` (#E50914) como **texto** daba 4,10:1 sobre `Background` y 3,76:1 sobre `Surface` (por debajo de 4,5:1) | **Corregido**: nuevo `LiveAccentText` #FF6E64 (7,17:1 / 6,58:1) usado para el aviso del puente en `PlayerScreen`; `LiveAccent` queda para chips con relleno (texto blanco encima, 4,79:1) y acentos gráficos |
| C2 | Naranja de aviso `#FF9F0A`/`#28FF9F0A` literal en `StatusBadge` de Ajustes | **Corregido**: token nuevo `WarnOrange` (7,97–9,56:1) con relleno al 16 % vía `copy(alpha = 0.16f)`; éxito también normalizado a `AccentGreen.copy(alpha = 0.16f)` |
| C3 | Placa clara `#F3F5FA` duplicada en `StudioSection` y `StudioCatalogScreen` | **Corregido**: token `LogoPlate` compartido (logos de estudio oscuros necesitan placa clara) |
| C4 | `GlassDark`/`GlassBorder` vivían en `PlayerUi.kt`, fuera del sistema de tema | **Corregido**: movidos a `theme/Color.kt`; `PlayerUi.kt` solo referencia tokens (`ControlWhite` queda como alias local de `SurfaceBright`) |
| C5 | Radio literal `RoundedCornerShape(14.dp)` en `ProfileComponents` | **Corregido**: `AppRadius.md` |
| C6 | Estilo `monospaceCode` muerto en `AppType` (0 usos) | **Eliminado** |
| C7 | Oro de medalla #1/#2/#3: bronce `#E08E45` (7,0–7,6:1) hardcodeado en el Top 10 | **Documentado** como paleta de medallas con comentario; no promovido a token por ser de un único uso |

**Contrastes medidos (WCAG, sobre fondos de la app)**: `TextPrimary` 19,6 · `TextMuted` 8,9 ·
`TextSecondary` 6,0/5,5 · `Accent` 5,4 · `AccentGreen` 9,7 · `RatingGold` 13,9 ·
`OnLight`/`SurfaceBright` 17,3 · `LiveAccentText` 7,2/6,6 · `WarnOrange` 8,0–9,6 ·
`Danger` 5,8 · `TextTertiary` 2,15 (adornos, nunca texto).

**Verificado**: `AVATAR_COUNT = 12` y los 12 drawables `avatar_face_1..12.xml` existen;
el README ya dice 12. Reportes del puente (`check_android_tv.py` OK, `node --test` 7/7)
intactos tras los cambios. Sin compilación (no hay JDK en el entorno).

## 12. Actualización 2026-11-16 · referencias externas (SkyStream y VortX) y movimiento reducido

Se clonaron y revisaron dos clientes del mismo nicho: **SkyStream** (Flutter, akashdh11/skystream) y **VortX** (Kotlin/Android, VortXTV/VortX, ~1.200 líneas en su `StreamRanking`). Lo aplicable al diseño de Omni TV:

| Ref | Idea | Aplicación en Omni TV |
|---|---|---|
| VortX · `ui/theme/Motion.kt` | Honorar `Settings.Global.ANIMATOR_DURATION_SCALE == 0` (el «Reduce Motion» de Android) colapsando transiciones a `snap()`; curva única cubic-bezier(.2,.8,.2,1) | **Aplicado**: nuevo `ui/theme/Motion.kt` (`AppMotion.easing`, `rememberReducedMotion()`, `appTween()`). Sustituye a todos los `tween(...)` de foco (~25 sitios en 9 archivos) y al `Crossfade` del héroe (550 ms); las elevaciones de póster (18 dp) también. Los giros infinitos (`LoadingSpinner`) se dejan: son progreso, no transición |
| SkyStream · `app_theme.dart` | «Una regla de foco que todo control sigue»; Material no muestra foco visible en TV | **No aplicable tal cual**: Omni TV no usa botones Material; todos sus componentes interactivos ya pintan foco propio (anillo/relleno/escala). Verificado por inspección; riesgo residual solo en controles nuevos |
| VortX · `Elevation.kt` | Elevación con sombra con tinte, nunca un borde de 1 dp fingiendo elevación | **Ya cumplido**: bordes solo para foco; la elevación va por `graphicsLayer` + sombra |
| VortX · paleta con tinte de acento / OLED true-black | Superficies teñidas hacia el acento y lienzo negro puro opcional | **No implementado** (decisión de producto y rediseño de paleta; ver M3). Candidato si se quiere un modo «OLED negro» |
| VortX · serif de exhibición (Lora) para héroe/títulos | Identidad editorial «cine» | **No implementado**: cambia la identidad tipográfica (hoy sans de sistema estilo SF) y obliga a empaquetar la fuente; a decidir por producto |
| VortX · escala de póster 1.03 + glow vs nuestra jerarquía de escala | Ajuste fino de sensación | **Resuelto (noche)**: jerarquía con nombre `FocusScale` (row 1.02 → hero 1.12) en `Dimens.kt`; sustituye a los literales 1.05–1.15 dispersos |

**Resultado**: la app gana un modo de movimiento reducido real en mando/TV, coste ≈ 1 archivo nuevo + conversión mecánica; sin cambios visuales para quien no tenga las animaciones desactivadas.

## 13. Actualización 2026-11-16 · Ajustes: diseño y comportamiento real

Pasada sobre la pantalla de Ajustes (vista dividida, ~1.500 líneas):

| # | Hallazgo | Estado |
|---|---|---|
| D1 | Las filas con `TvToggle` no eran pulsables en la fila completa: el único objetivo era el interruptor de 72×40 dp (patrón pobre a 10 pies) | **Corregido**: la fila completa conmuta el estado (Reproducción ×4, AniSkip ×5+fuentes, Red/DoH, scrapers Nuvio). El interruptor sigue enfocable de forma independiente |
| D2 | `LaunchedEffect(SettingsStore.revision)` → **cualquier** ajuste (hasta un toggle de autoplay) forzaba `AddonRegistry.refresh(force = true)` y `NuvioPluginManager.refreshAll(force = true)`: tormenta de red en Ajustes | **Corregido**: firma derivada de solo los valores de integraciones (`customAddons`, `streamAddons`, `nuvioPlugins`, `disabledNuvioScrapers`, puentes Cloudstream/Nuvio/FFmpeg); la recarga solo ocurre si eso cambia o al pulsar «Comprobar todo» |
| D3 | «Limpiar caché de catálogos» no vaciaba la caché de resultados Nuvio (añadida en §11) — mentía por omisión | **Corregido**: también llama a `NuvioScraperResolver.clearResultCache()`; texto de la fila actualizado |
| D4 | El QR se generaba con 262.144 llamadas `setPixel` en hilo principal por cada `remember` | **Corregido**: un único `IntArray` + `setPixels` |
| — | QR, tarjetas, chips, foco y badges: sin cambios estructurales; la pantalla ya era Two-Pane | Documentado |

| D5 | La misma tormenta en pantallas: `LaunchedEffect(SettingsStore.revision)` + `needsReload()` hacía que cualquier ajuste (autoplay, calidad…) recargara **toda la portada** en Inicio/Películas/Anime/Búsqueda | **Corregido**: `RemoteCatalog.needsReload()` compara una huella (`SettingsStore.catalogStamp()` = addons instalados); `AnimeCatalog.needsReload()` usa su TTL de 15 min (sus fuentes fijas no dependen de ajustes) |

**Pendiente de dispositivo** (no se puede validar en este entorno): foco con mando real en filas con doble objetivo (fila + toggle), overscan y tamaño mínimo de objetivo.

## 14. Actualización 2026-11-16 · tercera pasada (noche): accesibilidad AA y tokens de foco

Barrido de diseño completo con medición WCAG 2.x calculada (no leída). Aplicado hoy:

| # | Hallazgo | Estado |
|---|---|---|
| E1 | Escala de foco era literales dispersos (1.02–1.15) y la doc prometía una taxonomía que el código no tenía | **Corregido**: `FocusScale` en `Dimens.kt` (row 1.02 · chip 1.05 · tab 1.06 · card 1.07 · key 1.08 · control 1.10 · hero 1.12 + `pressFactor` 0.94); todos los literales eliminados |
| E2 | **Pressed state inexistente** (indication = null en todo; ningún control respondía a OK/Enter) | **Corregido**: contracción ×0.94 vía `collectIsPressedAsState()` en PillButton, TvChip, TabPill, PosterCard, ContinueCard, CircleIconButton, TvToggle, teclas del teclado (9 componentes) |
| E3 | Badges con texto blanco sobre `#0A84FF` = **3,65:1** (bajo AA para 15 sp): «HOY», «KIDS» | **Corregido**: nuevo token `BadgeAccent` (#0070E0) → 4,78:1; badges de perfil, KidsChip, Badge HOY y AccentChip de detalle migrados |
| E4 | PillButton destructivo sin foco: `Danger #FF453A` sobre GlassFillStrong = **4,08:1**; y enfocado: blanco sobre Danger = 3,41:1 | **Corregido**: texto sin foco → `LiveAccentText` (5,6:1 sobre fill); enfocado → `OnLight` sobre Danger (5,65:1, simétrico al botón primario) |
| E5 | `TextSecondary` sobre `GlassFillStrong` = **4,26:1** | **Corregido**: alfa de `GlassFillStrong` 0,30 → 0,24 (#25252A) → 4,68:1; TextMuted 6,9:1 |
| E6 | Teclado embebido: foco con anillo de **2 dp** (el único que no respetaba los 3 dp) | **Corregido**: `AppStroke.focus` (3 dp) en teclas y acciones |
| E7 | `medalBronze` inline en el bucle de recom. de Top 10 | **Corregido**: token `MedalBronze` en theme/Color.kt |
| E8 | `TvChip` por defecto en `AppType.body` (21 sp) — confusión con `AppType.chip` (15 sp) | **Documentado**: los chips de filtro usan 21 sp a propósito (lectura a distancia); KDoc + README lo explican |
| E9 | Espaciado 4–48 dp era promesa de escala pero no había tokens | **Documentado** como escala informal con literales; `AppSpacing.screenMargin = 48.dp` es el único token obligatorio; extraerla a módulos sin validar en pantalla sería mover 2–3 cientos de llamadas a ciegas |
| E10 | README decía «TextMuted 6:1» (real 8,9:1; la de 6:1 es TextSecondary) | **Corregido** |

**Resultado**: la app pasa a cumplir WCAG AA en todos los emparejamientos texto/superficie medidos (peor caso ≈4,6:1), el foco pasa a estar tokenizado (fin de los literales), y el mando tiene feedback de pulsación en los controles principales.

**Sigue pendiente** (honestidad): *disabled* sigue sin variante visual propia (ningún control se deshabilita condicionalmente hoy — fabricarlo a ciegas sería inventar estados); modo alto contraste (M3); blur real en `GlassFill*`; legibilidad real de los nuevos rellenos/escalas en TV física.
