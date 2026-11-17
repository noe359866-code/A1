# Revisión de diseño para Android TV — Omni TV

**Fecha:** 17 de noviembre de 2026 · **Rama:** `arena/30f67343-a1`
**Método:** comparación del código real (`app/src/main`) contra (a) guías oficiales de
Android TV, (b) el contrato de calidad de Google Play para TV, (c) las HIG de tvOS y
(d) un repositorio de referencia de TV en Compose clonado y leído (`Davidona/StreamVault-IPTV`).
Se midió el contraste de la paleta con cálculo WCAG y se convirtieron los `sp` a `px`
sobre el canvas real de TV.

---

## 0. Resumen ejecutivo

El diseño está **por encima de la media** en lo que más se rompe en una app de TV:
tipografía escalada de 10 pies, foco siempre visible con anillo + escala + sombra,
navegación D-pad con restauración de foco, y una paleta cuyo contraste **verifiqué y
cumple** (6,0:1 el texto secundario, 4,78:1 los badges con blanco).

Hay **tres hallazgos que sí rompen el diseño en un televisor real**, y los tres son
aritméticos, no estéticos:

1. ~~La tipografía se queda corta~~ — **retirado: era un error mío** (ver §8). Con
   la densidad real de una TV (2.0), el hero da 108 px y el `body` 42 px. La escala
   **cumple** el contrato de Play y la referencia de 10 pies.
2. **Las rejillas se recomponen al enfocar la barra.** La sidebar expande a 224 dp
   dentro del área de contenido: el póster de Películas cae de **142 dp a 112 dp**
   (−21 %) y la disposición se recalcula mientras el usuario navega.
3. **El margen superior está a 10–12 dp del borde.** El estándar Android TV es
   27 dp (48 dp lateral). Todas las pantallas de pestañas empiezan con 10–12 dp.

Y uno de "incumplimiento documental" que **no** existe: el contrato de Play para TV
se cumple (TV-TC y TV-TA holgados). La tipografía no es el problema.

---

## 1. Fuentes y estándares (investigación)

