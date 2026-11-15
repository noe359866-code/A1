#!/usr/bin/env bash
# Crea una keystore de producción y muestra los secretos que hay que pegar en
# GitHub (Settings → Secrets and variables → Actions).
#
#   tools/signing/generar-keystore.sh
#
# La keystore se guarda FUERA de git (signing/ está ignorado salvo el
# certificado público). Si la pierdes, las TVs ya instaladas no aceptarán
# ninguna actualización firmada con otra clave: guárdala con copia de seguridad.
set -euo pipefail

cd "$(dirname "$0")/../.."
mkdir -p signing

ALIAS="${OMNITV_KEY_ALIAS:-omnitv}"
STORE="signing/omnitv-release.jks"

if ! command -v keytool >/dev/null 2>&1; then
  echo "ERROR: keytool no está en el PATH (viene con cualquier JDK 17)." >&2
  exit 1
fi

if [ -f "$STORE" ]; then
  echo "ERROR: ya existe $STORE. No lo sobrescribas: la firma debe ser siempre la misma." >&2
  echo "       Si de verdad quieres una nueva, muévelo antes a otro sitio." >&2
  exit 1
fi

echo "==> Creando keystore RSA 4096 (válida 30 años) en $STORE"
keytool -genkeypair \
  -keystore "$STORE" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 10950 \
  -storetype JKS \
  -dname "CN=OmniTV, OU=Omni TV, O=Omni TV, L=Managua, C=NI"

echo
echo "==> Certificado público y huella (estos SÍ se pueden subir a git)"
keytool -exportcert -rfc -keystore "$STORE" -alias "$ALIAS" -file signing/omnitv-release-cert.pem
keytool -list -v -keystore "$STORE" -alias "$ALIAS" \
  | sed -nE 's/^[[:space:]]+SHA256: *(.*)/\1/p' | head -n1 > signing/CERT_SHA256.txt
cat signing/CERT_SHA256.txt

echo
echo "==> Secretos para GitHub (pegar uno a uno en el repo)"
echo
echo "OMNITV_KEY_ALIAS"
echo "  $ALIAS"
echo
echo "OMNITV_KEYSTORE_PASSWORD  y  OMNITV_KEY_PASSWORD"
echo "  (la contraseña que acabas de introducir en keytool)"
echo
echo "OMNITV_KEYSTORE_BASE64  (todo esto en una sola línea)"
echo
base64 -w0 "$STORE" 2>/dev/null || base64 "$STORE" | tr -d '\n'
echo
echo
echo "==> También puedes compilar en local sin secretos:"
echo "    OMNITV_KEYSTORE_FILE=$STORE \\"
echo "    OMNITV_KEYSTORE_PASSWORD=... OMNITV_KEY_ALIAS=$ALIAS OMNITV_KEY_PASSWORD=... \\"
echo "    tools/build-apk.sh release"
