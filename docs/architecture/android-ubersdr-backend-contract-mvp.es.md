# 1. Alcance

Este contrato cubre solo lo necesario para el MVP Android de escucha HF:

- sesión
- audio remoto
- waterfall/spectrum
- tune
- cambio de modo
- cambio de filtro
- lectura de bandas
- memorias locales en cliente

No cubre:

- IQ modes
- DSP local de receptor
- bookmarks remotos avanzados
- paneles web
- extensiones
- controles DSP no confirmados en el protocolo principal

## Estado funcional Android validado

Estado real actualmente validado en móvil:

- audio operativo
- waterfall operativo
- selección de bandas desde `GET /api/bands`
- barra superior con menú y power
- menú con paleta y telemetría
- línea rápida `MIN - + MAX C`
- frecuencia editable manualmente con teclado Android
- pan con un dedo operativo
- zoom por botones estable
- gesto de pinza descartado por ahora

# 2. Flujo de sesión

Orden validado en cliente Android real:

1. Android genera `user_session_id` UUID.
2. Android hace `POST /connection`.
3. Android hace `GET /api/description`.
4. Si `allowed=true`, abre `WS /ws` de audio.
5. Espera brevemente.
6. Después abre `WS /ws/user-spectrum`.
7. Android queda operativo cuando:
   - audio WS está abierto
   - spectrum WS recibió `config`

Reconexión recomendada:

1. cerrar estado local de conexión
2. repetir `POST /connection`
3. reabrir `/ws`
4. reabrir `/ws/user-spectrum`
5. restaurar último estado local:
   - frecuencia
   - modo
   - filtro
   - zoom visible si se decide mantener

Notas:

- [Confirmado] Audio y spectrum usan el mismo `user_session_id`.
- [Confirmado] `/connection` debe ejecutarse antes de abrir los WS.
- [Confirmado] `GET /api/description` se usa antes de abrir los WS para resolver `default_frequency` y `default_mode`.
- [Confirmado] En despliegues detrás de Cloudflare o rate limiting similar, abrir spectrum demasiado rápido tras audio puede provocar `429 Too Many Requests`.
- [Confirmado] Una espera breve entre audio WS y spectrum WS evita ese `429` en el despliegue actual validado.
- [Asumido para MVP] Ante error de sesión inválida, regenerar UUID y rehacer el flujo completo.

## Reglas técnicas confirmadas en contrato y cliente

- `totalBandwidth = binCount * binBandwidth`
- `zoom` pide `binBandwidth` objetivo y el backend puede normalizarlo
- `pan` no debe enviar `binBandwidth`
- `MAX` envía una sola acción al máximo zoom válido y deja que el backend normalice el límite efectivo
- `MIN` debe restaurar la vista más abierta válida con clamp seguro
- selección de banda debe ajustar también el rango visible
- el pan funcional actual en Android mueve sintonía real y mantiene la vista coherente

# 3. HTTP endpoints usados en MVP

## `POST /connection`

- ruta: `/connection`
- método: `POST`
- body mínimo:

