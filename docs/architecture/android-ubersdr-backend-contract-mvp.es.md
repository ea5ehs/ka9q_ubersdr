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

# 2. Flujo de sesión

Orden recomendado:

1. Android genera `user_session_id` UUID.
2. Android hace `POST /connection`.
3. Si `allowed=true`, abre `WS /ws` de audio.
4. Después abre `WS /ws/user-spectrum`.
5. Android queda operativo cuando:
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
- [Asumido para MVP] Ante error de sesión inválida, regenerar UUID y rehacer el flujo completo.

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
  - posibles rangos por defecto

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
- guardar métricas de señal
- pasar `opusData` al decoder

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
  "frequency": 7100000,
  "binBandwidth": 25
}
```

### `pan`

```json
{
  "type": "pan",
  "frequency": 7105000
}
```

### `reset`

```json
{"type":"reset"}
```

## Mensajes servidor -> cliente

### `config`

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

Uso:

- inicializar renderer
- calcular frecuencia por pixel
- alinear cursor de sintonía y bordes de filtro

## Frames `SPEC`

Resumen operativo:

- magic: `"SPEC"`
- version: `0x01`
- flags:
  - `0x01` full float32
  - `0x02` delta float32
  - `0x03` full uint8
  - `0x04` delta uint8
- timestamp `uint64 LE`
- frequency `uint64 LE`
- payload full o delta

Convención MVP:

- [Asumido para MVP] Android soporta al menos `0x03` y `0x04` con `mode=binary8`
- [Pendiente de validar] soporte posterior para float32 si algún servidor no usa `binary8`

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

- Android selecciona frecuencia inicial de la banda
- envía `tune`
- opcionalmente después `zoom` a un ancho por defecto de banda

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
  - llegan frames o se abre WS pero no hay configuración utilizable
- acción:
  - pedir `get_status`
  - si no llega `config`, recrear solo el WS de spectrum

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
- [Asumido para MVP] `binary8` es el formato principal de waterfall.
- [Asumido para MVP] Android enviará siempre `tune` completo.
- [Asumido para MVP] Las memorias del MVP son locales al dispositivo.
- [Pendiente de validar] La política exacta de presets CW queda del lado del cliente Android para evitar depender de defaults inconsistentes.
