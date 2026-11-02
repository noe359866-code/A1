# Despliegue en Stremio Beamup

Beamup es el PaaS gratuito de Stremio para addons. Internamente es **Dokku con
buildpack Herokuish**: el despliegue es un `git push` a un remoto SSH, Dokku
instala las dependencias con `npm`, arranca el proceso `web` del `Procfile`
inyectando `PORT`, y **sólo publica el addon si `/manifest.json` pasa el linter
oficial** (`stremio-addon-linter`).

> **No hay despliegue real ejecutado desde este entorno.** Para publicar hacen
> falta datos que sólo tiene el propietario de la cuenta: el host de Beamup y una
> clave SSH privada cuya pública esté asociada a la cuenta de GitHub. Todo lo
> demás (código, buildpack, workflow y contrato del linter) ya está listo en el
> repositorio.

---

## 1. Qué se preparó en el repositorio

| Archivo | Para qué sirve en Beamup |
| --- | --- |
| `package.json` | Dispara el buildpack de Node. Tiene `start`, `main`, `engines.node` y un `package-lock.json` reproducible. |
| `Procfile` → `web: node src/index.js` | Define explícitamente el proceso `web` que Dokku escala y supervisa. |
| `src/index.js` | Lee `PORT` (Dokku lo inyecta) y enlaza en `HOST \|\| 0.0.0.0`, obligatorio dentro del contenedor. |
| `.slugignore` | Reduce el slug: excluye `tests/`, `docs/`, `.github/`, `docker/`, etc. |
| `docker/Dockerfile` | Docker local sigue funcionando, pero **se movió fuera de la raíz**: un `Dockerfile` en la raíz haría que Dokku eligiera el *builder* de Dockerfile en vez de Herokuish, que es el único plenamente soportado por Beamup. |
| `.gitignore` → `beamup.json` | El CLI genera ese archivo con el nombre del proyecto y el último commit; no se versiona. |
| `src/public/logo.png`, `background.png`, `favicon.ico` | Assets de marca servidos por el propio addon en `/logo.png`, `/background.png` y `/favicon.ico`. Están dentro de `src/`, así que `.slugignore` **no** los excluye. |
| `src/manifest.js` | `id=org.omniindex.addon`, `v1.1.0`, nombre **Omni Index** y URLs absolutas de logo/fondo calculadas con el origen de la petición. |
| `tests/beamup.test.js`, `tests/brand.test.js` | Reproducen el chequeo de Beamup (linter oficial sobre el manifiesto por HTTP, arranque real respetando `PORT`) y verifican la identidad y los assets. |
| `.github/workflows/deploy-beamup.yml` | Alternativa de despliegue con `Stremio/beamup-deploy-action@v1.0.1`, **sólo manual** (`workflow_dispatch`). |

El addon es *stateless*: la caché y los limitadores viven en memoria, así que no
necesita disco persistente ni base de datos. No hay datos de prueba ni respuestas
sintéticas: en Beamup se consultarán las seis APIs reales igual que en local.

## 2. Requisitos previos

- Node.js en local y una cuenta de GitHub con una **clave SSH pública** cargada
  (Beamup la sincroniza con `sync-github-keys`).
- Un host de Beamup; el habitual es `a.baby-beamup.club`.
- El CLI oficial (el paquete es `beamup-cli`, **no** `stremio-beamup`):

  ```bash
  npm install -g beamup-cli      # v1.3.2, comando `beamup`
  ```

## 3. Despliegue con el CLI

```bash
cd A1
npm ci                           # opcional en local; verifica el contrato
npm test                         # incluye el chequeo del linter de Beamup

beamup config a.baby-beamup.club TU_USUARIO_GITHUB
beamup init omniindex           # crea beamup.json y el remoto git `beamup`
beamup                           # configura + despliega (o `beamup deploy`)
```

`beamup deploy` equivale a `git push beamup HEAD:master --force`. Los despliegues
siguientes son simplemente:

```bash
git commit -am "..."             # Beamup compara el último commit para re-desplegar
npm run beamup:deploy            # = beamup deploy
```

> El CLI pregunta si quiere hacer un *auto-commit* cuando el HEAD no cambió desde
> el último despliegue. Conviene tener el árbol limpio antes de desplegar.

### URL del addon

Beamup calcula el subdominio así (tomado de `lib/utils.js` del CLI):

```
hash   = sha256(usuario_github_en_minúsculas + "\n").hexdigest()[0:12]
dominio = hash + "-" + proyecto_en_minúsculas + ".baby-beamup.club"
```

Ejemplo con proyecto `omniindex`:

```
https://<hash>-omniindex.baby-beamup.club/manifest.json
```

Copia esa URL en Stremio (`Agregar complemento → URL`), o abre
`https://<hash>-omniindex.baby-beamup.club/configure` para generar el enlace
`stremio://.../c/<config>/manifest.json` con tu configuración.

## 4. Variables de entorno y logs

Las opciones del addon se pasan por la URL de configuración, no por entorno.
Aun así, los *overrides* de endpoints y la clave de predataba.se se pueden
inyectar como secretos de Dokku:

```bash
beamup secrets OMNI_PREDATABA_KEY 'tu-api-key'
beamup secrets OMNI_XREL_BASE 'https://api-alternate.xrel.top'

# Recomendado si el proxy no reenvía el Host público: fija las URLs del logo/fondo
beamup secrets OMNI_PUBLIC_URL 'https://<hash>-omniindex.baby-beamup.club'

# Equivalente directo, sin CLI:
ssh dokku@a.baby-beamup.club config:set <hash>/omniindex NODE_ENV=production

beamup logs
# o: ssh dokku@a.baby-beamup.club logs <hash>/omniindex
```

