# Compatibilidad multimedia con FFmpeg externo

Omni TV **no incluye FFmpeg dentro del APK**. El reproductor sigue siendo Media3 ExoPlayer y usa los decodificadores del dispositivo. Esta integración añade dos piezas:

1. **Diagnóstico de códecs** en el televisor: cuando Media3 falla, se guarda el `errorCodeName`, los MIME/codecs observados y si la pista fue marcada como no soportada. Se muestra en Ajustes → Compatibilidad multimedia.
2. **Puente multimedia opcional** (`tools/ffmpeg-bridge/`): un servicio Node 20+ que ejecuta `ffprobe`/`ffmpeg` en otro equipo, remuxea a HLS y convierte audio no AAC a AAC. El vídeo H.264 se copia; otros códecs de vídeo solo se recodifican si el operador arranca el puente con `--video-transcode`.

FastFlix no se integra: es una GUI de escritorio, no una librería Android. Tampoco se añade FFmpegKit ni un decoder nativo al APK.

## Cuándo actúa

- El TV intenta primero las fuentes normales del addon.
- Si todas fallan y Media3 apunta a **códec/contenedor** (decoder, parsing o pista no soportada), y hay puente configurado, el TV pide `POST /prepare` una vez por fuente.
- Los fallos de red/HTTP **no** disparan conversión.
- Si el puente devuelve una URL HLS del mismo origen, el TV la añade como fuente `· puente` y reintenta conservando la posición.
- Sin puente configurado, la app solo muestra el diagnóstico; no inventa una fuente compatible.

## Arrancar el puente

Requisitos en el equipo del puente: Node 20+, `ffmpeg` y `ffprobe` en el PATH (o rutas con `--ffmpeg`/`--ffprobe`).

```bash
node tools/ffmpeg-bridge/bridge.mjs \
  --host 0.0.0.0 --port 8790 \
  --allow-origin https://cdn-de-tus-addons.example \
  --token UN_TOKEN_LARGO
```

- `--allow-origin` se puede repetir. Debe ser el origen exacto (`https://host:puerto`) de los streams que el puente puede leer. No se aceptan rutas ni comodines.
- Si el puente escucha fuera de loopback, exige `--token` (o `OMNI_FFMPEG_TOKEN`).
- Por defecto solo copia vídeo H.264. Para recodificar vídeo no H.264 a H.264:

```bash
node tools/ffmpeg-bridge/bridge.mjs --host 0.0.0.0 --port 8790 \
  --allow-origin https://cdn.example --token UN_TOKEN_LARGO --video-transcode
```

La recodificación de vídeo consume CPU y puede no seguir tiempo real en equipos lentos. El puente limita trabajos concurrentes (`--max-jobs`, máx. 4), tiempo de probe (`--probe-timeout`), espera del primer segmento (`--ready-timeout`), duración total del trabajo (`--job-timeout`) y conserva los HLS temporales solo mientras se usan (`--ttl`).

## Configurar el televisor

1. Escanea el QR de Ajustes → Vincular.
2. En **Compatibilidad multimedia · FFmpeg externo**, guarda `http://IP-DEL-EQUIPO:8790`.
3. En **Añadir APIs**, guarda `FFMPEG_BRIDGE=UN_TOKEN_LARGO` si el puente usa token.
4. Abre Ajustes → **Comprobar manifests y puentes**. El estado debe decir que FFmpeg está conectado.

La URL del puente sigue la política de red de Omni TV: HTTPS siempre, o HTTP solo en LAN privada. El token se guarda con las demás claves de API (cifrado Keystore cuando el dispositivo lo admite).

## Contrato HTTP

- `GET /health` → `{ "ok": true, "ffmpeg": "ffmpeg version …", "ffprobe": "…", "videoTranscode": false }`
- `POST /prepare` con cabecera `Authorization: Bearer <token>` si está configurado:

```json
{
  "url": "https://cdn.example/pelicula.mkv",
  "headers": { "Referer": "https://addon.example/" },
  "reason": "ERROR_CODE_DECODER_INIT_FAILED"
}
```

Respuesta útil:

```json
{
  "action": "transcode",
  "url": "/media/<id>/playlist.m3u8",
  "format": "hls",
  "video": "copy",
  "audio": "aac"
}
```

El TV resuelve la URL contra el mismo origen del puente y solo acepta rutas `/media/…`. Las cabeceras del stream original se reenvían a FFmpeg tras sanitizarlas; no se envían cookies ni cabeceras de salto. El token del puente no se reenvía al CDN.

## Límites reales

- No reproduce torrents, magnets ni DRM.
- No conserva subtítulos en la versión convertida (HLS de vídeo/audio).
- El audio convertido sale como AAC estéreo; se pierde audio multicanal original.
- Si el vídeo no es H.264 y el puente no tiene `--video-transcode`, el puente responde error en vez de fingir compatibilidad.
- El puente es un servicio de confianza para tu LAN. No lo expongas a Internet sin TLS, autenticación adicional y aislamiento.
- La prueba automatizada usa fixtures de `ffmpeg`/`ffprobe`; no garantiza reproducción en un TV físico ni con todos los CDN.

## Pruebas

```bash
node --test tools/ffmpeg-bridge/bridge.test.mjs
```

En CI también se ejecutan los tests JVM de `MediaBridge`, `PlaybackDiagnostics` y el panel de vinculación.