| Fuente | Dato clave usado |
|---|---|
| [Layouts · Android TV](https://developer.android.com/design/ui/tv/guides/styles/layouts) | Canvas **960×540 dp**; overscan **48 dp lateral / 27 dp superior-inferior**; rejilla de 12 columnas de 52 dp con 20 dp de calle |
| [Adaptive apps for TV](https://developer.android.com/develop/adaptive-apps/guides/tv/build-adaptive-apps-for-tv) | 10-foot UI, overscan ~5 %, sin multitarea en segundo plano |
| [Focus system · Android TV](https://developer.android.com/design/ui/tv/guides/styles/focus-system) | Indicadores de foco: scale (**1.025 / 1.05 / 1.1**), glow (**2–32 dp**), outline (ancho + inset + color), color y elevación tonal; matriz **default / focused / pressed + enabled / disabled / selected** |
| [Typography · Android TV](https://developer.android.com/design/ui/tv/guides/styles/typography) | Tipo por defecto Roboto; 15 estilos; jerarquía display/headline/title/body/label; evitar fuentes decorativas |
| [TV App Quality · Google Play](http://docs.52im.net/extend/docs/api/android-50/distribute/essentials/quality/tv.html) | **TV-TC**: texto de núcleo ≥16 sp · **TV-TA**: todo el texto ≥12 sp · **TV-OV**: nada cortado · **TV-DP**: navegable con D-pad de 5 vías · **TV-DM**: no depender del botón Menu |
| [Apple HIG · Layout](https://developer.apple.com/design/human-interface-guidelines/layout) | Zona segura tvOS: **60 pt** arriba/abajo, **80 pt** a los lados; rejillas óptimas de 2, 3 y 4 columnas |
| [Apple HIG · Tab bars](https://developers.apple.com/design/human-interface-guidelines/tvos/interface-elements/tab-bars/) | Barra de 68 pt de alto, borde superior a 46 pt, translúcida, solo la seleccionada opaca |
| `Davidona/StreamVault-IPTV` (clonado) | Tokens de espaciado con **safeTop/safeBottom = 32 dp, safeHoriz = 56 dp**; `railWidth = 124 dp`; **skeleton con shimmer** en vez de spinner; tests de UI/golden |

**Nota sobre tvOS:** la HIG fija `68 pt` de alto de tab bar y `46 pt` desde el borde.
Una app de Android TV **no debe copiarlos** (el propio `APPLE_TV_DESIGN_REVIEW.md` del
repo lo advierte). Se citan solo como referencia del híbrido declarado.

---

## 2. Lo que está bien (verificado, no asumido)

- **Contraste medido con WCAG 2.1** — la tabla del README es exacta:

| Token | Ratio | Veredicto |
|---|---|---|
| TextPrimary `#FFFFFF` / Background | **19,64:1** | ✅ AAA |
| TextSecondary `#8E8E93` / Background | **6,02:1** | ✅ AA+ |
| TextSecondary / Surface `#16161C` | 5,53:1 | ✅ AA |
| TextMuted `#AEAEB2` / Background | **8,88:1** | ✅ AAA |
| Accent `#0A84FF` / Background | 5,39:1 | ✅ AA |
| BadgeAccent `#0070E0` + blanco | **4,78:1** | ✅ AA (y acierta al no usar el acento) |
| LiveAccentText `#FF6E64` / Background | 7,17:1 | ✅ AAA |
| TextTertiary `#48484A` | 2,15:1 | ⚠️ solo adornos — bien documentado |

- **El foco nunca es solo color**: anillo `AppStroke.focus` (3 dp) + escala + sombra
  de 18 dp al enfocar. Cumple FOCUS-01 y el "nunca solo color" de tvOS.
- **`FocusScale` está tokenizado** (32 usos) en vez de literales sueltos.
- **Movimiento reducido** implementado de verdad vía `ANIMATOR_DURATION_SCALE == 0`
  (`Motion.kt`), algo que la mayoría de apps de TV no hace.
- **Ajustes ya es de dos paneles** (columna de categorías 330 dp + panel derecho),
  que es lo que la HIG de tvOS pide y lo que el propio `APPLE_TV_DESIGN_REVIEW.md`
  listaba como carencia.
- **Un solo `ImageLoader` de Coil** con Cronet, caché y crossfade compartidos.

---

## 3. Hallazgos

### ❌ D1 · RETIRADO — "la tipografía se queda corta" era un error de cálculo

Este hallazgo estaba mal por una razón simple: **convertí `sp` a `px` con densidad
1.0**. En una TV de 1080p la densidad es **2.0** (1920 px sobre un canvas de 960 dp),
así que 1 sp = 2 px, no 1 px.

| Estilo | Actual | px reales (×2) | Contrato Play | Referencia 10 pies | Veredicto |
|---|---|---|---|---|---|
| `hero` | 54 sp | **108** | — | ~92 | ✅ por encima |
| `screenTitle` | 40 sp | **80** | — | — | ✅ |
| `sectionTitle` | 27 sp | 54 | — | — | ✅ |
| `body` | 21 sp | **42** | ≥32 (TV-TC) | ~57 de cuerpo | ✅ cumple |
| `metadata` | 18 sp | **36** | ≥32 | ~28 en px | ✅ cumple |
| `tab` | 16 sp | 32 | =16 sp exacto | — | ✅ cumple |
| `chip` | 15 sp | 30 | ≥24 (TV-TA) | — | ✅ etiqueta |

La única precisión que sobrevive (y es menor, no un defecto): `tab` a **16 sp** es
exactamente el mínimo de TV-TC y `chip` a 15 sp queda por debajo del umbral de *núcleo*
pero por encima del de *todo el texto*. Ambos son etiquetas cortas, así que la escala
**está bien calibrada y no hay que reescalarla**. Ver §8 para el detalle.

### 🔴 D2 · La barra lateral no participa en el cálculo de las rejillas

`TopTabBar` anima su ancho **76 → 224 dp** (`Components.kt:154`) y el contenido es un
`Box(Modifier.weight(1f))`. Al enfocar la barra, el contenido pierde 148 dp y todo se
recalcula. Con el canvas de TV (960 dp de ancho):

| Pantalla | Columnas | Espacio/ítem — barra compacta | — barra expandida |
|---|---|---|---|
| Películas / Plataformas / Estudios | 5 fijas | 90 dp | **56 dp (−38 %)** |
| Buscar | 3–4 según `screenWidthDp` | — | — |

El foco de `PosterCard` es **1,07 + sombra 18 dp**, y el póster mide 56 dp de ancho:
el escalado no cabe en la celda y se solapa con la vecina. Es exactamente el fallo que
la guía de layouts advierte: *"considera el padding entre ítems y el aumento del
estado enfocado para evitar solapamientos"*.

Además, `SearchScreen` decide columnas con
`if (screenWidthDp >= 1120) 4 else 3` — a 960 dp **nunca** llega a 4, y `screenWidthDp`
no descuenta la barra. Es el único sitio con esa lógica (`GridCells.Fixed(5)`
hardcodeado en los otros cuatro).

**Recomendación:** (a) ampliar la barra **por encima** del contenido con
`.zIndex(1f)` en vez de empujarlo, o (b) fijar columnas con `GridCells.Adaptive(minSize)`
y derivar el ancho disponible de `BoxWithConstraints` / `LocalConfiguration` menos el
ancho real de la barra. La opción (a) es la de tvOS y elimina el reflow; la (b) es más
robusta a 720p.

### 🔴 D3 · Margen superior por debajo del overscan (27 dp)

Todas las pantallas de pestañas arrancan con **10–12 dp** de separación superior:

| Pantalla | Inicio |
|---|---|
| `MoviesScreen` | `Spacer(12.dp)` |
| `SearchScreen` | `Spacer(10.dp)` |
| `AnimeScreen` | `Spacer(12.dp)` |
| `PlataformasScreen` | `Spacer(12.dp)` |
| `SettingsScreen` | `vertical = 24.dp` (el mejor) |
| `ProfileSelectScreen` | `vertical = 32.dp` ✅ |

El lateral sí es ejemplar (`AppSpacing.screenMargin = 48 dp`, 65 usos). El vertical no
tiene token y se aplica con literales distintos en cada pantalla. Con overscan real,
un título a 10 dp puede recortarse (falla **TV-OV**).

**Recomendación:** añadir `AppSpacing.safeTop = 27.dp` / `safeBottom = 27.dp` a
`Dimens.kt` y sustituir los literales. Coincide con lo que hace la referencia
(`safeTop = 32.dp`).

### 🟠 D4 · La escala de foco tiene siete valores donde el estándar tiene tres

La guía de foco de Android TV define **1.025, 1.05 y 1.1**. `FocusScale` usa
1.02, 1.05, 1.06, 1.07, 1.08, 1.10 y 1.12. Los intermedios (1.06/1.07/1.08) existen
para granularidad fina, pero **el ojo no distingue 1.06 de 1.07**: son siete reglas que
hay que recordar y que no producen diferencia perceptual.

**Recomendación:** consolidar a tres niveles (`subtle 1.025 · base 1.05 · large 1.1`)
conservando los nombres actuales como alias, para no tocar 32 llamadas.

### 🟠 D5 · Falta el estado **disabled** en los componentes

La matriz oficial tiene seis casillas por componente: default / focused / pressed ×
enabled / disabled. `PillButton`, `TvChip`, `PosterCard`, `TvToggle` y las teclas
**no exponen `enabled`** (0 coincidencias). Hoy se evita el problema no dibujando
acciones inactivas, pero cuando una acción lo esté (p. ej. «Reintentar» mientras carga),
no habrá forma visual de comunicarlo.

### 🟠 D6 · Carga = spinner centrado, no esqueleto

17 usos de `LoadingSpinner` como estado principal de pantalla (Home, Películas, Anime,
Buscar, Plataformas, Estudios, Reproductor). La referencia de la industria y la guía de
10 pies piden **skeleton con la forma del contenido**: evita el salto de layout y hace
la espera perceptualmente más corta. `Components.kt` no tiene ningún `shimmer`.

**Recomendación:** un `PosterSkeleton` reutilizando las dimensiones de `PosterCard`
con el `shimmerEffect` que usa StreamVault (modifier de ~40 líneas, sin dependencias).

### 🟡 D7 · El chip de 15 sp se usa para información, no solo para adornos

24 usos de `AppType.chip`. En badges («KIDS», «HD», «4K») es correcto y está bien
razonado en el KDoc. Pero también se usa para el **rating** (`★ 8.1`) y para **tiempos
del reproductor** (`PlayerUi.kt:299,347`), que son datos, no adornos. Se arregla solo
con D1.

### 🟡 D8 · La barra lateral expande también al navegar horizontalmente

`SidebarItem` llama a `onSelect` en **KeyDown** (`clickable`). Con `KeyUp` se evita el
doble disparo, pero el patrón actual (`dpadRepeatThrottle` + `isFocused` del grupo) hace
que el ancho salte al enfocar. La barra de tvOS aparece y desaparece suavemente y **no
cambia el layout del contenido** (D2). No es un bug; es una decisión que conviene
documentar como deliberada.

### 🟡 D9 · Documentación de diseño desactualizada (ya reportado en `REVISION_GENERAL.md`)

`docs/DPAD_FOCUS.md` describe `TopTabBar` + `TabPill` y un "preview con retardo de
150 ms" que no existen. Sigue pendiente de la ronda anterior.

---

## 4. Comparación con la referencia (StreamVault-IPTV)

| Aspecto | Omni TV | StreamVault | Comentario |
|---|---|---|---|
| Canvas / márgenes | 48 dp lateral, 10–12 dp superior | `safeHoriz 56`, `safeTop/Bottom 32` | **Omni pierde en vertical** |
| Tokens de espaciado | solo `screenMargin` (el resto literales) | scale completa `xs…xxl` + gutters | Omni lo declara deliberado en `Dimens.kt` |
| Barra lateral | 76 ↔ 224 dp, empuja contenido | `railWidth = 124 dp` fijo | fijo evita el reflow de D2 |
| Carga | 17 spinners | skeleton + shimmer | **StreamVault gana** |
| Estados de foco | anillo + escala + sombra | idem | empate |
| `disabled` | ausente | presente | StreamVault gana |
| Tests de UI | ninguno | `ShellGoldenTest`, `CoreAppScreenScaffoldTest` | StreamVault gana |

Lo importante: **Omni TV no va por detrás en dirección de arte** (la paleta medida es
mejor que la de la media), va por detrás en **aritmética de TV**: márgenes verticales,
columnas dentro del ancho real y escala tipográfica.

---

## 5. Plan propuesto

| Fase | Trabajo | Archivos | Riesgo |
|---|---|---|---|
| **1 · Correcciones de contrato** | D1 (rampa ×1,3), D3 (`safeTop/Bottom`), D2 (barra con `zIndex` o `Adaptive`) | 1 + 1 + 2 | Medio: hay que revisar truncados tras D1 |
| **2 · Sistema** | D4 (tres niveles de foco), D5 (`enabled` en 5 componentes), D6 (`PosterSkeleton`) | 3 | Bajo |
| **3 · Documentación** | D9 + reescribir `DPAD_FOCUS.md` y ajustar las métricas de `APPLE_TV_DESIGN_REVIEW.md` | 3 docs | Nulo |
| **4 · En televisor** | Verificar D1/D2/D3 con overscan real y probar 720p | — | Requiere dispositivo |

**Sugerencia de orden:** empezar por D3 (10 minutos, sin riesgo) y D1 (un archivo, pero
hay que mirar los `maxLines`). D2 conviene decidirlo antes de tocar el sidebar, porque
afecta a la decisión de si la barra empuja o flota.

---

## 6. Qué **no** recomiendo

- **Copiar la tab bar de tvOS** (68 pt de alto, 46 pt del borde, se oculta al scroll).
  Es la decisión correcta la que ya está tomada: en Android TV el *Atrás* es la salida y
  ocultar la navegación rompe TV-DM.
- **Adoptar `androidx.tv:tv-material` ahora.** El repo lo evita con razón (Material 2 de
  TV no expone `Text`/`MaterialTheme` en 1.0.0). Los componentes dibujados a mano son
  correctos y no hay que reescribirlos para ganar nada visual.
- **Tokenizar los 4–48 dp literales.** `Dimens.kt` ya documenta por qué no: sin poder
  validar en pantalla sería reorganizar cientos de llamadas a ciegas. Con D1 y D3 el
  problema real (vertical) queda cubierto.

---

## 7. Correcciones aplicadas en esta ronda

### D3 · Margen vertical de overscan (✅ aplicado)

Añadidos `AppSpacing.safeTop` y `AppSpacing.safeBottom` = **27 dp** a `Dimens.kt`,
con la cita de la guía en el KDoc, y sustituidos los literales superiores de las
cuatro pantallas de pestañas que arrancaban a 10–12 dp:

`MoviesScreen` (12→27) · `SearchScreen` (10→27) · `AnimeScreen` (12→27) ·
`PlataformasScreen` (12→27).

`SettingsScreen` (24 dp) y `ProfileSelectScreen` (32 dp) ya estaban dentro del área
segura y **no se tocan**. Riesgo nulo: 17 dp más de aire arriba, sin cambios de lógica.

### Pendientes de decisión

- **D1 (tipografía ×1,3)** — un archivo, pero cambia el tamaño de todo el texto;
  conviene revisarlo en pantalla antes de fijarlo. No lo aplico sin verlo en una TV.
- **D2 (barra lateral vs rejillas)** — hay que elegir entre *barra flotante con
  `zIndex`* (sin reflow, estilo tvOS) o *columnas adaptativas*. Afecta al sidebar, así
  que mejor decidirlo junto con el resto de la navegación.
- **D4, D5, D6** — mejoras de sistema; son la fase 2 del plan.

---

## 8. Aplicado en esta ronda

| # | Hallazgo | Estado | Qué se hizo |
|---|---|---|---|
| D1 | Tipografía corta | ❌ **retirado** | Error propio: usé densidad 1.0 en vez de 2.0. La escala cumple Play y 10 pies; no se toca |
| D2 | Barra lateral vs rejillas | ✅ aplicado | Barra **flotante** (`Box` + `zIndex`), contenido a ancho completo, `navRail = 84dp` reservado; Buscar pasa a `GridCells.Adaptive(150dp)` |
| D3 | Margen vertical | ✅ aplicado | `safeTop`/`safeBottom = 27dp` en Inicio, Películas, Anime, Plataformas y Buscar |
| D4 | Siete escalas de foco | ✅ aplicado | Consolidado a 1.025 / 1.05 / 1.10 con alias para no tocar 32 llamadas |
| D5 | Falta `disabled` | ✅ aplicado (PillButton) | Nuevo `enabled: Boolean = true`: fondo y texto atenuados, sin anillo, sin escala, clic desactivado |
| D6 | Spinner en vez de esqueleto | ✅ aplicado | `SkeletonBlock` + `PosterGridSkeleton` con brillo; optimizado para movimiento reducido. Usado en Películas |
| D9 | Docs desactualizadas | ✅ aplicado | `docs/DPAD_FOCUS.md` y este informe; README (barra, foco, geometría) |

### Detalle del cambio de barra (D2)

```
Antes:  Row { TopTabBar(width 76→224) ; Box(weight 1f) { content } }
        → el contenido pierde 148 dp al enfocar la barra; rejilla recalculada

Ahora:  Box { Box(fillMaxSize) { content } ; TopTabBar(align=CenterStart, zIndex=2) }
        → el contenido no cambia de tamaño; la barra se superpone al expandirse
```

El contenido reserva `AppSpacing.navRail = 84 dp` al inicio (8 dp de aire + 76 dp de
barra compacta). Cuando la barra crece a 224 dp se superpone al contenido: es el
comportamiento de tvOS y evita el reflow.

### Honestidad sobre la verificación

- **No compilado**: el entorno no tiene JDK ni Android SDK. Sí verifiqué: balance de
  llaves/paréntesis, imports presentes y sin duplicados, firmas de los helpers usados
  (`ProvideHorizontalTvBringIntoView(leadingPadding)`), y que no quedan referencias a
  `LoadingSpinner` en Películas ni a `onFocus` en `SidebarItem`.
- **No ejecutado en dispositivo**: la barra flotante cambia el reparto de espacio, y
  eso solo se valida con overscan real. El riesgo principal es que el contenido bajo la
  barra expandida quede tapado mientras dura la expansión (esperado y reversible).
- El CI (GitHub Actions) compila `assembleDebug` + `assembleRelease` (R8) y corre
  lint en cada push, que es lo que confirmará que no hay error de compilación.
