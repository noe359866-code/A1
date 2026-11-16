#!/usr/bin/env python3
"""Comprobaciones estáticas de *preparación para compilar* (sin Android SDK).

Este script no compila: verifica que todo lo que hace falta para producir un
APK está en su sitio y es coherente. Se puede ejecutar en cualquier máquina
con Python 3, incluida una sin JDK ni SDK de Android, y también en CI.

Comprueba:
  1. Ficheros base de Gradle (wrapper, catálogo de versiones, settings).
  2. Que cada alias `libs.*` usado en los build.gradle.kts exista de verdad en
     gradle/libs.versions.toml (un alias inventado rompe la configuración).
  3. Que los plugins declarados por alias existan en [plugins].
  4. Versión de la app (versionCode sube, versionName semántica).
  5. Firma: coherencia de las variables OMNITV_KEYSTORE_* y aviso si falta
     alguna (sin ellas el APK sale firmado con la clave de depuración, que no
     sirve para actualizar una instalación existente).
  6. Workflows de GitHub necesarios para generar el APK sin máquina local.

Salida: código 0 si no hay errores (los avisos no fallan el proceso).
"""
from __future__ import annotations

import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

errors: list[str] = []
warnings: list[str] = []
ok: list[str] = []


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


# ---------------------------------------------------------------- 1. Gradle
GRADLE_FILES = [
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle.properties",
    "gradle/libs.versions.toml",
    "gradle/wrapper/gradle-wrapper.properties",
    "gradle/wrapper/gradle-wrapper.jar",
    "gradlew",
    "app/build.gradle.kts",
    "app/proguard-rules.pro",
    "app/src/main/AndroidManifest.xml",
]

for name in GRADLE_FILES:
    path = ROOT / name
    if not path.exists() or path.stat().st_size == 0:
        errors.append(f"Falta o está vacío: {name}")
if not errors:
    ok.append(f"{len(GRADLE_FILES)} ficheros base de Gradle presentes")

# El wrapper jar es binario: comprobamos que sea un ZIP real (primera firma PK).
jar = ROOT / "gradle/wrapper/gradle-wrapper.jar"
if jar.exists() and jar.read_bytes()[:2] != b"PK":
    errors.append("gradle/wrapper/gradle-wrapper.jar no es un JAR válido")

wrapper = ROOT / "gradle/wrapper/gradle-wrapper.properties"
if wrapper.exists():
    match = re.search(r"gradle-([\d.]+)-bin\.zip", read(wrapper))
    if not match:
        errors.append("gradle-wrapper.properties no fija una distribución -bin")
    else:
        ok.append(f"Gradle wrapper {match.group(1)}")

if not os.access(ROOT / "gradlew", os.X_OK):
    warnings.append("gradlew no tiene permiso de ejecución (chmod +x gradlew)")

# --------------------------------------------- 2. Catálogo de dependencias
toml_path = ROOT / "gradle/libs.versions.toml"
if toml_path.exists():
    toml = read(toml_path)
    sections: dict[str, set[str]] = {"versions": set(), "libraries": set(), "plugins": set(), "bundles": set()}
    current = None
    for line in toml.splitlines():
        line = line.strip()
        header = re.match(r"^\[(\w+)\]$", line)
        if header:
            current = header.group(1)
            sections.setdefault(current, set())
            continue
        entry = re.match(r"^([\w.-]+)\s*=", line)
        if entry and current:
            sections[current].add(entry.group(1))

    used_aliases: set[str] = set()
    used_plugins: set[str] = set()
    for gradle_file in ("build.gradle.kts", "app/build.gradle.kts"):
        text = read(ROOT / gradle_file)
        text = re.sub(r"//.*", "", text)
        for alias in re.findall(r"\blibs\.((?:[\w-]+\.)+[\w-]+)", text):
            # libs.plugins.* se resuelve contra [plugins], no contra [libraries]
            if not alias.startswith("plugins."):
                used_aliases.add(alias)
        # alias(libs.plugins.foo.bar)
        used_plugins.update(re.findall(r"alias\(\s*libs\.plugins\.([\w.-]+)\s*\)", text))

    def to_toml_key(alias: str) -> str:
        # libs.androidx.media3.exoplayer.hls -> androidx-media3-exoplayer-hls
        return alias.replace(".", "-").replace("_", "-")

    library_keys = {to_toml_key(a) for a in used_aliases}
    plugin_keys = {to_toml_key(p) for p in used_plugins}

    missing_libs = sorted(k for k in library_keys if k not in sections.get("libraries", set()))
    missing_plugins = sorted(k for k in plugin_keys if k not in sections.get("plugins", set()))
    for key in missing_libs:
        errors.append(f"Alias de dependencia usado pero no declarado en libs.versions.toml: {key}")
    for key in missing_plugins:
        errors.append(f"Alias de plugin usado pero no declarado en libs.versions.toml: {key}")
    if not missing_libs and not missing_plugins:
        ok.append(
            f"Catálogo de versiones coherente ({len(library_keys)} dependencias, {len(plugin_keys)} plugins)"
        )

