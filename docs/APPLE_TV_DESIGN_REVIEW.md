# Revisión de diseño · inspiración Apple TV / tvOS

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
| Foco | Parallax, elevación, iluminación, animación, hasta 5 estados | Escala 1.05–1.08, anillo de 3 dp blanco/oscuro, sombra animada 18 dp en `PosterCard`, `hasFocus` en `SettingRow` | **Bien resuelto para Android TV, distinto de tvOS**. Falta parallax, iluminación, estado *pressed/highlighted* explícito y estado *unavailable*. |
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
