# Perfiles en Omni TV: modelo, puertas y límites

**Fecha:** 2026-11-16 · Estado: **auditoría estática** (sin compilador ni dispositivo en el entorno).

## Modelo y persistencia

`data/Profile.kt` / `data/ProfileStore.kt` — perfiles estilo Netflix (máx. 12 avatares
ilustrados propios, **sin límite de número de perfiles**):

| Dato | Dónde vive | Notas |
|---|---|---|
| `profiles[]` (nombre, avatarIndex, isKids, autoPlayNext) | `SharedPreferences` `omni_tv_profiles` como JSON | `ProfileStore` es estado observable por Compose |
| `current_profile` (id activo) | misma prefs | `init()` valida que el id exista; si no, queda sin perfil elegido |
| `progress: Map<videoId, Float>` | dentro de cada perfil | Se borra al llegar a ≥ 95 % o < 2 %; "Continuar viendo" por perfil |
| `myList: Set<String>` | dentro de cada perfil | Aislada por perfil |
| PIN parental (huella SHA-256 + salt) | `SettingsStore` (global, operador del TV) | `ParentalPin` (data/): salt aleatorio por dispositivo, comparación en tiempo constante, longitud fija 4 dígitos |

Reglas: no se puede borrar el último perfil; `ProfileStore.add` usa nombre "Perfil N"
si el campo queda vacío; los cambios de nombre se limitan a 32 caracteres (UI).

## Puertas parentales (perfil infantil + PIN configurado)

1. **Ajustes**: al entrar con perfil infantil se superpone `PinTask.GATE`
   (`SettingsScreen`); la pantalla queda detrás con foco atrapado en la rejilla del
   PIN; `Atrás` sale a Inicio. Quitar el PIN exige verificarlo
   (`PinTask.CLEAR` con `verifyParentalPin`); configurar exige doble entrada.
2. **Cambiar/gestionar/añadir perfil**: `ProfileSelectScreen` pide el PIN solo para
   acciones que **escalan** de infantil a adulto o requieren gestión
   (`needsPin`): seleccionar un perfil estándar desde infantil, Gestionar, Añadir.
   Moverse entre perfiles infantiles o re-elegir el perfil activo no pide PIN.
3. **Barra superior**: `ProfileBadge` lleva a «¿Quién está viendo?», que aplica la
   puerta anterior. Sin PIN configurado, no hay puerta (comportamiento deliberado:
   el control parental existe solo con PIN activo).
4. **Pestaña Ajustes en vista previa (hover)**: `OmniTvApp` no navega a
   `SETTINGS` con perfil infantil + PIN (la ruta real OK además exige el GATE).
5. **Reproducción**: `autoPlayNext` es por perfil; Mi lista y progreso aislados.

## Corregido en esta tanda

- **Bloqueo anti fuerza bruta reiniciable** (`data/PinAttemptGuard.kt`):
  antes el contador de fallos y el bloqueo de 30 s vivían en el estado de la propia
  UI del diálogo, de modo que descartar y reabrir el diálogo del PIN reiniciaba el
  bloqueo y permitía probar combinaciones sin límite efectivo. Ahora el estado vive
  a nivel de aplicación: 5 fallos consecutivos → bloqueo de 30 s compartido entre
  aperturas; se reinicia tras un PIN correcto o al caducar el bloqueo.
- **PIN pedido de más en «¿Quién está viendo?»**: seleccionar un perfil infantil o
  re-elegir el perfil activo ya no pide PIN (no hay escalada de privilegios). Solo
  pedirlo al seleccionar uno estándar, gestionar o añadir.

## Límites conocidos / decisiones abiertas

- **El bloqueo no persiste entre reinicios del proceso** (obstáculo doméstico, no
  criptografía de alta seguridad; 10.000 combinaciones acotadas por UI).
- **Sin PIN por perfil estándar** (solo PIN parental global para bloquear la salida
  del perfil infantil). Funcionalidad nueva: fuera de alcance actual.
- **Borrar el perfil activo** deja `currentProfileId = null`; el progreso y Mi lista
  de ese perfil se pierden con él (la UI avisa en el diálogo de borrado).
- **Catálogo infantil casi vacío** con fuentes remotas sin clasificación
  (TP/7+): los metas de Cinemeta/Nuvio rara vez traen `certification`. Es el mismo
  hallazgo de catálogo ya documentado; no es un fallo de perfiles en sí.
- **Sin límite de perfiles** (a diferencia de Netflix, que limita a 5).
- **Trakt y credenciales del panel QR no son por perfil** (son globales del TV).

## Verificación (honesta)

- Balance de sintaxis de `PinAttemptGuard.kt`, `PinDialog.kt`,
  `ProfileSelectScreen.kt`: OK.
- `python3 tools/check_android_tv.py`: contrato Android TV OK (pendiente de
  dispositivo: foco D-pad, overscan, ciclo de vida y reproducción).
- **No compilado, no ejecutado en dispositivo.** La lógica PIN es determinista y se
  revisó línea a línea, pero sigue sin validarse en pantalla real. Ninguna prueba
  unitaria cubre los diálogos.