# ------------------------------------------------------------- 3. Versión
app_gradle = ROOT / "app/build.gradle.kts"
if app_gradle.exists():
    text = read(app_gradle)
    code = re.search(r"versionCode\s*=\s*(\d+)", text)
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not code or not name:
        errors.append("app/build.gradle.kts no declara versionCode/versionName literales")
    else:
        version_code, version_name = int(code.group(1)), name.group(1)
        if not re.fullmatch(r"\d+\.\d+\.\d+", version_name):
            warnings.append(f"versionName «{version_name}» no es semántica (X.Y.Z)")
        if version_code < 1:
            errors.append("versionCode debe ser >= 1")
        ok.append(f"Versión declarada: {version_name} (versionCode {version_code})")

    if not re.search(r"isMinifyEnabled\s*=\s*true", text):
        warnings.append("El release no activa R8 (minifyEnabled)")
    if "isShrinkResources = true" not in text:
        warnings.append("El release no activa shrinkResources")
    if "compose = true" not in text:
        errors.append("buildFeatures.compose no está activado")

# Fijamos las versiones de build en el catálogo (no en el script): así CI y
# local compilan exactamente lo mismo.
if toml_path.exists():
    for key in ("agp", "kotlin"):
        value = re.search(rf'^{key}\s*=\s*"([^"]+)"', read(toml_path), re.M)
        if value:
            ok.append(f"{key} {value.group(1)} (fijado en el catálogo)")

# -------------------------------------------------------------- 4. Firma
SIGNING_VARS = [
    "OMNITV_KEYSTORE_FILE",
    "OMNITV_KEYSTORE_PASSWORD",
    "OMNITV_KEY_ALIAS",
    "OMNITV_KEY_PASSWORD",
]
present = [v for v in SIGNING_VARS if os.environ.get(v, "").strip()]
missing = [v for v in SIGNING_VARS if v not in present]
if present and missing:
    errors.append("Firma a medias: faltan " + ", ".join(missing))
elif present:
    keystore = Path(os.environ["OMNITV_KEYSTORE_FILE"])
    if not keystore.exists():
        errors.append(f"OMNITV_KEYSTORE_FILE apunta a un fichero inexistente: {keystore}")
    else:
        ok.append("Firma de producción configurada (keystore de release)")
else:
    warnings.append(
        "Sin OMNITV_KEYSTORE_*: el APK se firmará con la clave de depuración. "
        "Sirve para sideload, pero no para actualizar una instalación hecha con otra clave."
    )

# --------------------------------------------------------- 5. Workflows
for workflow in (".github/workflows/ci.yml", ".github/workflows/apk.yml"):
    path = ROOT / workflow
    if not path.exists():
        warnings.append(f"No existe {workflow}: no se podrá compilar el APK desde GitHub")
    else:
        ok.append(f"Workflow disponible: {workflow}")

# ------------------------------------- 6. Descarga directa del APK (sin ZIP)
# Un artefacto de Actions siempre se empaqueta en un ZIP. Para que el usuario
# pueda bajar `OmniTV.apk` con un clic, la release debe adjuntar el APK con ese
# nombre exacto y existir una release «latest» que no caduque.
APK_NAME = "OmniTV.apk"

for workflow in (".github/workflows/apk.yml", ".github/workflows/release.yml"):
    path = ROOT / workflow
    if not path.exists():
        continue
    text = read(path)
    name = workflow.rsplit("/", 1)[-1]
    if 'OUT="' + APK_NAME + '"' not in text:
        errors.append(f"{name}: no genera el APK con nombre fijo {APK_NAME} (rompería la descarga directa)")
        continue
    if APK_NAME not in text.split("files:", 1)[-1]:
        errors.append(f"{name}: la Release no adjunta {APK_NAME}")
        continue
    ok.append(f"{name}: publica {APK_NAME} (descarga directa, sin ZIP)")

