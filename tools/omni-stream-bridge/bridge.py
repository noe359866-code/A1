#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Omni Stream Bridge — puente mínimo para entregar streams a Omni TV.

Implementa el protocolo de `docs/STREAM-ADDON-PROTOCOL.md`:

    GET /stream/{tipo}/{id}.json     ← forma estándar (Stremio)
    GET /stream/{tipo}/{id}          ← forma simple (alternativa)

ÚNICO PUNTO DE ENGANCHE: la función `resolve()` de abajo. Sustitúyela por
la llamada a los resolvers de TU proyecto (providers/extractors tipo
StreamFlix, tu API, tu base de datos…) y Omni TV reproducirá tus enlaces
con su menú multi-servidor.

Uso:
    python3 bridge.py --port 8787

Luego añade  http://<ip-de-esta-máquina>:8787  en el panel QR de Omni TV
(Ajustes → Vincular → 🎬 Añadir addons de streams).

Sin dependencias externas: solo Python 3 (stdlib).
"""

import argparse
import json
import re
import socket
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote, urlparse

# Límites alineados con el cliente de Omni TV (StreamHub)
MAX_STREAMS = 24
NAME = "omni-stream-bridge"

# --------------------------------------------------------------------------
# PUNTO DE ENGANCHE — aquí va la lógica de TU proyecto
# --------------------------------------------------------------------------

def resolve(kind: str, video_id: str):
    """Devuelve los enlaces de reproducción para un título.

    kind:    'movie' | 'series'
    video_id: 'tt1375666', 'tmdb:123', 'tt0944947:1:1', 'tmdb:123:1:2'…

    Devuelve una lista de dicts con esta forma:

        {
            "label":   "Servidor 1",                 # obligatorio
            "url":     "https://…/video.mp4|m3u8",   # obligatorio
            "quality": "1080p",                      # opcional
            "title":   "Español Latino · HDR",       # opcional
        }

    ----- EJEMPLO (sustituir por tus resolvers) ---------------------------
    Imagina que tu proyecto ya resuelve enlaces así:

        def mis_extractores(tipo, identificador):
            # …tu scraping/API aquí…
            return [{"label": "MiServidor", "url": "https://cdn…/v.mp4"}]

    Basta con devolver esa lista desde esta función. En un proyecto tipo
    StreamFlix (Kotlin), llama a tus providers/extractors desde su propio
    puente — ver `android/OmniStreamServer.kt` de esta misma carpeta.
    ------------------------------------------------------------------------
    """
    # Demo de arranque: dos enlaces de prueba (Google sample videos) para
    # verificar el cableado de punta a punta antes de enganchar tu proyecto.
    return [
        {
            "label": "Demo · Big Buck Bunny",
            "url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            "quality": "720p",
            "title": f"{kind}:{video_id} · sustituye resolve() por tus resolvers",
        },
        {
            "label": "Demo · Sintel",
            "url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            "quality": "720p",
        },
    ]

# --------------------------------------------------------------------------
# Normalización (no tocar: replica lo que espera el cliente de Omni TV)
# --------------------------------------------------------------------------

_QUALITY_RE = re.compile(r"(2160|1440|1080|720|576|480|360)p?", re.I)


def _infer_quality(*texts):
    for t in texts:
        m = _QUALITY_RE.search(t or "")
        if m:
            return m.group(1) + "p"
    return "SD"


def normalize(raw_list):
    """Limpia y normaliza la lista de enlaces del resolver."""
    out, seen = [], set()
    for item in raw_list or []:
        if not isinstance(item, dict):
            continue
        url = str(item.get("url", "")).strip()
        if not url.startswith(("http://", "https://")):
            continue  # sin torrents ni enlaces no reproducibles
        if url in seen:
            continue
        seen.add(url)
        label = str(item.get("label") or item.get("name") or "Servidor").strip()
        title = str(item.get("title") or "").strip()
        quality = str(item.get("quality") or _infer_quality(label, title, url))
        out.append({"label": label, "name": label, "title": title,
                    "url": url, "quality": quality})
        if len(out) >= MAX_STREAMS:
            break
    return out


def stremio_form(streams):
    """Forma estándar Stremio."""
    return {"streams": [
        {"name": s["name"], "title": s["title"] or s["quality"], "url": s["url"],
         "quality": s["quality"]}
        for s in streams
    ]}


def simple_form(streams):
    """Forma simple."""
    return {"streams": [
        {"label": s["label"], "url": s["url"], "quality": s["quality"],
         "title": s["title"]}
        for s in streams
    ]}

# --------------------------------------------------------------------------
# Servidor HTTP
# --------------------------------------------------------------------------

_ID_RE = re.compile(r"^[A-Za-z0-9:_\-.]+$")
_KIND_ALIASES = {"movie": "movie", "film": "movie", "series": "series",
                 "serie": "series", "tv": "series", "show": "series"}


class Handler(BaseHTTPRequestHandler):
    server_version = NAME

    def _send(self, code, body, ctype="application/json; charset=utf-8"):
        data = body if isinstance(body, bytes) else body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def _json(self, code, obj):
        self._send(code, json.dumps(obj, ensure_ascii=False))

    def do_OPTIONS(self):  # CORS preflight
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.end_headers()

    def do_GET(self):
        path = urlparse(self.path).path
        try:
            if path in ("/", "/index.html"):
                self._send(200, INDEX_HTML, "text/html; charset=utf-8")
                return
            if path == "/health":
                self._json(200, {"ok": True, "name": NAME})
                return
            if path.startswith("/stream/"):
                self._handle_stream(path[len("/stream/"):])
                return
            self._json(404, {"err": "ruta no encontrada", "protocolo": "/stream/{tipo}/{id}"})
        except BrokenPipeError:
            pass
        except Exception as exc:  # nunca tumbar el puente por un título
            self._json(500, {"err": f"{type(exc).__name__}: {exc}"})

    def _handle_stream(self, rest):
        # …/{tipo}/{id}[.json]
        if rest.endswith(".json"):
            rest = rest[:-5]
        parts = rest.split("/", 1)
        if len(parts) != 2:
            self._json(400, {"err": "formato: /stream/{tipo}/{id}"})
            return
        kind_raw, video_id = unquote(parts[0]).lower(), unquote(parts[1])
        kind = _KIND_ALIASES.get(kind_raw)
        if kind is None or not _ID_RE.match(video_id):
            self._json(400, {"err": f"tipo o id inválidos: {kind_raw}/{video_id}"})
            return
        streams = normalize(resolve(kind, video_id))
        # Se responde con la SUPERFORMA (name+label+title+url+quality): la
        # parsean igual el cliente estándar y el simple.
        self._json(200, stremio_form(streams) | {
            "streams": [{**s} for s in streams],
            "cacheMaxAge": 60,
        })

    def log_message(self, fmt, *args):
        print(f"[{NAME}] {self.address_string()} {fmt % args}")


INDEX_HTML = f"""<!doctype html><html lang="es"><head><meta charset="utf-8">
<title>{NAME}</title>
<style>body{{font-family:system-ui;background:#0B0B0F;color:#eee;max-width:700px;margin:2rem auto;padding:0 1rem}}
code,pre{{background:#16161C;border-radius:8px;padding:.2rem .5rem}} pre{{padding:1rem;overflow:auto}}
a{{color:#0A84FF}} h1{{color:#0A84FF}}</style></head><body>
<h1>🌉 {NAME}</h1>
<p>Puente de streams para <b>Omni TV</b>. Estado: <a href="/health">/health</a></p>
<h3>Protocolo</h3>
<pre>GET /stream/movie/tt1375666.json
GET /stream/series/tt0944947:1:1.json
GET /stream/series/tt0944947:1:1      ← forma simple</pre>
<h3>Cableado</h3>
<p>Edita la función <code>resolve()</code> de <code>bridge.py</code> para
devolver los enlaces de TU proyecto. Añade esta URL en Omni TV:
<b>Ajustes → Vincular → 🎬 Añadir addons de streams</b>.</p>
</body></html>"""


def _lan_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except OSError:
        return "127.0.0.1"


def main():
    ap = argparse.ArgumentParser(description=NAME)
    ap.add_argument("--port", type=int, default=8787)
    ap.add_argument("--host", default="0.0.0.0",
                    help="0.0.0.0 para acceso desde la LAN (por defecto)")
    args = ap.parse_args()

    srv = ThreadingHTTPServer((args.host, args.port), Handler)
    ip = _lan_ip()
    print(f"🌉 {NAME} escuchando en http://{ip}:{args.port}/")
    print(f"   Añade en Omni TV → Ajustes → Vincular → 🎬 Añadir addons de streams:")
    print(f"   http://{ip}:{args.port}")
    print("   Ctrl+C para parar.")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("\n👋 Puente detenido.")


if __name__ == "__main__":
    main()
