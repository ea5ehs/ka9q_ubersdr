# 1. Visión general

En v2 del plan se mantiene el mismo MVP:

- conexión a backend UberSDR
- WS audio
- WS spectrum
- reproducción de audio remoto
- waterfall interactivo
- tune, cambio de modo y filtro
- memorias locales simples si se activan más adelante

Arquitectura general:

- backend = demodulación, filtro remoto, waterfall RF, sesión
- cliente Android = control, render, reproducción y UX

Componentes principales:

- sesión/conexión
- audio WS
- spectrum WS
- `AudioPlayer`
- render de waterfall
- `RadioViewModel`
- capa de presentación separada de UI

# 2. Decisiones revisadas para v2

## Package name definitivo

Package base por defecto:

- `es.niceto.ubersdr`

No usar:

- `com.example.*`
- nombres temporales o de muestra

## Estructura presentation/ui

Se reorganiza para separar mejor Compose, estado y lógica de pantalla.

Estructura recomendada:

```text
es.niceto.ubersdr

- app/
- model/
- data/
  - network/
  - websocket/
- session/
- audio/
- spectrum/
- presentation/
  - radio/
    - RadioViewModel
    - RadioUiState
    - RadioAction
  - common/
- ui/
  - radio/
    - RadioScreen
    - components/
```

Decisión:

- `presentation/` contiene `ViewModel`, estado observable y acciones
- `ui/` contiene Compose puro
- no meter `RadioViewModel` dentro de `ui/`

## Estrategia de render del waterfall

Opción elegida para v1:

- mantener un `Bitmap` mutable en memoria
- dibujar cada nueva línea del waterfall con `Canvas` Android estándar
- mostrarlo en Compose mediante `ImageBitmap`/`Canvas`

Por qué encaja mejor:

- es la opción más simple de programar
- usa APIs estándar Android
- funciona en cualquier móvil razonable
- es fácil de depurar con logs, breakpoints y capturas
- evita introducir OpenGL, shaders, Surface complejas o render nativo adicional

Qué complejidad evita:

- pipeline GPU específico
- sincronización rara entre hilos de render
- problemas de compatibilidad por drivers
- depuración difícil de frames o texturas

Limitaciones:

- no será la opción más eficiente para waterfall muy agresivo
- puede requerir optimización posterior si aumentan mucho resolución o FPS

Decisión práctica:

- para MVP se prioriza simplicidad y fiabilidad sobre sofisticación gráfica

## Política de persistencia en v1

Regla principal:

- el MVP debe funcionar completamente sin persistencia

Decisión:

- no acoplar el arranque a `DataStore`
- no cargar estado persistido antes de conectar audio/spectrum
- no bloquear nunca el flujo principal por settings o memorias

Política concreta:

- fase inicial: persistencia desactivada o mínima
- si se activa algo, debe ser:
  - opcional
  - aislado
  - fácil de deshabilitar
  - no bloqueante

Qué sí se puede guardar más adelante sin riesgo:

- `baseUrl`
- volumen
- step de sintonía

Qué no debe condicionar el arranque:

- memorias
- último estado de radio
- settings de UI

## Fase actual: bootstrap HTTP

Estado actual confirmado en `android-client`:

1. `POST /connection`
2. `GET /api/description`

En esta fase:

- todavía no se abren WebSockets
- todavía no hay audio activo
- todavía no hay spectrum activo

# 3. Stack técnico recomendado

- lenguaje: Kotlin
- UI: Jetpack Compose
  - sigue siendo la opción correcta para pantalla única y estado reactivo
- networking HTTP: Retrofit + OkHttp
  - Retrofit opcional, pero práctico para `/connection`, `/api/description`, `/api/bands`
- WebSocket: OkHttp WebSocket
  - suficiente y estándar
- audio: `AudioTrack`
  - mantiene el mejor encaje para streaming PCM controlado
- decoder Opus: wrapper JNI sobre `libopus`
  - elección práctica para el framing custom del backend