Tras cambiar secretos hay que re-desplegar (Dokku reinicia el proceso al hacer
`config:set`). Variables disponibles: `PORT`, `HOST`, `OMNI_PORT`,
`OMNI_KNABEN_BASE`, `OMNI_TORRENTS_CSV_BASE`, `OMNI_TORLOCK_BASE`,
`OMNI_PREDB_NET_BASE`, `OMNI_PREDATABA_SE_BASE`, `OMNI_XREL_BASE`,
`OMNI_CINEMETA_BASE`, `OMNI_PREDATABA_KEY`, `OMNI_PREDATABA_TYPE`,
`OMNI_XREL_ALTERNATE` y **`OMNI_PUBLIC_URL`**. Ver `README.md`.

### Logo y fondo detrás del proxy

Stremio exige URLs **absolutas** en `manifest.logo` y `manifest.background`. El
addon las genera a partir del origen de cada petición (`Host` o
`X-Forwarded-Host` + `X-Forwarded-Proto`), por lo que un usuario que instale
desde `https://<hash>-omniindex.baby-beamup.club/manifest.json` recibe
`https://<hash>-omniindex.baby-beamup.club/logo.png`. Durante el *check* del
linter, Beamup consulta el contenedor por su IP interna; ese manifiesto no se
entrega a ningún usuario, así que no afecta. Si tu proxy reescribe el Host,
fija `OMNI_PUBLIC_URL` con el dominio público.

## 5. Despliegue con GitHub Actions

El workflow `.github/workflows/deploy-beamup.yml` es **manual** porque la acción
hace un `push --force` al remoto de Beamup. Configura en el repositorio:

| Tipo | Nombre | Valor |
| --- | --- | --- |
| Secret | `SSH_PRIVATE_KEY` | Clave privada SSH (sin passphrase) cuya pública está en GitHub |
| Variable | `BEAMUP_HOST` | `a.baby-beamup.club` (ya hay valor por defecto) |
| Variable | `USERNAME_GITHUB` | Usuario de GitHub (por defecto, el dueño del repo) |
| Variable | `PROJECT_NAME` | `omniindex` (opcional: el input `project_name` del despacho tiene prioridad) |

Luego: *Actions → Desplegar en Beamup → Run workflow*. La acción sincroniza las
claves de GitHub, calcula el hash del usuario y empuja `HEAD:master`.

> El workflow debe existir en la rama por defecto para que GitHub permita
> despacharlo con `workflow_dispatch`.

## 6. El linter de Beamup

El plugin `beamup-addon-linter` se ejecuta **después de arrancar el contenedor y
antes de publicarlo**: hace `GET http://<ip>:<PORT>/manifest.json` (5 intentos,
10 s cada uno) y exige `lintManifest(manifest).valid === true`. Si falla, el
despliegue se rechaza. Reglas relevantes:

- `id`, `name` y `version` (semver válido) deben ser strings.
- `resources`, `types`, `catalogs` e `idPrefixes` deben ser arrays.
- `behaviorHints` debe ser objeto y sus flags booleanos.
- Cada catálogo necesita `id`/`type` string y `extra` como array.

`tests/beamup.test.js` valida exactamente eso con la misma librería que usa el
servidor (`stremio-addon-linter@1.7.0`), tanto para el manifiesto por defecto
como con `enableCatalog: true`, y sirviéndolo por HTTP. `npm test` falla localmente
si un cambio rompería el despliegue.

## 7. Solución de problemas

| Síntoma | Causa / solución |
| --- | --- |
| `beamup-lint: ... refusing to deploy` | `/manifest.json` no responde o no pasa el linter. Ejecuta `npm test` y comprueba que el servidor arranca con `PORT`. |
| El build usa Dockerfile | Verifica que **no** exista `Dockerfile` en la raíz. Está en `docker/Dockerfile`. |
| `EADDRINUSE` / contenedor no responde | El proceso debe enlazar `0.0.0.0` y usar `PORT`; revisa `HOST`. |
| `streams: []` en Beamup | El host debe poder alcanzar las seis APIs y Cinemeta. Algunas bloquean IPs de datacenter (Torlock es el caso más habitual); comprueba `/api/status` y los logs. No se rellena con resultados falsos: son errores reales de red. |
| Caché vacía tras reinicio | Es en memoria por diseño; se recalienta sola. |
| `npm ci` falla en el build | Confirmar que `package-lock.json` está versionado y que el buildpack tiene salida a `registry.npmjs.org`. |
| La página `/configure` genera una URL interna | Usa la URL pública de Beamup en el navegador; la UI construye el enlace con `location.host`. |
| El logo no aparece en Stremio | Comprueba `manifest.logo`: debe ser una URL pública absoluta. Si apunta a una IP interna, define `OMNI_PUBLIC_URL` y re-despliega. |
| Addon duplicado / configuración perdida tras la v1.1.0 | El ID pasó de `org.arena.omniindex` a `org.omniindex.addon`; Stremio lo considera otro addon. Desinstala el anterior y vuelve a instalarlo. |

## 8. Referencias

- `https://github.com/Stremio/stremio-beamup` (infraestructura, plugin linter)
- `https://github.com/Stremio/stremio-beamup-cli` (`beamup-cli`, `lib/utils.js`, `lib/deploy.js`)
- `https://github.com/Stremio/beamup-deploy-action` (`v1.0.1`, `action.yml`)
- `https://github.com/Stremio/stremio-addon-linter` (`lintManifest`)
