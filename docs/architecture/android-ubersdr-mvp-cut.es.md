# 1. Objetivo del MVP

Construir una primera app Android usable para escuchar HF en receptores UberSDR/ka9q-radio, con sintonía táctil sobre waterfall, audio remoto estable y control básico de modo y filtro, sin reimplementar DSP de receptor en el teléfono.

# 2. Funciones que SÍ entran

## Conexión y sesión

- [Confirmado] Generación local de `user_session_id` UUID.
- [Confirmado] `POST /connection` antes de abrir WebSockets.
- [Confirmado] Apertura de dos WebSockets separados:
  - `/ws` para audio y control
  - `/ws/user-spectrum` para waterfall/spectrum
- [Asumido para MVP] Reintento simple de conexión con backoff corto.
- [Asumido para MVP] Restauración automática de sesión visual tras reconexión si la red no ha cambiado.

## Audio

- [Confirmado] Recepción de audio remoto por `/ws`.
- [Confirmado] Formato principal de MVP: `format=opus&version=2`.
- [Confirmado] Decodificación Opus en Android.
- [Confirmado] Reproducción de audio con volumen final local.
- [Asumido para MVP] Mute local.
- [Asumido para MVP] Buffer/jitter mínimo para reproducción estable.

## Waterfall/spectrum

- [Confirmado] Conexión a `/ws/user-spectrum` con el mismo `user_session_id`.
- [Confirmado] Recepción de mensaje `config`.
- [Confirmado] Parseo de frames binarios `SPEC`.
- [Asumido para MVP] Uso preferente de modo `binary8` para reducir ancho de banda de waterfall.
- [Asumido para MVP] Render waterfall desplazable con indicador de frecuencia central, frecuencia sintonizada y bordes de filtro.

## Sintonía

- [Confirmado] `tune` por WebSocket audio con `frequency`, `mode`, `bandwidthLow`, `bandwidthHigh`.
- [Asumido para MVP] Tap en waterfall para sintonizar.
- [Asumido para MVP] Drag horizontal de frecuencia con step configurable.
- [Asumido para MVP] Campo de frecuencia editable manualmente.
- [Asumido para MVP] Step tuning básico: 10 Hz, 100 Hz, 1 kHz.

## Modos

- [Confirmado] MVP soporta solo:
  - `usb`
  - `lsb`
  - `cwu`
  - `am`
- [Asumido para MVP] `sam`, `fm`, `nfm`, `cwl`, `iq*` quedan fuera.

## Filtros

- [Confirmado] Control remoto mediante `bandwidthLow` y `bandwidthHigh`.
- [Asumido para MVP] Control UI con dos tiradores horizontales o preset slider equivalente.
- [Asumido para MVP] Presets Android explícitos por modo, sin depender de defaults implícitos del backend.
- [Asumido para MVP] Presets iniciales recomendados:
  - `usb`: `50 / 2700`
  - `lsb`: `-2700 / -50`
  - `cwu`: `200 / 800`
  - `am`: `-5000 / 5000`

## S-meter

- [Confirmado] Mostrar S-meter básico a partir de `basebandPower` y/o `snr` derivado de `basebandPower - noiseDensity`.
- [Asumido para MVP] Barra simple con valor numérico opcional.
- [Pendiente de validar] Escala visual exacta y calibración radioaficionada.

## Memorias

- [Asumido para MVP] Memorias locales simples en dispositivo:
  - nombre
  - frecuencia
  - modo
  - `bandwidthLow`
  - `bandwidthHigh`
- [Asumido para MVP] Guardar/cargar/borrar memorias locales.

## Bandas

- [Confirmado] Leer `/api/bands` si está disponible.
- [Asumido para MVP] Si `/api/bands` falla, usar fallback local mínimo de bandas HF amateur comunes.
- [Asumido para MVP] Botones rápidos de banda que ajustan frecuencia por defecto y un zoom inicial razonable del waterfall.

## Ajustes locales mínimos

- [Asumido para MVP] Volumen.
- [Asumido para MVP] Mute.
- [Asumido para MVP] Step de sintonía.
- [Asumido para MVP] Persistencia del último estado:
  - host/base URL
  - última frecuencia
  - último modo
  - último filtro
  - último volumen
  - último step

# 3. Funciones que NO entran en MVP

- [Confirmado] NR, EQ, compresor o low-pass locales avanzados.
- [Confirmado] Auto-tune o detección automática de carrier/borde.
- [Confirmado] DSP local de receptor.
- [Confirmado] Modos IQ (`iq`, `iq48`, `iq96`, `iq192`, `iq384`).
- [Confirmado] Soporte de controles DSP no confirmados en protocolo principal:
  - AGC configurable
  - notch remoto
  - noise blanker
  - CW pitch/BFO offset independiente
  - de-emphasis
- [Asumido para MVP] Bookmarks remotos avanzados.
- [Asumido para MVP] Sincronización remota de memorias.
- [Asumido para MVP] Vista dual, overlays complejos o gráficos accesorios.
- [Asumido para MVP] Estadísticas, chat, extensiones web, paneles de decoders.
- [Asumido para MVP] Cambio de salida de audio compleja, EQ por dispositivo o grabación.