```json
{
  "user_session_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

- body opcional:

```json
{
  "user_session_id": "550e8400-e29b-41d4-a716-446655440000",
  "password": "optional"
}
```

- respuesta esperada:

```json
{
  "client_ip": "203.0.113.10",
  "allowed": true,
  "reason": "",
  "session_timeout": 0,
  "max_session_time": 0,
  "bypassed": false,
  "allowed_iq_modes": []
}
```

- uso en Android:
  - validar acceso
  - detectar rechazo
  - leer límites de sesión

## `GET /api/description`

- ruta: `/api/description`
- método: `GET`
- parámetros: ninguno
- respuesta esperada mínima:

```json
{
  "default_frequency": 14175000,
  "default_mode": "usb",
  "max_session_time": 0,
  "receiver": {
    "name": "Example Receiver"
  }
}
```

- uso en Android:
  - frecuencia inicial
  - modo inicial
  - nombre del receptor

## `GET /api/bands`

- ruta: `/api/bands`
- método: `GET`
- parámetros: ninguno
- respuesta esperada:
  - array de bandas configuradas por backend

- uso en Android:
  - botones rápidos de banda
  - rango visible real por banda usando `start` y `end`
  - cálculo del centro de banda con `(start + end) / 2`
  - aplicación de modo por banda si el backend lo sirve en `mode`
  - fallback de modo coherente con web si `mode` no existe:
    - `LSB` por debajo de `10 MHz`
    - `USB` a partir de `10 MHz`

## `GET /api/bookmarks`

- ruta: `/api/bookmarks`
- método: `GET`
- parámetros: ninguno
- estado en MVP:
  - [Asumido para MVP] opcional, no bloqueante

- uso en Android:
  - no imprescindible para MVP
  - reservado para iteración posterior o lectura simple si ya existe

# 4. WebSocket de audio `/ws`

## Query params obligatorios

```text
/ws?frequency=14175000&mode=usb&user_session_id=<uuid>&format=opus&version=2&bandwidthLow=50&bandwidthHigh=2700
```

Obligatorios para MVP:

- `frequency`
- `mode`
- `user_session_id`
- `format=opus`
- `version=2`

Recomendados para MVP:

- `bandwidthLow`
- `bandwidthHigh`

Opcionales:

- `password`

## Mensajes cliente -> servidor

### `tune`

```json
{
  "type": "tune",
  "frequency": 7100000,
  "mode": "lsb",
  "bandwidthLow": -2700,
  "bandwidthHigh": -50
}
```

Uso:

- cambio de frecuencia
- cambio de modo
- cambio de filtro

### `ping`

```json
{"type":"ping"}
```

Uso:

- keepalive

### `get_status`

```json
{"type":"get_status"}
```

Uso:

- refresco del estado si la UI lo necesita

### `set_squelch`

Estado en MVP:

- [Asumido para MVP] fuera del MVP inicial

Formato observado:

```json
{
  "type": "set_squelch",
  "squelchOpen": 6.0,
  "squelchClose": 4.0
}
```

## Mensajes servidor -> cliente

### `pong`

```json
{"type":"pong"}
```

### `status`

Ejemplo mínimo:

```json
{
  "type": "status",
  "sessionId": "...",
  "frequency": 7100000,
  "mode": "lsb",
  "sampleRate": 48000,
  "channels": 1,
  "info": {}
}
```

### `error`

Ejemplo:

```json
{
  "type": "error",
  "error": "Invalid session. Please refresh the page and try again.",
  "status": 0
}
```

### `squelch_updated`

- fuera de MVP inicial, pero backend lo soporta

## Binarios

Formato MVP:

- [Confirmado] Opus v2
- framing:
  - `timestampNs` `uint64 LE`
  - `sampleRate` `uint32 LE`
  - `channels` `uint8`
  - `basebandPower` `float32 LE`
  - `noiseDensity` `float32 LE`
  - `opusData[]`

Uso en Android:

- parsear cabecera
- la implementación actual ya parsea `sampleRate` y `channels`
- pasar `opusData` al decoder
- decodificar Opus a PCM
- reproducir PCM con `AudioTrack`

Estado actual validado en cliente Android:

- audio WS operativo
- decoder Opus real operativo
- reproducción PCM operativa
- control local de volumen visible y operativo
- mute local visible y operativo

## Errores

Casos a manejar:

- invalid session
- IP mismatch
- frequency out of range
- invalid mode
- bandwidth out of range
- rate limit exceeded

## Keepalive / ping

- [Asumido para MVP] enviar `ping` cada 10 a 30 segundos
- [Confirmado] esperar `pong`

## Tune

Regla Android:

- enviar siempre `tune` completo con:
  - `frequency`
  - `mode`
  - `bandwidthLow`
  - `bandwidthHigh`

Razón:

- evita depender de defaults distintos entre backend y clientes de referencia

# 5. WebSocket de spectrum `/ws/user-spectrum`

## Query params

Mínimo:

```text
/ws/user-spectrum?user_session_id=<uuid>&mode=binary8
```

Obligatorio:

- `user_session_id`

Recomendado para MVP:

- `mode=binary8`

Opcional:

- `password`

## Mensajes cliente -> servidor

### `ping`

```json
{"type":"ping"}
```

### `get_status`

```json
{"type":"get_status"}
```

### `zoom`

```json
{
  "type": "zoom",
  "frequency": 14074000,
  "binBandwidth": 25.0
}
```

Reglas confirmadas:

- `frequency` usa frecuencia absoluta en Hz
- `binBandwidth` es el ancho de bin objetivo solicitado por el cliente
- el backend puede normalizar `binBandwidth` y ajustar `binCount`
- el cliente debe aceptar la `config` resultante como autoridad final
- `totalBandwidth = binCount * binBandwidth`
- hacer zoom significa pedir un `binBandwidth` distinto
- `MAX` debe enviar una sola petición de `zoom` al mínimo `binBandwidth` práctico y dejar que el backend normalice el máximo zoom válido

### `pan`

```json
{
  "type": "pan",
  "frequency": 14074000
}
```

Reglas confirmadas:

- `frequency` usa frecuencia absoluta en Hz
- no se debe enviar `binBandwidth` en `pan`

### `reset`

```json
{"type":"reset"}
```

## Mensajes servidor -> cliente

### `config`

`config` no debe asumirse como mensaje de texto.

Comportamiento real confirmado:

- `config` llega como frame WebSocket binario
- ese frame binario va comprimido con gzip
- el cliente debe usar `onMessage(bytes)`
- después debe descomprimir con gzip y parsear el JSON resultante

Ejemplo lógico del JSON una vez descomprimido:

```json
{
  "type": "config",
  "centerFreq": 7100000,
  "binCount": 800,
  "binBandwidth": 25,
  "totalBandwidth": 20000,
  "sessionId": "..."
}
```

### `pong`

```json
{"type":"pong"}
```

### `error`

```json
{
  "type": "error",
  "error": "Failed to update spectrum: ...",
  "status": 0
}
```

## Mensaje `config`

Campos usados en MVP:

- `centerFreq`
- `binCount`
- `binBandwidth`
- `totalBandwidth`
- `sessionId`

Relación confirmada:

- `totalBandwidth = binCount * binBandwidth`

Uso:

- inicializar renderer
- calcular frecuencia por pixel
- alinear cursor de sintonía y bordes de filtro

## Frames `SPEC`

Header fijo confirmado: `22` bytes

| Offset | Tamaño | Campo |
| ------ | ------ | ----- |
| 0-3 | 4 | `"SPEC"` |
| 4 | 1 | `version` |
| 5 | 1 | `flags` |
| 6-13 | 8 | `timestamp` |
| 14-21 | 8 | `frequency` |

Todos los enteros están en little-endian.

Tipos de frame confirmados:

| Flag | Tipo | Payload |
| ---- | ---- | ------- |
| `0x01` | full float32 | `binCount * 4` |
| `0x02` | delta float32 | `2 + N * (index:uint16 + float32)` |
| `0x03` | full uint8 | `binCount * 1` |
| `0x04` | delta uint8 | `2 + N * (index:uint16 + uint8)` |

Convención MVP:

- [Confirmado] Android MVP usa `mode=binary8`
- [Confirmado] Android MVP soporta `0x03` y `0x04`
- [Pendiente de validar] soporte posterior para float32 si algún servidor no usa `binary8`

Reconstrucción mínima necesaria:

1. si `flags == 0x03`, reemplazar buffer completo
2. si `flags == 0x04`, leer:

```text
changeCount:uint16
repetir N veces:
  index:uint16
  value:uint8
