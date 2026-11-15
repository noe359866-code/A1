# 📦 Compilar el APK de Omni TV

Todo lo necesario para pasar del código a un `OmniTV-*.apk` instalable en la TV,
**sin tener que configurar nada a mano**. Hay dos caminos: el botón de GitHub
(recomendado) o la compilación local.

---

## 1. Estado del proyecto (revisión de noviembre 2026)

Revisión completa del repositorio antes de dejarlo listo para generar el APK:

| Comprobación | Resultado |
|---|---|
| Contrato Android TV (`tools/check_android_tv.py`) | ✅ Leanback obligatorio, táctil opcional, banner 320×180, sin launcher de móvil |
| Catálogo de versiones (`gradle/libs.versions.toml`) | ✅ Coherente: 21 dependencias y 3 plugins, todos declarados |
| Recursos (`R.string/R.drawable/...` usados en el código) | ✅ 40 referencias, 0 huérfanas |
| Puentes Node (`nuvio`, `cloudstream`, `ffmpeg`) | ✅ 7 pruebas en verde |
| Tests unitarios JVM | ✅ 32 clases de prueba (`app/src/test/…`) |
| Workflows YAML (`ci.yml`, `release.yml`, `apk.yml`) | ✅ Válidos |
| **Compilación Kotlin** | ⚠️ **estaba rota en `main` y ya está corregida** (ver abajo) |

### Lo que estaba mal y se ha corregido

1. **`main` no compilaba.** Tres archivos usaban símbolos sin importar, así que
   `:app:compileDebugKotlin` fallaba y **no se podía generar ningún APK**:
   - `ui/components/StudioSection.kt` → faltaba `com.omnitv.app.ui.theme.AppStroke`
   - `ui/screens/PlataformasScreen.kt` → faltaba `AppStroke`
   - `ui/screens/SettingsScreen.kt` → faltaba `androidx.compose.ui.text.TextStyle`
2. **No había un botón claro para generar el APK.** Existían `ci.yml` (que
   compilaba pero no entregaba nada) y `release.yml` (que exigía etiqueta y
   firmaba con debug en silencio). Ahora existe `.github/workflows/apk.yml`.
3. **No había comprobación previa de preparación.** Un alias inventado en
   `libs.versions.toml` o un wrapper ausente sólo se descubría tras varios
   minutos de Gradle. Ahora `tools/check_build_readiness.py` lo detecta en
   menos de un segundo, en local y en CI.
4. **La firma no estaba documentada como paso previo.** Un APK firmado con la
   clave de depuración no puede actualizar una instalación firmada con
   producción (y al revés). Ahora hay un script y una sección dedicada (§4).

> La compilación se validó en CI (GitHub Actions), sobre un árbol idéntico al de
> `main` **más estas tres correcciones de import**; ese árbol ya pasó
> `testDebugUnitTest` + `assembleDebug` + `assembleRelease` + `lintDebug` en
> verde. En el entorno donde se hizo esta revisión no hay JDK ni Android SDK,
> por eso el APK final se genera con los pasos de §2 o §3.

---

## 2. Opción A — El botón de GitHub (recomendado, 2 clics)

No hace falta instalar nada: GitHub compila y te da el APK descargable.

1. Abre el repositorio → pestaña **Actions**.
2. Elige **«Generar APK (Android TV)»** en la lista de la izquierda.
3. Pulsa **Run workflow**:
   - `variant`: `release` (por defecto) o `debug` (más rápido, sin R8).
   - `publicar`: déjalo desmarcado para obtener sólo el artefacto, márcalo si
     quieres además una *Release* descargable.
   - `version`: opcional, por ejemplo `1.3.0` (vacío = la del proyecto).
4. Espera ~8–12 minutos. Al terminar:
   - El APK está al final de la página del run, en **Artifacts → `OmniTV-apk`**
     (se descarga como `.zip`).
   - El resumen del run muestra versión, tamaño, **SHA-256 del archivo** y
     **SHA-256 del certificado de firma**.
5. Descomprime y pasa el `.apk` a la TV (§5).

El workflow también se dispara al subir una etiqueta (`git tag v1.3.0 && git
push origin v1.3.0`); en ese caso publica la Release automáticamente.

---

## 3. Opción B — Compilar en local

### Requisitos (una sola vez)