# 4. Requisitos mínimos de backend para MVP

Endpoints obligatorios:

- [Confirmado] `POST /connection`
- [Confirmado] `GET /api/description`
- [Confirmado] `GET /api/bands`
- [Confirmado] `GET /api/bookmarks` no es imprescindible para MVP
- [Confirmado] `WS /ws`
- [Confirmado] `WS /ws/user-spectrum`

Mensajes y formatos obligatorios:

- [Confirmado] `/connection` debe aceptar `user_session_id`.
- [Confirmado] `/ws` debe aceptar:
  - `frequency`
  - `mode`
  - `user_session_id`
  - `format=opus`
  - `version=2`
  - opcionalmente `bandwidthLow`, `bandwidthHigh`
- [Confirmado] `/ws` debe soportar:
  - `tune`
  - `ping`
  - `get_status`
- [Asumido para MVP] `set_squelch` queda fuera del MVP inicial, aunque backend lo soporte.
- [Confirmado] `/ws/user-spectrum` debe soportar:
  - `ping`
  - `get_status`
  - `zoom`
  - `pan`
  - `reset`
- [Confirmado] Audio binario Opus v2.
- [Confirmado] Spectrum binario `SPEC`.
- [Confirmado] Mensaje `config` inicial para spectrum.

Campos backend mínimos realmente necesarios:

- [Confirmado] En `/api/description`:
  - `default_frequency`
  - `default_mode`
  - `max_session_time`
  - `receiver.name` o equivalente presentable
- [Confirmado] En `/connection`:
  - `allowed`
  - `reason`
  - `session_timeout`
  - `max_session_time`
  - `bypassed`
- [Confirmado] En spectrum `config`:
  - `centerFreq`
  - `binCount`
  - `binBandwidth`
  - `totalBandwidth`
  - `sessionId`

# 5. Requisitos mínimos Android para MVP

- decodificación Opus compatible con el framing binario v2 del backend
- dos WebSockets simultáneos
- parser del protocolo `SPEC`
- renderer waterfall eficiente
- estado local de radio y sesión
- persistencia mínima con `DataStore` o equivalente
- reproducción de audio con baja latencia
- cola/buffer para evitar cortes breves
- gestión básica de reconexión

Capas mínimas recomendadas:

- `SessionRepository`
- `AudioWsClient`
- `SpectrumWsClient`
- `AudioPlayer`
- `SpectrumRenderer`
- `RadioViewModel`
- `LocalSettingsStore`

# 6. Riesgos y mitigaciones

## Latencia

Riesgo:

- audio y waterfall pueden sentirse desacompasados o lentos en red móvil.

Mitigación:

- usar Opus v2 como camino principal
- usar waterfall `binary8`
- mantener buffer de audio pequeño pero no agresivo

## Reconexión

Riesgo:

- caída de uno de los dos WebSockets deja UI en estado inconsistente.

Mitigación:

- modelar audio y spectrum por separado
- reconectar primero `/connection`, luego audio, luego spectrum
- no considerar “conectado” hasta recibir audio abierto y `config` de spectrum

## Cambios de modo

Riesgo:

- el backend hace recarga de preset y luego re-aplica filtro; si la UI asume instantaneidad total puede mostrar un estado incorrecto unos cientos de ms.

Mitigación:

- tratar el cambio de modo como operación remota
- mantener spinner/estado transitorio corto
- reenviar `tune` completo con modo + filtro deseado

## Sincronización waterfall/frecuencia

Riesgo:

- el usuario hace zoom/pan y la frecuencia visible ya no coincide con la frecuencia sintonizada.

Mitigación:

- separar en estado local:
  - frecuencia sintonizada
  - frecuencia central visible del waterfall
- nunca derivar una desde la otra sin evento explícito

## Gestión de sesión

Riesgo:

- `user_session_id` inválido, rebinding IP o cierre por timeout.

Mitigación:

- si `/connection` o los WS devuelven error de sesión, regenerar UUID y rehacer flujo completo
- mostrar mensaje claro y recuperar desde estado local persistido

# 7. Definición final de “MVP usable”

Checklist mínima:

- [ ] introducir URL/base del servidor y conectar
- [ ] obtener `default_frequency` y `default_mode`
- [ ] escuchar audio estable en `usb`, `lsb`, `cwu` y `am`
- [ ] ver waterfall funcional y hacer zoom/pan básicos
- [ ] sintonizar tocando waterfall o editando frecuencia
- [ ] cambiar modo sin romper audio ni UI
- [ ] mover filtro y oír el cambio real
- [ ] ver un S-meter básico
- [ ] guardar y restaurar memorias locales
- [ ] cambiar rápidamente entre bandas HF principales

Nota de corte duro:

- Si una función no mejora directamente el flujo “abrir app -> ver waterfall -> sintonizar -> escuchar -> guardar memoria”, queda fuera del MVP.

Inconsistencia menor detectada y decisión para MVP:

- [Pendiente de validar] Hay diferencias menores entre presets CW por defecto en backend y clientes de referencia.
- [Decisión MVP] Android enviará siempre `bandwidthLow` y `bandwidthHigh` explícitos en cada `tune` relevante, y no dependerá de defaults implícitos del servidor para CW.
