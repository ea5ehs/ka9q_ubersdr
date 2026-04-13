# 1. Visión general

En v1 se va a construir una app Android/Kotlin de cliente ligero para UberSDR:

- abre sesión contra backend
- conecta audio por WebSocket
- conecta waterfall/spectrum por WebSocket
- reproduce audio remoto
- permite sintonizar, cambiar modo y mover filtro
- guarda memorias locales

Arquitectura general:

- backend = autoridad de canal, demodulación, filtro remoto, waterfall RF
- cliente Android = control, reproducción, render y UX móvil

Componentes principales:

- sesión/conexión
- WS audio
- WS spectrum
- reproductor de audio
- renderer de waterfall
- estado de radio en `ViewModel`
- persistencia local

# 2. Stack técnico recomendado

- lenguaje: Kotlin
  - estándar natural para Android moderno
- UI: Jetpack Compose
  - encaja bien con pantalla única MVP, estado observable y render reactivo
- networking HTTP: Retrofit + OkHttp
  - Retrofit simplifica `/connection`, `/api/description`, `/api/bands`
  - OkHttp ya resuelve también WebSocket
- WebSocket: OkHttp WebSocket
  - suficiente para `/ws` y `/ws/user-spectrum`
- audio: `AudioTrack`
  - mejor elección que ExoPlayer aquí porque el flujo es binario en tiempo real y controlado por nosotros
- decoder Opus: wrapper JNI sobre `libopus`
  - opción recomendada: empaquetar `libopus` Android y exponer una interfaz Kotlin mínima
  - no depender de `MediaCodec` para Opus framing custom del backend
- persistencia: DataStore
  - suficiente para host, volumen, última frecuencia, modo, filtro, memorias simples
- concurrencia: coroutines + Flow
  - buen ajuste para WS, estado reactivo y UI Compose

# 3. Estructura de paquetes

```text
com.example.ubersdrclient

- app/
- model/
- data/
  - network/
  - websocket/
  - datastore/
- session/
- audio/
- spectrum/
- domain/
- ui/
  - screen/
  - components/
  - state/
```

Distribución concreta:

- `model/`
  - DTOs y modelos de estado
- `data/network/`
  - Retrofit API
  - mappers HTTP
- `data/websocket/`
  - clientes WS
  - parsers binarios
- `data/datastore/`
  - settings y memorias locales
- `session/`
  - UUID, arranque de sesión, reconexión
- `audio/`
  - parser audio v2
  - decoder Opus
  - buffer
  - `AudioTrack`
- `spectrum/`
  - parser `SPEC`
  - estado de waterfall
  - renderer
- `domain/`
  - casos de uso simples si hacen falta
- `ui/`
  - Compose
  - `RadioViewModel`

# 4. Componentes principales

## `SessionRepository`

Qué hace:

- coordina el flujo completo de sesión
- conserva `user_session_id`
- conoce el orden de conexión/reconexión

Entradas:

- `baseUrl`
- settings locales
- acciones de UI como `connect()`, `reconnect()`, `disconnect()`

Outputs:

- estado global de conexión
- errores de sesión
- estado combinado audio/spectrum

## `ConnectionService`

Qué hace:

- llamadas HTTP a `/connection`, `/api/description`, `/api/bands`

Entradas:

- `baseUrl`
- `userSessionId`
- password opcional

Outputs:

- `ConnectionResponse`
- `DescriptionResponse`
- lista de bandas

## `AudioWsClient`

Qué hace:

- abre `/ws`
- envía `tune`, `ping`, `get_status`
- recibe mensajes JSON y frames binarios de audio

Entradas:

- `baseUrl`
- `userSessionId`
- query inicial de audio
- comandos de tune

Outputs:

- flujo de audio binario
- flujo de eventos `pong/status/error`

## `SpectrumWsClient`

Qué hace:

- abre `/ws/user-spectrum`
- envía `ping`, `get_status`, `zoom`, `pan`, `reset`
- recibe `config`, `pong`, `error` y binarios `SPEC`

Entradas:

- `baseUrl`
- `userSessionId`
- comandos de zoom/pan

Outputs:

- `SpectrumConfig`
- frames `SPEC`
- eventos de error/conexión

## `AudioPlayer`

Qué hace:

- parsea header binario audio v2
- extrae `opusData`
- decodifica Opus a PCM
- bufferiza
- reproduce con `AudioTrack`

Entradas:

