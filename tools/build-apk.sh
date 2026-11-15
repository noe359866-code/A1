#!/usr/bin/env bash
# Compila el APK de Omni TV (Android TV) en local y lo deja en dist/.
#
#   tools/build-apk.sh            # release (minificado, listo para la TV)
#   tools/build-apk.sh debug      # debug (más rápido, sin R8)
#   tools/build-apk.sh release --skip-tests
#
# Requisitos: JDK 17 y Android SDK 35 (ANDROID_HOME o local.properties).
# Todo lo demás lo descarga Gradle la primera vez.
set -euo pipefail

cd "$(dirname "$0")/.."

VARIANT="${1:-release}"
shift || true
SKIP_TESTS=0
for arg in "$@"; do
  case "$arg" in
    --skip-tests) SKIP_TESTS=1 ;;
    *) echo "Argumento desconocido: $arg" >&2; exit 2 ;;
  esac
done

if [ "$VARIANT" != "release" ] && [ "$VARIANT" != "debug" ]; then
  echo "Variante no válida: $VARIANT (usa release o debug)" >&2
  exit 2
fi

echo "==> 1/5 Comprobaciones de preparación"
python3 tools/check_build_readiness.py
python3 tools/check_android_tv.py

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: no hay java en el PATH. Instala un JDK 17 (Temurin/OpenJDK)." >&2
  exit 1
fi
JAVA_MAJOR=$(java -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -n1)
if [ -n "$JAVA_MAJOR" ] && [ "$JAVA_MAJOR" -lt 17 ]; then
  echo "ERROR: se necesita JDK 17 o superior (detectado $JAVA_MAJOR)." >&2
  exit 1
fi

echo "==> 2/5 SDK de Android"
if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ] && [ ! -f local.properties ]; then
  echo "ERROR: define ANDROID_HOME/ANDROID_SDK_ROOT o crea local.properties con sdk.dir=..." >&2
  echo "       (Android Studio → SDK Manager instala la plataforma 35)" >&2
  exit 1
fi

echo "==> 3/5 Gradle"
if [ -x ./gradlew ]; then
  GRADLE=(./gradlew --no-daemon)
else
  GRADLE=(gradle --no-daemon)
fi

if [ "$SKIP_TESTS" -eq 0 ]; then
  echo "==> 4/5 Tests unitarios"
  "${GRADLE[@]}" :app:testDebugUnitTest
else
  echo "==> 4/5 Tests omitidos (--skip-tests)"
fi

echo "==> 5/5 Compilando APK ($VARIANT)"
TASK="assemble${VARIANT^}"
"${GRADLE[@]}" ":app:$TASK"

APK_DIR="app/build/outputs/apk/$VARIANT"
APK=$(ls -1 "$APK_DIR"/*.apk 2>/dev/null | head -n1 || true)
if [ -z "$APK" ]; then
  echo "ERROR: Gradle no generó ningún APK en $APK_DIR" >&2
  exit 1
fi

VERSION_NAME=$(sed -nE 's/.*versionName *= *"([^"]+)".*/\1/p' app/build.gradle.kts | head -n1)
VERSION_CODE=$(sed -nE 's/.*versionCode *= *([0-9]+).*/\1/p' app/build.gradle.kts | head -n1)
mkdir -p dist
OUT="dist/OmniTV-${VERSION_NAME}-${VARIANT}.apk"
cp "$APK" "$OUT"

SIZE=$(du -h "$OUT" | cut -f1)
SHA=$(sha256sum "$OUT" | cut -d' ' -f1)

echo
echo "APK generado: $OUT"
echo "  versión : $VERSION_NAME (versionCode $VERSION_CODE)"
echo "  tamaño  : $SIZE"
echo "  SHA-256 : $SHA"

# Huella de la firma: sirve para saber si una TV podrá actualizar sin desinstalar.
APKSIGNER=$(ls -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/nonexistent}}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n1 || true)
if [ -n "$APKSIGNER" ] && [ -x "$APKSIGNER" ]; then
  echo "  firma   : $("$APKSIGNER" verify --print-certs "$OUT" | sed -nE 's/.*certificate SHA-256 digest: *(.*)/\1/p' | head -n1)"
fi

echo
echo "Instalar en la TV (con depuración ADB activada):"
echo "  adb connect <IP_DE_LA_TV>:5555"
echo "  adb install -r \"$OUT\""