- persistencia:
  - v1 inicial: desactivada o muy mínima
  - si se usa, que sea `DataStore`, pero fuera del camino crítico
- concurrencia: coroutines + Flow

# 4. Estructura de paquetes

```text
es.niceto.ubersdr

- app/
- model/
- data/
  - network/
  - websocket/
- session/
- audio/
- spectrum/
- presentation/
  - radio/
  - common/
- ui/
  - radio/
    - components/
```

Distribución concreta:

- `model/`
  - DTOs HTTP/WS
  - modelos de estado
- `data/network/`
  - APIs HTTP
  - mappers
- `data/websocket/`
  - `AudioWsClient`
  - `SpectrumWsClient`
  - parsers JSON/binario
- `session/`
  - UUID
  - coordinación de conexión/reconexión
- `audio/`
  - parser audio v2
  - decoder Opus
  - buffer
  - `AudioTrack`
- `spectrum/`
  - parser `SPEC`
  - estado de waterfall
  - renderer basado en `Bitmap`
- `presentation/radio/`
  - `RadioViewModel`
  - `RadioUiState`
  - acciones y reducers simples
- `ui/radio/`
  - `RadioScreen`
  - controles Compose

# 5. Componentes principales

## `SessionRepository`

Hace:

- coordina `POST /connection`
- coordina `GET /api/description`
- crea y recrea `user_session_id`
- en fases posteriores, orquesta audio WS + spectrum WS

Entradas:

- `baseUrl`
- comandos `connect/reconnect/disconnect`

Outputs:

- estado global de conexión
- errores de sesión

## `ConnectionService`

Hace:

- `/connection`
- `/api/description`
- `/api/bands`

Entradas:

- `baseUrl`
- `userSessionId`

Outputs:

- respuestas HTTP parseadas

## `AudioWsClient`

Hace:

- abre `/ws`
- envía `tune`, `ping`, `get_status`
- emite binarios y eventos JSON

Entradas:

- query inicial
- comandos de tune

Outputs:

- frames de audio
- eventos de conexión/error

## `SpectrumWsClient`

Hace:

- abre `/ws/user-spectrum`
- envía `ping`, `get_status`, `zoom`, `pan`, `reset`
- emite `config` y frames `SPEC`

Entradas:

- `userSessionId`
- acciones de zoom/pan

Outputs:

- `SpectrumConfig`
- líneas/frames de spectrum

## `AudioPlayer`

Hace:

- parsea audio v2
- decodifica Opus
- escribe a `AudioTrack`

Entradas:

- binario WS
- volumen/mute

Outputs:

- audio reproducido
- `AudioPlaybackState`
- métricas de señal

## `SpectrumRenderer`

Hace:

- mantiene un buffer de bins
- aplica full/delta
- pinta líneas en un `Bitmap` desplazable

Entradas:

- `SpectrumConfig`
- frames `SPEC`

Outputs:

- imagen de waterfall
- estado visible de frecuencia/zoom

## `RadioViewModel`

Hace:

- estado observable único de la pantalla
- traduce acciones UI en comandos de sesión/audio/spectrum

Entradas:

- acciones de usuario
- eventos de repositorios

Outputs:

- `StateFlow<RadioUiState>`

## `LocalSettingsStore`

Estado en v2:

- no forma parte del camino crítico
- puede no existir en la primera fase de implementación

Si se crea:

- debe ser opcional
- no debe bloquear arranque ni reconexión

# 6. Flujo de arranque de la app

Secuencia revisada:

1. abrir app
2. tomar `baseUrl` actual desde configuración en memoria o valor simple no bloqueante
3. generar `user_session_id`
4. llamar `GET /api/description`
5. usar `default_frequency` y `default_mode`
6. llamar `GET /api/bands`
7. hacer `POST /connection`
8. abrir WS audio
9. abrir WS spectrum
10. esperar `config` de spectrum
11. inicializar `AudioPlayer`
12. enviar `tune` inicial completo
13. pasar a listo

Regla nueva importante:

- el arranque no depende de persistencia