```

3. aplicar los cambios sobre el último buffer completo
4. si llega delta sin buffer previo, ignorarlo

Conclusión operativa:

- el cliente debe mantener siempre un buffer reconstruido de tamaño `binCount`
- no se puede asumir que todos los frames son full
- los deltas son el comportamiento normal tras el primer full
- `full/delta` correctos no garantizan imagen correcta si el cliente interpreta mal el orden horizontal

Orden horizontal crítico:

- `binary8` puede llegar en orden FFT crudo
- ese orden no debe pintarse directamente como izquierda->derecha en frecuencia
- antes de representar el waterfall, el cliente visual debe aplicar `unwrap` o `fftshift` de media anchura
- convención visual correcta:
  - primera mitad visible = `rawData[half..end]`
  - segunda mitad visible = `rawData[0..half-1]`

Síntomas observados cuando falta ese `unwrap`:

- señales que cambian de lado al hacer zoom
- señales que salen por izquierda y reaparecen por derecha
- tap aparentemente incorrecto aunque la geometría `centerFreq/totalBandwidth/x` sea correcta

Corrección aplicada en Android:

- mantener la reconstrucción `0x03`/`0x04` sin cambios
- aplicar solo `unwrap` visual de media anchura antes de pintar la fila del waterfall

Estado actual validado en cliente Android:

- reconstrucción `0x03`/`0x04` operativa
- waterfall estable tras zoom/pan
- fix de `unwrap` consolidado en el render
- tap, hover, cursor y eje ya son coherentes entre sí
- síntoma anterior resuelto: la señal ya no “salta” de lado al hacer zoom

# 6. Modelo de estado local Android

```text
SessionState
- baseUrl
- userSessionId
- connectionAllowed
- sessionTimeoutSec
- maxSessionTimeSec
- isConnectedAudio
- isConnectedSpectrum