| Herramienta | Versión | Cómo |
|---|---|---|
| JDK | **17** (no 21+) | [Temurin 17](https://adoptium.net/temurin/releases/?version=17) o `sudo apt install openjdk-17-jdk` |
| Android SDK | plataforma **35** + build-tools | Android Studio → *SDK Manager*, o `sdkmanager "platforms;android-35" "build-tools;35.0.0"` |
| `ANDROID_HOME` | apuntando al SDK | o crea `local.properties` con `sdk.dir=/ruta/al/sdk` |

Gradle **no** hay que instalarlo: lo trae el wrapper (8.10.2).

### El camino corto

```bash
tools/build-apk.sh              # release: tests + R8 → dist/OmniTV-<version>-release.apk
tools/build-apk.sh debug        # debug: más rápido
tools/build-apk.sh release --skip-tests
```

El script comprueba el proyecto, valida el JDK y el SDK, ejecuta los tests,
compila, copia el APK a `dist/` y te muestra versión, tamaño, SHA-256 y huella
de firma.

### Los comandos de siempre

```bash
./gradlew :app:testDebugUnitTest   # tests JVM
./gradlew :app:assembleRelease     # APK release → app/build/outputs/apk/release/
./gradlew :app:assembleDebug       # APK debug   → app/build/outputs/apk/debug/
./gradlew :app:lintDebug           # lint
```

### Con Android Studio

*Abrir* → carpeta del repositorio → esperar la sincronización de Gradle →
menú **Build → Build Bundle(s) / APK(s) → Build APK(s)**. El diálogo final tiene
el enlace «locate» con el archivo.

---

## 4. Firma: leer esto antes de la primera release

Android sólo instala una actualización si su **firma coincide** con la de la
versión ya instalada. Hay dos caminos:

| Modo | Cuándo | Consecuencia |
|---|---|---|
| **Depuración** (por defecto) | pruebas, sideload, uso propio | Archivo firmado con la clave debug: cualquier APK debug del mismo equipo lo actualiza, pero no es apto para Play Store |
| **Producción** | compartir con otras TVs / publicar | Hay que configurar la keystore; siempre la misma clave entre versiones |

### Generar la keystore de producción

```bash
tools/signing/generar-keystore.sh
```

Crea `signing/omnitv-release.jks` (RSA 4096, 30 años), deja el certificado
público y su huella en `signing/`, e imprime **los cuatro secretos** que hay que
pegar en GitHub → *Settings → Secrets and variables → Actions*:

- `OMNITV_KEYSTORE_BASE64` — el `.jks` en base64 (una sola línea)
- `OMNITV_KEYSTORE_PASSWORD`
- `OMNITV_KEY_ALIAS` (por defecto `omnitv`)
- `OMNITV_KEY_PASSWORD`

Con esos secretos, `apk.yml` y `release.yml` firman con la clave de producción y
lo anuncian en el resumen del run.

> ⚠️ **Guarda el `.jks` y sus contraseñas en un lugar seguro y fuera de git**
> (`.gitignore` ya lo excluye). Si se pierde, las TVs con la versión anterior no
> aceptarán ninguna actualización: habría que desinstalar y volver a instalar.

Localmente, la misma firma se usa así:

```bash
OMNITV_KEYSTORE_FILE=signing/omnitv-release.jks \
OMNITV_KEYSTORE_PASSWORD=... OMNITV_KEY_ALIAS=omnitv OMNITV_KEY_PASSWORD=... \
tools/build-apk.sh release
```

### Si la TV dice «app no instalada» / `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

Es un choque de firmas (la instalada se firmó con otra clave). Solución:

```bash
adb uninstall com.omnitv.app
adb install -r OmniTV-<version>-release.apk
```

---

## 5. Instalar en la TV

**Con ADB (lo más cómodo):**

```bash
# En la TV: Ajustes → Sistema → Información → pulsar «Compilación» 7 veces
#          → Opciones de desarrollador → Depuración ADB (activar)
adb connect 192.168.1.50:5555      # IP de tu TV
adb install -r dist/OmniTV-1.2.0-release.apk
```

**Sin cables:** instala «Send Files to TV» (o X-plore) en la TV y en el móvil,
envía el `.apk` y ábrelo desde la TV. Requiere permitir *orígenes desconocidos*
en Ajustes → Seguridad.

Requisitos del televisor: **Android TV / Google TV con Android 5.1 (API 22) o
superior.** La app sólo aparece en el launcher de TV (`LEANBACK_LAUNCHER`).

---

## 6. Problemas conocidos

| Síntoma | Causa | Solución |
|---|---|---|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | firma distinta a la instalada | `adb uninstall com.omnitv.app` y reinstalar (§4) |
| `INSTALL_FAILED_OLDER_SDK` | TV con Android < 5.1 | `minSdk = 22`; no hay soporte para versiones anteriores |
| La actualización «no aparece» | `versionCode` no ha subido | Sube `versionCode` en `app/build.gradle.kts` (Android rechaza códigos menores) |
| El APK no aparece en el launcher | se instaló en el móvil, no en la TV | Es una app sólo de TV; usa un emulador Android TV |
| Gradle falla con «SDK location not found» | falta `ANDROID_HOME` o `local.properties` | ver §3 |
| `Unresolved reference: AppStroke` (o similar) | import perdido en una pantalla | añade el import que indica el error; el CI lo detecta en segundos |
| El run de CI falla en «Recuento de tests» | no se ejecutó ningún test | comprueba que `app/src/test` compila |

> **Nota sobre la etiqueta `v1.2.0`:** esa Release se construyó desde un árbol
> anterior (sin la pestaña Anime, que llegó con el PR #11) y firmada con la clave
> de depuración. Compartía `versionCode = 3` con `main`, así que una TV con la
> 1.2.0 instalada habría rechazado la actualización. Ya está resuelto: el
> proyecto declara **`versionCode = 4` y `versionName = 1.3.0`**. Recuerda subir
> el `versionCode` en cada publicación futura.

---

## 7. Lista rápida antes de publicar

- [ ] `python3 tools/check_build_readiness.py` sin errores.
- [ ] `versionCode` mayor que el de la versión publicada anteriormente.
- [ ] Secretos `OMNITV_KEYSTORE_*` configurados si el APK debe ser de producción.
- [ ] El run de «Generar APK» termina en verde y el resumen muestra la firma esperada.
- [ ] Prueba en la TV: abrir la app, iniciar sesión de perfil, reproducir un título.

---

<p align="center"><em>De <code>git push</code> a la pantalla del salón en menos de 15 minutos.</em></p>