- frames binarios de `/ws`
- volumen/mute

Outputs:

- audio reproducido
- métricas locales de buffer
- estado `playing/buffering/error`
- `basebandPower` y `noiseDensity` hacia UI

## `SpectrumRenderer`

Qué hace:

- mantiene estado de `config`
- parsea `SPEC`
- aplica full/delta
- expone bitmap o buffer renderizable

Entradas:

- `SpectrumConfig`
- frames `SPEC`

Outputs:

- estado de waterfall listo para dibujar
- frecuencia visible y escalas

## `RadioViewModel`

Qué hace:

- fuente única de verdad para UI
- coordina sesión, audio, spectrum y acciones del usuario

Entradas:

- acciones UI
- eventos de repositorios

Outputs:

- `StateFlow<RadioUiState>`
- eventos de error

## `LocalSettingsStore`

Qué hace:

- guarda y recupera settings y memorias locales

Entradas:

- host
- última frecuencia/modo/filtro
- volumen
- step
- memorias

Outputs:

- flows observables de config local

# 5. Flujo de arranque de la app

Secuencia exacta:

1. abrir app
2. cargar config local desde `DataStore`
3. cargar `baseUrl`, última frecuencia, último modo, último filtro, volumen y step
4. generar `user_session_id` si no existe sesión activa válida
5. llamar `GET /api/description`
6. si no hay estado local previo usable, usar `default_frequency` y `default_mode`
7. llamar `GET /api/bands`
8. hacer `POST /connection`
9. si `allowed=false`, mostrar error y no abrir WS
10. abrir WS audio `/ws`
11. abrir WS spectrum `/ws/user-spectrum`
12. esperar `config` del spectrum
13. inicializar `AudioPlayer`
14. enviar `tune` inicial completo:
    - `frequency`
    - `mode`
    - `bandwidthLow`
    - `bandwidthHigh`
15. pasar a estado listo

Regla práctica:

- no marcar la radio como “lista” hasta tener WS audio abierto y `config` de spectrum recibido

# 6. Flujo de sintonía

## Tap en waterfall

1. convertir coordenada X a frecuencia usando:
   - `centerFreq`
   - `totalBandwidth`
   - ancho visible del canvas
2. actualizar `tunedFrequencyHz` local
3. enviar `tune` completo

## Drag horizontal

1. convertir delta X a delta de frecuencia
2. aplicar step actual
3. actualizar frecuencia local
4. enviar `tune` con throttle

Regla práctica:

- throttle de `tune` durante drag: `40-80 ms`
- al soltar el dedo: enviar un `tune` final inmediato

## Cambio de modo

1. seleccionar nuevo modo
2. aplicar preset de filtro del modo en cliente
3. enviar `tune` completo

## Cambio de filtro

1. mover tirador izquierdo/derecho
2. actualizar `bandwidthLow/high` local
3. enviar `tune` completo con throttle corto si el gesto es continuo

Reglas para evitar flooding:

- nunca enviar más de un `tune` por frame de UI
- usar un `MutableSharedFlow` o canal interno para coalescer comandos
- si llegan varios cambios rápidos, quedarse con el último

# 7. Audio pipeline

Flujo:

1. `AudioWsClient` recibe binario
2. `AudioPlayer` parsea header v2:
   - `timestampNs`
   - `sampleRate`
   - `channels`
   - `basebandPower`
   - `noiseDensity`
3. extrae `opusData`
4. decodifica Opus a PCM 16-bit o float según wrapper
5. escribe PCM a buffer interno
6. vuelca a `AudioTrack`

Decisiones prácticas:

- formato principal: Opus v2
- sample rate: usar el que llega en header
- `AudioTrack` en modo streaming
- buffer inicial objetivo:
  - empezar en `120-180 ms`
  - bajar luego solo si las pruebas reales lo permiten

Latencia vs estabilidad:

- v1 prioriza estabilidad
- no perseguir latencia mínima a costa de cortes

Manejo de underrun:

- si el buffer cae por debajo del umbral:
  - pasar a estado `buffering`
  - acumular de nuevo un mínimo
  - reanudar

Regla simple:

- mejor una latencia moderada estable que microcortes frecuentes

# 8. Spectrum pipeline

Flujo:

1. `SpectrumWsClient` abre WS con `mode=binary8`
2. recibe `config`
3. inicializa estado:
   - `centerFreq`
   - `binCount`
   - `binBandwidth`
   - `totalBandwidth`