LocalRadioState
- tunedFrequencyHz
- visibleCenterFrequencyHz
- mode
- bandwidthLowHz
- bandwidthHighHz
- binBandwidthHz
- binCount
- totalBandwidthHz
- signalBasebandPowerDb
- signalNoiseDensityDb
- signalSnrDb
- audioPlaying
- audioMuted
- audioVolume
- tuneStepHz
- selectedBandId
- reconnectState

BandItem
- label
- startHz
- endHz
- defaultMode

MemoryItem
- id
- name
- frequencyHz
- mode
- bandwidthLowHz
- bandwidthHighHz
```

Campos mínimos que deben vivir en cliente:

- sesión
- frecuencia actual
- frecuencia visible del waterfall
- modo
- `bandwidthLow`
- `bandwidthHigh`
- zoom/binBandwidth
- bands
- memorias locales
- estado de audio
- estado de reconexión

# 7. Acciones UI y su traducción a backend

## Tap en waterfall -> tune

- UI calcula frecuencia objetivo desde `centerFreq`, `totalBandwidth` y coordenada X
- Android envía:

```json
{
  "type": "tune",
  "frequency": 14074200,
  "mode": "usb",
  "bandwidthLow": 50,
  "bandwidthHigh": 2700
}
```

## Drag 1 dedo -> tune

- actualiza frecuencia en pasos discretos o continuos
- enviar `tune` con throttling moderado en cliente

## Pinch -> zoom

- Android calcula nuevo `binBandwidth`
- envía:

```json
{
  "type": "zoom",
  "frequency": 14074000,
  "binBandwidth": 25
}
```

## Cambio de modo -> tune con nuevo mode

- Android actualiza presets de filtro del modo
- envía `tune` completo

## Cambio de filtro -> tune con nuevos `bandwidthLow/high`

- Android mantiene frecuencia y modo
- envía `tune` completo

## Cambio de banda -> tune + posible zoom default

- Android lee `start` y `end` desde `/api/bands`
- calcula `center = (start + end) / 2`
- deriva un `binBandwidth` objetivo para mostrar el ancho real de esa banda
- aplica `zoom` al rango de banda
- aplica `tune` a `center`
- aplica `mode` de API si existe
- si no existe `mode`, usa el mismo fallback que web:
  - `LSB` por debajo de `10 MHz`
  - `USB` a partir de `10 MHz`

Regla importante:

- no debe mantenerse el span global previo al pulsar banda
- la vista no debe quedarse en un rango abierto tipo `0-30 MHz`

## Memoria -> restaurar frecuencia/modo/filtro

- Android carga memoria local
- envía `tune` completo

# 8. Errores y recuperación

## Session invalid

- síntoma:
  - error WS o rechazo HTTP
- acción:
  - regenerar `user_session_id`
  - repetir flujo completo

## Timeout

- síntoma:
  - cierre de sesión o denegación posterior
- acción:
  - volver a `/connection`
  - mostrar motivo

## WS cerrado

- síntoma:
  - `onClosed` o `onFailure`
- acción:
  - marcar desconectado
  - intentar reconectar en orden

## Spectrum sin `config`

- síntoma:
  - el WS abre pero no hay configuración utilizable
- acción:
  - pedir `get_status`
  - comprobar recepción de mensajes binarios gzip
  - no asumir que `config` llegará como texto
  - si sigue sin llegar, recrear solo el WS de spectrum

## Audio sin paquetes

- síntoma:
  - WS abierto pero no entra binario
- acción:
  - esperar ventana corta
  - pedir `get_status`
  - reconectar audio WS si persiste

## Cambio de red

- síntoma:
  - IP cambia, UUID puede quedar inválido por binding
- acción:
  - cerrar ambos WS
  - regenerar UUID
  - rehacer sesión completa

Estrategia general:

- no intentar reparar parcialmente una sesión rota por IP mismatch
- reiniciar sesión completa cuando el backend lo indique

# 9. Pseudomodelos de datos

## `ConnectionRequest`

```json
{
  "user_session_id": "550e8400-e29b-41d4-a716-446655440000",
  "password": null
}
```

## `ConnectionResponse`

```json
{
  "client_ip": "203.0.113.10",
  "allowed": true,
  "reason": "",
  "session_timeout": 0,
  "max_session_time": 0,
  "bypassed": false,
  "allowed_iq_modes": []
}
```

## `AudioWsQuery`

```json
{
  "frequency": 14175000,
  "mode": "usb",
  "user_session_id": "<uuid>",
  "format": "opus",
  "version": 2,
  "bandwidthLow": 50,
  "bandwidthHigh": 2700
}
```

## `SpectrumWsQuery`

```json
{
  "user_session_id": "<uuid>",
  "mode": "binary8"
}
```

## `TuneMessage`

```json
{
  "type": "tune",
  "frequency": 7100000,
  "mode": "lsb",
  "bandwidthLow": -2700,
  "bandwidthHigh": -50
}
```

## `SpectrumConfig`

```json
{
  "type": "config",
  "centerFreq": 7100000,
  "binCount": 800,
  "binBandwidth": 25,
  "totalBandwidth": 20000,
  "sessionId": "..."
}
```

## `LocalRadioState`

```json
{
  "tunedFrequencyHz": 7100000,
  "visibleCenterFrequencyHz": 7100000,
  "mode": "lsb",
  "bandwidthLowHz": -2700,
  "bandwidthHighHz": -50,
  "binBandwidthHz": 25,
  "audioVolume": 0.8,
  "audioMuted": false,
  "selectedBandId": "40m",
  "reconnectState": "connected"
}
```

# 10. Decisiones explícitas

- [Confirmado] La app no demodula IQ.
- [Confirmado] El filtro principal es remoto.
- [Confirmado] El waterfall RF viene del backend.
- [Confirmado] Audio y spectrum usan WebSocket separados.
- [Confirmado] El volumen final es local.
- [Asumido para MVP] Opus v2 es el formato principal de audio.
- [Confirmado] `binary8` es el formato usado por el cliente Android MVP para waterfall.
- [Confirmado] El backend no expone un parámetro específico para forzar FULL frames de spectrum.
- [Confirmado] FULL suele aparecer al inicio de sesión, en resize o cuando los cambios son suficientemente grandes.
- [Confirmado] El cliente debe asumir que después del primer full llegarán deltas.
- [Asumido para MVP] Android enviará siempre `tune` completo.
- [Asumido para MVP] Las memorias del MVP son locales al dispositivo.
- [Pendiente de validar] La política exacta de presets CW queda del lado del cliente Android para evitar depender de defaults inconsistentes.

## Notas de implementación Android (validado en cliente real)

Validación experimental observada:

- `binCount = 1024`
- payload delta típico aproximado: `100-200 bytes`
- tras reconstrucción:
  - buffer final = `1024`
  - `match=true`

Render actual en Android:

- waterfall funcional
- escala de grises
- ancho lógico igual a `binCount`
- altura fija aproximada de `256` líneas
- scroll vertical continuo
- eje de frecuencia visible
- línea central visible

Estado actual del cliente Android spectrum:

- `zoom` y `pan` reales validados contra backend
- el backend soporta `zoom`, `pan`, `reset`, `ping` y `get_status`
- el enfoque de zoom visual local en UI no es la solución correcta
- `zoom out` satura en el mayor `binBandwidth` observado
- `MAX` ya no usa una secuencia incremental visible de varios `zoomIn`
- `MAX` aplica una sola acción directa al máximo zoom válido centrada en la frecuencia actual
- la selección de banda ya no conserva el span global previo

## Bugs corregidos que afectan al contrato de integración

- `unwrap` visual obligatorio del spectrum antes de pintar waterfall
- bug del botón textual `PWR` corregido: el power principal está en la barra superior y la línea rápida ahora usa `MIN`
- bug de `MAX` incremental sustituido por acción directa
- bug de `MIN` con bordes negativos corregido con clamp de rango válido
- bug de bandas sin ajustar span corregido usando `start/end` reales de `/api/bands`
- bug de pan con referencia fija y bloqueo corregido migrando a drag de sintonía real
- gesto de pinza retirado por mala usabilidad sobre una lógica de zoom backend normalizada/discreta

## Nota operativa sobre zoom discreto real

Aunque Android envía `binBandwidth` como valor continuo, el backend puede discretizarlo y ajustar `binCount`.

En el servidor actual eso ocurre en `user_spectrum_websocket.go`:

- redondeo de `binBandwidth` a escalones seguros
- reducción/restauración de `binCount` según profundidad de zoom

Conclusión práctica:

- el zoom efectivo no debe tratarse como visualmente continuo
- por eso el gesto de pinza no se mantiene en el cliente Android actual
- resultado observado: ya se distinguen señales CW y SSB con cierta facilidad

Todavía no implementado en este estado:

- interacción táctil
- color mapping avanzado

Implicación:

- el backend usa compresión delta de forma activa
- cualquier cliente no web debe soportar:
  - mensajes binarios gzip para `config`
  - header `SPEC` de 22 bytes
  - interpretación correcta de `flags`
  - reconstrucción delta
  - no asumir frames completos continuos
