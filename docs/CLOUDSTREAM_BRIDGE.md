# Extensiones Cloudstream mediante puente externo

Omni TV **no carga archivos `.cs3`/DEX ni ejecuta extensiones dentro del televisor**. La opción implementada es un puente HTTP hacia un runtime Cloudstream de confianza instalado en otro equipo.

## Uso

1. Instala un runtime con CLI compatible `providers/search/info/links`, por ejemplo Stream Goat (`cloudstream-linux`).
2. En ese equipo añade tus repositorios e instala las extensiones con sus comandos (`repo add`, `install`, `providers`). Omni TV no modifica ese equipo.
3. Arranca el puente de este repositorio:

```bash
node tools/cloudstream-bridge/bridge.mjs --host 0.0.0.0 --port 8789 \
  --runtime cloudstream-linux
# Opcional: repetir --provider Nombre para limitar qué proveedores se consultan.
```

4. En el panel QR del televisor, **Extensiones Cloudstream**, guarda `http://IP-DEL-EQUIPO:8789`.
5. Abre Ajustes → **Comprobar manifests y puentes**. Al reproducir, StreamHub consulta este puente en paralelo con Stremio/Nuvio.

## Contrato

- `GET /health` → `{ "ok": true, "extensions": ["Provider", …] }`
- `POST /resolve` con:

```json
{"title":"Serie","type":"series","id":"tt1:1:2","year":2024,"imdbId":"tt1","tmdbId":null,"season":1,"episode":2}
```

Respuesta: `{ "streams": [{ "name", "url", "quality", "headers" }] }`. Solo se aceptan URLs HTTP directas que cumplan `NetPolicy` de la app. Si el runtime no identifica el episodio exacto, el puente devuelve vacío en vez de reproducir el primero.

## Límites

- El puente parsea la salida CLI del runtime; no es un cargador nativo de extensiones. Si el runtime cambia su formato, hay que actualizar el adaptador.
- La salida CLI de Stream Goat no expone todas las cabeceras/cookies internas; algunos enlaces protegidos pueden necesitar un runtime/servicio que sí las entregue.
- No se envían claves de API del televisor al puente. Configura las credenciales en el runtime externo.
- Ejecuta solo extensiones de confianza en un equipo/contenedor aislado. El puente limita concurrencia, tiempo y tamaño de salida, pero no convierte código de terceros en seguro.
- Prueba automatizada con runtime fixture: `node --test tools/cloudstream-bridge/bridge.test.mjs`. No garantiza disponibilidad de proveedores externos ni reproducción en un TV físico.