4. recibe frame `SPEC`
5. parsea cabecera
6. si es full:
   - reemplaza buffer completo
7. si es delta:
   - aplica cambios sobre buffer actual
8. genera línea de waterfall
9. renderiza incrementalmente

Regla práctica:

- implementar primero solo flags `0x03` y `0x04` (`binary8`)
- si aparece otro formato, registrar error y no bloquear la app

# 9. Estado y ViewModel

`RadioViewModel` debe exponer como mínimo:

- `frequencyHz`
- `visibleCenterFrequencyHz`
- `mode`
- `bandwidthLowHz`
- `bandwidthHighHz`
- `audioState`
- `spectrumState`
- `signalSnrDb`
- `signalBasebandPowerDb`
- `volume`
- `mute`
- `bands`
- `memories`
- `connectionState`

Acciones mínimas:

- `connect()`
- `reconnect()`
- `disconnect()`
- `tune(frequencyHz)`
- `changeMode(mode)`
- `changeFilter(low, high)`
- `zoom(binBandwidth)`
- `pan(centerFrequencyHz)`
- `selectBand(bandId)`
- `saveMemory(name)`
- `loadMemory(memoryId)`
- `setVolume(value)`
- `toggleMute()`

# 10. UI inicial (mínima)

Pantalla única MVP:

- header
  - frecuencia grande
  - selector de modo
  - S-meter simple
- centro
  - waterfall interactivo
  - marcador de frecuencia sintonizada
  - bordes de filtro
- footer
  - volumen
  - botones de banda
  - acceso a memorias
  - step tuning

Layout lógico:

- arriba: estado y control principal
- centro: instrumento principal de sintonía
- abajo: controles secundarios rápidos

# 11. Gestión de errores

## WS cerrado

Acción:

- marcar desconectado
- relanzar flujo de reconexión ordenado

## Session invalid

Acción:

- regenerar UUID
- repetir `/connection`
- reabrir ambos WS
- restaurar estado local

## Cambio de red

Acción:

- cerrar sesión actual
- regenerar UUID
- reconectar completo

## Audio sin datos

Acción:

- pasar a `buffering`
- pedir `get_status`
- si persiste, recrear solo WS audio

## Spectrum sin config

Acción:

- pedir `get_status`
- si no llega `config`, recrear WS spectrum

Regla general:

- reinicio parcial si falla solo audio o solo spectrum
- reinicio completo si falla la sesión

# 12. Hitos de implementación

Orden recomendado:

1. conexión HTTP `/connection`
2. cliente WS audio con logging
3. reproducción de audio básica con `AudioTrack`
4. cliente WS spectrum con parse de `config`
5. parser `SPEC` y render simple
6. tune básico manual
7. UI mínima Compose
8. interacción de waterfall
9. filtros
10. memorias locales
11. reconexión

# 13. Pruebas manuales

Checklist:

- conecta con servidor válido
- falla de forma clara con servidor inválido
- suena audio
- cambia frecuencia
- cambia modo
- mueve filtro
- recibe `config` de spectrum
- renderiza waterfall
- zoom waterfall
- pan waterfall
- restaura última frecuencia al reabrir la app
- reconecta tras caída de WS
- recupera tras cambio de red

# 14. Decisiones técnicas fijadas

- dos WebSockets separados
- Opus como audio principal
- `AudioTrack` para reproducción
- filtro principal remoto
- sin DSP local de receptor
- waterfall RF remoto
- tune siempre completo
- memorias locales en v1
- `binary8` como formato preferente de spectrum

# 15. Riesgos prácticos

- latencia variable en red móvil
- jitter y underrun de audio
- diferencias menores entre presets por modo según servidor/cliente de referencia
- servidores que no usen exactamente el mismo comportamiento de spectrum
- cambios de red que invaliden la sesión por IP binding

## 5 decisiones técnicas que NO deben cambiar

- usar dos WebSockets separados para audio y spectrum
- tratar el filtro principal como remoto
- usar Opus v2 como camino principal de audio
- enviar siempre `tune` completo
- no introducir DSP local de receptor

## 5 puntos que conviene validar en el primer test real

- estabilidad real del buffer de audio en red móvil
- latencia percibida entre waterfall y audio
- comportamiento real de cambio de modo en `usb/lsb/cwu/am`
- compatibilidad de `binary8` en el servidor objetivo
- preset final de filtro para `cwu` en uso real