# 7. Flujo de sintonía

Se mantiene:

- tap -> cálculo de frecuencia -> `tune` completo
- drag -> throttle -> `tune`
- cambio de modo -> preset local de filtro -> `tune` completo
- cambio de filtro -> `tune` completo

Reglas prácticas:

- throttle durante drag: `50-80 ms`
- `tune` final inmediato al terminar gesto
- coalescer mensajes y quedarse con el último

# 8. Audio pipeline

Se mantiene:

1. recibir binario WS
2. parsear header v2
3. extraer `opusData`
4. decodificar Opus
5. bufferizar
6. reproducir con `AudioTrack`

Decisiones prácticas:

- buffer inicial conservador: `150-200 ms`
- priorizar estabilidad
- si hay underrun:
  - pasar a `buffering`
  - rellenar
  - reanudar

# 9. Spectrum pipeline

Se mantiene:

1. abrir WS spectrum en `binary8`
2. parsear `config`
3. parsear `SPEC`
4. aplicar full/delta
5. convertir bins a línea de color
6. desplazar waterfall en `Bitmap`
7. invalidar render Compose

Decisión práctica de implementación:

- una línea nueva por frame de spectrum
- scroll vertical simple dentro del `Bitmap`
- sin GPU específica en v1

# 10. Estado y ViewModel

`RadioViewModel` debe exponer:

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
- `connectionState`

Estado opcional, no crítico:

- memorias
- preferencias locales

Regla:

- si la persistencia falla, el `ViewModel` sigue funcionando igual

# 11. UI inicial (mínima)

Pantalla única MVP:

- header
  - frecuencia
  - modo
  - S-meter
- centro
  - waterfall
  - cursor de sintonía
  - bordes de filtro
- footer
  - volumen
  - selector de modo
  - bandas
  - acceso simple a memorias si están activadas

# 12. Gestión de errores

Casos y acción:

- WS cerrado
  - reconectar ese WS
- session invalid
  - regenerar UUID y rehacer flujo completo
- cambio de red
  - regenerar UUID y reconectar completo
- audio sin datos
  - recrear audio WS si persiste
- spectrum sin config
  - recrear spectrum WS si persiste

Regla:

- nunca bloquear audio/spectrum esperando estado local persistido

# 13. Hitos de implementación

Orden revisado:

1. `POST /connection`
2. WS audio + logging
3. `AudioTrack` + decode Opus
4. WS spectrum + `config`
5. parser `SPEC`
6. renderer waterfall con `Bitmap`
7. `RadioViewModel` y estado observable
8. UI Compose mínima
9. tune manual
10. interacción waterfall
11. filtros
12. reconexión
13. persistencia opcional y aislada, solo si todo lo anterior es estable

# 14. Cambios respecto a v1

- package name actualizado a `es.niceto.ubersdr`
- separación más limpia entre `presentation/` y `ui/`
- decisión cerrada de render de waterfall con `Bitmap` + `Canvas` Android estándar
- persistencia sacada del camino crítico
- hitos reordenados para no depender de settings o memorias

# 15. Reglas de implementación que no se deben romper

- no introducir DSP local de receptor
- no acoplar persistencia al arranque
- no bloquear audio o spectrum por estado local
- no rediseñar el contrato backend-Android
- mantener `tune` siempre completo
- mantener dos WebSockets separados
- no meter rendering sofisticado de waterfall en v1
- no hacer depender la estabilidad del audio de memorias o settings

## 5 decisiones técnicas que NO deben cambiar

- `es.niceto.ubersdr` como package base
- `presentation/` separado de `ui/`
- waterfall con `Bitmap` mutable + `Canvas` estándar
- persistencia fuera del camino crítico
- `tune` completo siempre

## 5 puntos que conviene validar en el primer test real

- estabilidad del render waterfall en móviles medios
- estabilidad del buffer de audio con red móvil real
- comportamiento de `binary8` en el servidor objetivo
- fluidez de drag+tune con throttle elegido
- preset final de filtro para `cwu`
