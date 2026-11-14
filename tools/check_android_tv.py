#!/usr/bin/env python3
"""Contrato estático de distribución TV; no sustituye una prueba en un TV real."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
A = "{http://schemas.android.com/apk/res/android}"
MAIN = ROOT / "app/src/main"


def attr(node, name):
    return node.get(A + name)


def main():
    errors = []
    manifest = ET.parse(MAIN / "AndroidManifest.xml").getroot()
    features = {attr(node, "name"): attr(node, "required") for node in manifest.findall("uses-feature")}
    if features.get("android.software.leanback") != "true":
        errors.append("Leanback debe ser obligatorio")
    if features.get("android.hardware.touchscreen") != "false":
        errors.append("El táctil debe ser opcional")
    app = manifest.find("application")
    if app is None or attr(app, "banner") != "@drawable/tv_banner":
        errors.append("Falta el banner de la aplicación TV")
    activities = app.findall("activity") if app is not None else []
    if len(activities) != 1:
        errors.append("Se esperaba una única actividad TV")
    launchers = []
    for activity in activities:
        if attr(activity, "screenOrientation") != "landscape":
            errors.append("La actividad debe ser horizontal")
        for intent in activity.findall("intent-filter"):
            categories = {attr(node, "name") for node in intent.findall("category")}
            actions = {attr(node, "name") for node in intent.findall("action")}
            if "android.intent.action.MAIN" in actions:
                launchers.extend(categories)
    if "android.intent.category.LEANBACK_LAUNCHER" not in launchers:
        errors.append("Falta LEANBACK_LAUNCHER")
    if "android.intent.category.LAUNCHER" in launchers:
        errors.append("No se debe publicar un iniciador de móvil")
    banner = ET.parse(MAIN / "res/drawable/tv_banner.xml").getroot()
    if (attr(banner, "width"), attr(banner, "height")) != ("320dp", "180dp"):
        errors.append("El banner debe declarar 320 × 180 dp (16:9)")
    for kind in ("layout-w360dp", "layout-sw360dp", "mobile"):
        if (MAIN / ("res/" + kind if kind.startswith("layout") else "java/com/omnitv/app/" + kind)).exists():
            errors.append("Variante específica de móvil: " + kind)
    # Todos los XML deben seguir siendo analizables después de editar recursos.
    for path in MAIN.rglob("*.xml"):
        try:
            ET.parse(path)
        except ET.ParseError as exc:
            errors.append(f"{path.relative_to(ROOT)}: {exc}")
    if errors:
        for error in errors:
            print("ERROR:", error)
        return 1
    print("Contrato Android TV OK: Leanback requerido, táctil opcional, banner 16:9 y sin launcher móvil.")
    print("Pendiente de dispositivo: foco D-pad, overscan, ciclo de vida y reproducción reales.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