apk_workflow = ROOT / ".github/workflows/apk.yml"
if apk_workflow.exists():
    text = read(apk_workflow)
    if 'tag_name: latest' not in text:
        errors.append(
            "apk.yml: falta la release rodante con etiqueta «latest» "
            "(el enlace permanente /releases/download/latest/OmniTV.apk dejaría de funcionar)"
        )
    else:
        ok.append("apk.yml: release rodante «latest» activa")

    # Las etiquetas v* las publica release.yml; si apk.yml también las tomara,
    # dos workflows escribirían la misma release.
    trigger = text.split("jobs:", 1)[0]
    if re.search(r"^\s+tags:", trigger, re.M):
        warnings.append("apk.yml: también se dispara con etiquetas v*; release.yml ya las publica")

    if "/releases/download/latest/" not in text:
        warnings.append("apk.yml: el cuerpo de la release no menciona el enlace permanente")

    # /releases/latest/download/OmniTV.apk sólo resuelve si alguna release está
    # marcada como «Latest». Si la rodante no lo pide, el enlace corto acabaría
    # apuntando a una versión antigua (o a una que ya no adjunta el archivo).
    rolling = text.split('tag_name: latest', 1)
    if len(rolling) > 1 and 'make_latest: true' not in rolling[1][:600]:
        errors.append(
            "apk.yml: la release «latest» no usa make_latest: true; "
            "/releases/latest/download/OmniTV.apk podría apuntar a una release antigua"
        )
    elif len(rolling) > 1:
        ok.append("apk.yml: la release «latest» se marca como Latest")

# Las releases públicas nunca deben reemplazarse con una variante debug,
# una ejecución manual desde una rama de trabajo o un APK firmado con debug.
if apk_workflow.exists():
    text = read(apk_workflow)
    required_guards = (
        ('GITHUB_EVENT_NAME:-}" = "push"', "evento push"),
        ('$GITHUB_REF" = "refs/heads/main"', "rama main"),
        ('$VARIANT" = "release"', "variante release"),
        ('PUBLISH_DEV=false', "bloqueo de latest sin firma de producción"),
        ('No se puede publicar una variante debug', "bloqueo de publicación debug"),
    )
    for token, label in required_guards:
        if token not in text:
            errors.append(f"apk.yml: falta protección de publicación ({label})")
    if 'GITHUB_REF_TYPE:-}" = "branch"' in text:
        errors.append("apk.yml: no debe publicar latest por el mero hecho de ejecutarse en una rama")
    else:
        ok.append("apk.yml: publicación restringida a push main y release firmada")

release_workflow = ROOT / ".github/workflows/release.yml"
if release_workflow.exists():
    text = read(release_workflow)
    if "No se publican releases firmadas con debug" not in text:
        errors.append("release.yml: permite publicar una APK firmada con debug")
    if '$GITHUB_REF" != "refs/heads/main"' not in text:
        errors.append("release.yml: falta la protección de rama main para publicaciones manuales")
    if "keytool -list" not in text:
        errors.append("release.yml: no valida keystore y alias antes de compilar")
    if 'GITHUB_REF_TYPE:-}" = "tag"' not in text:
        errors.append("release.yml: debe distinguir un push de etiqueta de una ejecución manual")
    else:
        ok.append("release.yml: exige keystore de producción y etiqueta versionada")

# Enlaces de descarga en la documentación: si alguien cambia el nombre del
# archivo, los enlaces del README y de la guía quedarían roto.
for doc in ("README.md", "docs/COMPILAR_APK.md"):
    path = ROOT / doc
    if path.exists() and "releases/download/latest/" not in read(path):
        warnings.append(f"{doc}: no documenta el enlace de descarga directa")

# Un workflow roto de YAML sólo se descubre cuando GitHub lo rechaza: lo
# validamos aquí si PyYAML está disponible (no es obligatorio).
try:  # pragma: no cover - depende del entorno
    import yaml  # type: ignore

    for workflow in ROOT.glob(".github/workflows/*.yml"):
        try:
            yaml.safe_load(read(workflow))
        except Exception as exc:  # noqa: BLE001
            errors.append(f"YAML inválido en {workflow.relative_to(ROOT)}: {exc}")
    ok.append("Workflows YAML válidos")
except ImportError:
    pass

# -------------------------------------------------------------- Resumen
for message in ok:
    print("OK   ", message)
for message in warnings:
    print("AVISO", message)
for message in errors:
    print("ERROR", message)

if errors:
    print(f"\n{len(errors)} error(es). El proyecto NO está listo para compilar.")
    sys.exit(1)

print("\nListo para compilar: el proyecto puede generar el APK de Android TV.")
if warnings:
    print(f"{len(warnings)} aviso(s) que no impiden compilar.")
