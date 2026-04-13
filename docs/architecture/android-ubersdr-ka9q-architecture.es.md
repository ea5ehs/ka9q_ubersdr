# 1. Resumen ejecutivo

Conclusión principal:

- [Confirmado por código] La app Android no debe reimplementar demodulación, AGC de receptor, filtrado de canal, generación de waterfall RF ni gestión real del canal SDR. Esa lógica ya vive en el backend `ka9q-radio`/`radiod` y UberSDR la expone y la orquesta.
- [Confirmado por código] La app Android sí debe actuar principalmente como cliente de control + reproducción + visualización: abrir sesión, conectar WebSocket de audio, conectar WebSocket de spectrum/waterfall, enviar `tune`/`set_squelch`, renderizar waterfall y mantener estado UX local.
- [Confirmado por código] El backend entrega audio ya demodulado y listo para reproducción en formatos cliente (`opus` o `pcm-zstd`) por `/ws`; el waterfall llega por un WebSocket separado `/ws/user-spectrum`.
- [Confirmado por código] UberSDR añade la capa que faltaría entre radiod y una app móvil: sesión, validación, límites, bindings por IP/UUID, bookmarks, bands, descripción del receptor y WebSockets amigables para cliente.

Qué debe hacer la app Android:

- reproducción de audio remoto
- control de frecuencia, modo, `bandwidthLow`, `bandwidthHigh`
- visualización waterfall/spectrum
- ergonomía móvil: gestos, taps, snap, memorias, bandas, favoritos, háptica, indicadores
- estado local de UI y persistencia ligera de preferencias del usuario

Qué NO debe hacer la app Android:

- demodular IQ a audio
- rehacer filtros DSP del canal principal
- implementar AGC “de receptor” sobre la señal SDR como si el móvil fuera el demodulador
- generar el spectrum RF desde muestras IQ locales para el caso normal de escucha
- gestionar por su cuenta el ciclo de vida real del canal radiod

Riesgos principales de mala arquitectura:

- duplicar AGC/NR/notch como si fuesen parte del receptor cuando hoy no forman parte del protocolo principal de audio
- mezclar UX local de audio final con DSP remoto de canal
- intentar un solo WebSocket para todo cuando el sistema real separa audio y spectrum
- asumir que `bandwidth` es un postfiltro local cuando en realidad son bordes de filtro de canal enviados a backend

# 2. Arquitectura observada

## 2.1 ka9q-radio

Papel real:

- [Confirmado por código] `radiod` es el motor SDR remoto. UberSDR le crea canales, cambia frecuencia/modo/filtro, recibe audio RTP y obtiene datos de spectrum/estado.

Evidencias:

- [radiod.go](/home/ni/projects/ka9q_ubersdr/radiod.go:286) `CreateChannelWithSquelch(...)`: crea canal en radiod con frecuencia, preset/modo, sample rate, SSRC y squelch.
- [radiod.go](/home/ni/projects/ka9q_ubersdr/radiod.go:475) `UpdateChannelWithSquelch(...)`: actualiza frecuencia, preset y bordes de filtro `LOW_EDGE/HIGH_EDGE`.
- [radiod.go](/home/ni/projects/ka9q_ubersdr/radiod.go:357) `CreateSpectrumChannel(...)`: crea canal de spectrum en preset `spectrum`.
- [radiod.go](/home/ni/projects/ka9q_ubersdr/radiod.go:415) `UpdateSpectrumChannel(...)`: cambia `RADIO_FREQUENCY`, `BIN_COUNT`, `NONCOHERENT_BIN_BW` y bordes de filtro del spectrum.
- [audio.go](/home/ni/projects/ka9q_ubersdr/audio.go:17) `AudioPacket`: UberSDR recibe de radiod `PCMData`, `SampleRate`, `RTPTimestamp`, `GPSTimeNs`.
- [user_spectrum.go](/home/ni/projects/ka9q_ubersdr/user_spectrum.go:236) procesamiento de respuestas de STATUS multicast de radiod para spectrum.

Lectura arquitectónica:

- [Confirmado por código] ka9q-radio ya hace la demodulación, el filtrado de canal, la entrega de audio base del receptor y la generación del spectrum RF.
- [Confirmado por código] El estado RF útil del canal existe en backend: `Frequency`, `Preset`, `FilterLow`, `FilterHigh`, `OutputSamprate`, `BasebandPower`, `NoiseDensity` en [radiod_channels_api.go](/home/ni/projects/ka9q_ubersdr/radiod_channels_api.go:260).

## 2.2 UberSDR

Papel real:

- [Confirmado por código] UberSDR es la capa de servicio hacia clientes web/nativos. Expone HTTP y WebSocket, crea y destruye sesiones, traduce control del cliente a comandos radiod y convierte audio interno a formatos de cliente.

Evidencias:

- [main.go](/home/ni/projects/ka9q_ubersdr/main.go:1745) registra `POST /connection`.
- [main.go](/home/ni/projects/ka9q_ubersdr/main.go:1748) registra `/ws` para audio.
- [main.go](/home/ni/projects/ka9q_ubersdr/main.go:1750) registra `/ws/user-spectrum` para waterfall/spectrum.
- [main.go](/home/ni/projects/ka9q_ubersdr/main.go:2304) `handleConnectionCheck(...)`: admisión, rate limit, password bypass, límites de sesión, `allowed_iq_modes`, bind UUID-IP y User-Agent.
- [session.go](/home/ni/projects/ka9q_ubersdr/session.go:196) `CreateSessionWithBandwidthAndPassword(...)`: crea sesión de audio asociada a un canal radiod.
- [session.go](/home/ni/projects/ka9q_ubersdr/session.go:451) `CreateSpectrumSessionWithUserIDAndPassword(...)`: crea sesión separada de spectrum para el mismo `user_session_id`.
- [websocket.go](/home/ni/projects/ka9q_ubersdr/websocket.go:337) `HandleWebSocket(...)`: valida query, comprueba sesión y crea el flujo de audio.
- [user_spectrum_websocket.go](/home/ni/projects/ka9q_ubersdr/user_spectrum_websocket.go:107) `HandleSpectrumWebSocket(...)`: valida y crea el flujo de spectrum.

Qué añade UberSDR por encima de radiod:

- sesión de usuario y seguridad básica
- binding `user_session_id` <-> IP <-> User-Agent
- formatos de audio cliente `opus` y `pcm-zstd`
- endpoints de metadatos: `/api/description`, `/api/bookmarks`, `/api/bands`
- UI web y clientes de referencia
- separación limpia entre WS de audio y WS de spectrum

## 2.3 Cliente actual

Papel real:

- [Confirmado por código] El cliente web actual es mayoritariamente un cliente de control y reproducción. No reemplaza el receptor. Añade UX local y algunos postprocesados de audio de navegador.

Evidencias:

- [static/minimal-radio.js](/home/ni/projects/ka9q_ubersdr/static/minimal-radio.js:278) abre `/ws?...format=opus&version=2`.
- [static/minimal-radio.js](/home/ni/projects/ka9q_ubersdr/static/minimal-radio.js:329) envía `tune` con `mode`, `bandwidthLow`, `bandwidthHigh`.
- [static/minimal-radio.js](/home/ni/projects/ka9q_ubersdr/static/minimal-radio.js:223) tiene volumen local.
- [static/minimal-radio.js](/home/ni/projects/ka9q_ubersdr/static/minimal-radio.js:658) abre spectrum por un WS separado.
- [static/app.js](/home/ni/projects/ka9q_ubersdr/static/app.js:111) inicializa `squelch`, `compressor`, `lowpass filter`, `equalizer` y `noise reduction` como procesamiento local de reproducción/UX en navegador.
- [static/carrier-detector.js](/home/ni/projects/ka9q_ubersdr/static/carrier-detector.js:1) implementa detección visual/local de carrier o borde espectral para autoajustes UI, no como parte del protocolo principal.

Lectura arquitectónica:

- [Confirmado por código] El cliente actual ya demuestra el modelo correcto: remoto para SDR/canal; local para reproducción, visualización y ergonomía.
- [Inferido] Algunas funciones de la web actual como EQ/NR del navegador son opcionales de escucha final y no deben confundirse con DSP del receptor.

# 3. Flujo real de conexión y control

## 3.1 Handshake

Secuencia observada:

1. [Confirmado por código] El cliente genera un UUID `user_session_id`.
2. [Confirmado por código] Hace `POST /connection` con JSON `{"user_session_id":"...","password":"..."}`.
3. [Confirmado por código] El backend valida método, body, UUID, límites, baneos, bypass password, máximos de sesión e IP.
4. [Confirmado por código] Si acepta, devuelve `allowed`, `session_timeout`, `max_session_time`, `bypassed`, `allowed_iq_modes` y liga UUID a IP/User-Agent.
5. [Confirmado por código] Después el cliente puede abrir `/ws` y `/ws/user-spectrum` usando el mismo `user_session_id`.

Evidencias:

- request/response structs en [main.go](/home/ni/projects/ka9q_ubersdr/main.go:2287)
- validación y binding en [main.go](/home/ni/projects/ka9q_ubersdr/main.go:2304)

Ejemplo real reconstruido desde código:

```json
POST /connection
{
  "user_session_id": "550e8400-e29b-41d4-a716-446655440000",
  "password": "optional"
}
```

```json
{
  "client_ip": "203.0.113.10",
  "allowed": true,
  "session_timeout": 0,
  "max_session_time": 0,
  "bypassed": true,
  "allowed_iq_modes": ["iq48", "iq96", "iq192", "iq384"]
}
```

## 3.2 Sesión/canal

- [Confirmado por código] Audio y spectrum son sesiones separadas, pero comparten `user_session_id`.
- [Confirmado por código] `SessionManager` mantiene un mapa UUID->audio session y UUID->spectrum session en [session.go](/home/ni/projects/ka9q_ubersdr/session.go:89).
- [Confirmado por código] La sesión de audio crea un canal radiod dedicado con SSRC propio.
- [Confirmado por código] El retune normal reutiliza la misma sesión/canal; no recrea el canal salvo casos de reconexión o cierre.

Evidencias:

- `Session` en [session.go](/home/ni/projects/ka9q_ubersdr/session.go:23)
- audio session en [session.go](/home/ni/projects/ka9q_ubersdr/session.go:196)
- spectrum session en [session.go](/home/ni/projects/ka9q_ubersdr/session.go:451)
- comentario de reutilización de canal en [websocket.go](/home/ni/projects/ka9q_ubersdr/websocket.go:706)

## 3.3 Audio

- [Confirmado por código] El audio llega por WebSocket `/ws`.
- [Confirmado por código] El backend recibe PCM de radiod y lo vuelve a empaquetar para cliente como `opus` o `pcm-zstd`.
- [Confirmado por código] El audio ya está demodulado; el cliente no recibe IQ en el caso normal de escucha.
- [Confirmado por código] El protocolo de audio binario está documentado también en [android-audio-protocol.en.md](/home/ni/projects/ka9q_ubersdr/docs/architecture/android-audio-protocol.en.md:1).

Evidencias:

- suscripción a audio radiod en [websocket.go](/home/ni/projects/ka9q_ubersdr/websocket.go:650)
- streaming/recodificación en [websocket.go](/home/ni/projects/ka9q_ubersdr/websocket.go:980)
- `AudioPacket.PCMData` en [audio.go](/home/ni/projects/ka9q_ubersdr/audio.go:17)

Secuencia típica:

1. cliente abre `/ws?frequency=...&mode=...&format=opus&version=2&user_session_id=...`
2. backend valida y crea sesión
3. backend aplica `bandwidthLow/bandwidthHigh`
4. backend suscribe audio de radiod
5. backend envía binarios de audio por WS
6. cliente decodifica Opus o descomprime PCM-zstd y reproduce

## 3.4 Waterfall/spectrum

- [Confirmado por código] El waterfall/spectrum va por `/ws/user-spectrum`, separado del audio.
- [Confirmado por código] Usa el mismo `user_session_id`.
- [Confirmado por código] El backend crea un canal radiod en modo `spectrum`.
- [Confirmado por código] Los datos de spectrum se envían como binario `SPEC` con frames full/delta y variantes `float32` o `uint8`.

Evidencias:

- handler WS en [user_spectrum_websocket.go](/home/ni/projects/ka9q_ubersdr/user_spectrum_websocket.go:107)
- canal spectrum en [session.go](/home/ni/projects/ka9q_ubersdr/session.go:581)
- formato `SPEC` en [user_spectrum_websocket.go](/home/ni/projects/ka9q_ubersdr/user_spectrum_websocket.go:498)
- mensaje `config` inicial en [user_spectrum_websocket.go](/home/ni/projects/ka9q_ubersdr/user_spectrum_websocket.go:839)

Mensajes cliente->servidor observados:

```json
{"type":"ping"}
{"type":"get_status"}
{"type":"reset"}
{"type":"zoom","frequency":7100000,"binBandwidth":25}
{"type":"pan","frequency":7105000}
```

Mensaje de config observado:

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

## 3.5 Estado y eventos

Audio:

- [Confirmado por código] `pong`
- [Confirmado por código] `status`
- [Confirmado por código] `error`
- [Confirmado por código] `squelch_updated`

Evidencias:

- `ServerMessage` en [websocket.go](/home/ni/projects/ka9q_ubersdr/websocket.go:320)
- `sendStatus` en [websocket.go](/home/ni/projects/ka9q_ubersdr/websocket.go:1391)
- `sendError` en [websocket.go](/home/ni/projects/ka9q_ubersdr/websocket.go:1404)

Spectrum:

- [Confirmado por código] `config`
- [Confirmado por código] `pong`
- [Confirmado por código] `error`
- [Confirmado por código] binarios `SPEC`

Evidencias:

- `sendStatus` spectrum en [user_spectrum_websocket.go](/home/ni/projects/ka9q_ubersdr/user_spectrum_websocket.go:839)

# 4. Inventario de controles y DSP

| función | existe en ka9q-radio | existe en UberSDR | visible en UI cliente actual | accesible por protocolo | evidencia exacta | recomendación para app Android |
|---|---|---|---|---|---|---|
| sintonía/frequency | Sí | Sí | Sí | Sí | [radiod.go:286](\/home\/ni\/projects\/ka9q_ubersdr\/radiod.go:286), [websocket.go:309](\/home\/ni\/projects\/ka9q_ubersdr\/websocket.go:309) | remoto |
| modo `usb/lsb/am/sam/fm/nfm/cwu/cwl/iq` | Sí, como preset | Sí | Sí | Sí | [radiod.go:525](\/home\/ni\/projects\/ka9q_ubersdr\/radiod.go:525), [websocket.go:441](\/home\/ni\/projects\/ka9q_ubersdr\/websocket.go:441) | remoto |
| `bandwidthLow` | Sí, low edge | Sí | Sí | Sí | [radiod.go:490](\/home\/ni\/projects\/ka9q_ubersdr\/radiod.go:490), [websocket.go:309](\/home\/ni\/projects\/ka9q_ubersdr\/websocket.go:309) | remoto |
| `bandwidthHigh` | Sí, high edge | Sí | Sí | Sí | [radiod.go:491](\/home\/ni\/projects\/ka9q_ubersdr\/radiod.go:491), [websocket.go:309](\/home\/ni\/projects\/ka9q_ubersdr\/websocket.go:309) | remoto |
| squelch open/close | Sí | Sí | Sí, al menos en web completa | Sí | [radiod.go:543](\/home\/ni\/projects\/ka9q_ubersdr\/radiod.go:543), [websocket.go:922](\/home\/ni\/projects\/ka9q_ubersdr\/websocket.go:922) | remoto |
| demodulación | Sí | La usa, no la reimplementa | No como control DSP detallado | No como control granular | canal radiod con `PRESET` en [radiod.go:301](\/home\/ni\/projects\/ka9q_ubersdr\/radiod.go:301) | no implementar |
| filtro de canal RF/audio demodulado | Sí | Sí, expuesto como low/high edges | Sí | Sí | [radiod.go:490](\/home\/ni\/projects\/ka9q_ubersdr\/radiod.go:490), [websocket.go:807](\/home\/ni\/projects\/ka9q_ubersdr\/websocket.go:807) | remoto |
| AGC del receptor | No confirmado directamente como control expuesto | No expuesto | No visible como parámetro backend | No en protocolo principal | no aparece en [websocket.go](\/home\/ni\/projects\/ka9q_ubersdr\/websocket.go:309) ni en handlers HTTP/WS revisados | no implementar |
| gain/threshold/headroom/hang/recovery/attack | No confirmado | No confirmado | No visible como control remoto | No | sin evidencia en protocolo principal | no implementar |
| notch remoto | No confirmado | No confirmado | No como control remoto | No | sin evidencia en WS audio/spectrum | no implementar |
| NR remoto | No confirmado | No confirmado | La web tiene NR local | No como control backend | NR local en [static/app.js:127](\/home\/ni\/projects\/ka9q_ubersdr\/static\/app.js:127) | local opcional |
| NB remoto | No confirmado | No confirmado | No confirmado | No | sin evidencia en protocolo principal | no implementar |
| de-emphasis | No confirmado | No confirmado | No confirmado | No | sin evidencia clara | no implementar |
| PLL / AM sync | `sam` existe como modo | Sí vía modo | Sí si la UI usa `sam` | Sí, como `mode="sam"` | [websocket.go:441](\/home\/ni\/projects\/ka9q_ubersdr\/websocket.go:441) | remoto |
| CW pitch / BFO offset / shift | No confirmado como parámetro independiente | No expuesto | No visible como control backend | No | solo modo `cwu/cwl`; no hay campo en `ClientMessage` | no implementar |
| volumen final | No | No | Sí | No remoto | [static/minimal-radio.js:223](\/home\/ni\/projects\/ka9q_ubersdr\/static\/minimal-radio.js:223) | local |
| mute | No | No | Sí en web | No remoto | estado local de audio en [static/app.js:219](\/home\/ni\/projects\/ka9q_ubersdr\/static\/app.js:219) | local |
| audio EQ/compresor/lowpass | No como parte del receptor expuesto | No | Sí, local navegador | No remoto | [static/app.js:111](\/home\/ni\/projects\/ka9q_ubersdr\/static\/app.js:111) | local opcional |
| S-meter / señal | radiod expone `BasebandPower` y `NoiseDensity` | Sí | Sí | Sí, embebido en audio v2 y radiod status | [websocket.go:1278](\/home\/ni\/projects\/ka9q_ubersdr\/websocket.go:1278), [radiod_channels_api.go:263](\/home\/ni\/projects\/ka9q_ubersdr\/radiod_channels_api.go:263) | mixto |
| waterfall/spectrum RF | Sí | Sí | Sí | Sí | [user_spectrum_websocket.go:498](\/home\/ni\/projects\/ka9q_ubersdr\/user_spectrum_websocket.go:498) | remoto |
| zoom/pan waterfall | radiod soporta vía canal spectrum | Sí | Sí | Sí | [user_spectrum_websocket.go:335](\/home\/ni\/projects\/ka9q_ubersdr\/user_spectrum_websocket.go:335), [radiod.go:415](\/home\/ni\/projects\/ka9q_ubersdr\/radiod.go:415) | mixto |
| bookmarks | No | Sí | Sí | Sí, HTTP | [main.go:2745](\/home\/ni\/projects\/ka9q_ubersdr\/main.go:2745) | mixto |
| bands | No | Sí | Sí | Sí, HTTP | [main.go:2809](\/home\/ni\/projects\/ka9q_ubersdr\/main.go:2809) | mixto |
| session timeout / max session time | No | Sí | Parcial | Sí, HTTP | [main.go:2293](\/home\/ni\/projects\/ka9q_ubersdr\/main.go:2293) | remoto |
| IQ wide modes | Sí | Sí | Parcial | Sí, con restricciones | [websocket.go:454](\/home\/ni\/projects\/ka9q_ubersdr\/websocket.go:454) | no prioritario |

Lectura práctica de la tabla:

- [Confirmado por código] Para escucha HF móvil, el control útil y estable hoy es: `frequency`, `mode`, `bandwidthLow`, `bandwidthHigh`, `set_squelch`, `zoom/pan/reset`, bookmarks, bands, session/description.
- [Confirmado por código] No hay evidencia de un API principal para AGC detallado, notch, NB, NR remoto, CW pitch independiente o BFO shift.

# 5. Qué ya hace el backend y no debe duplicar la app

No implementar en local:

- demodulación de USB/LSB/CW/AM/FM
- filtrado principal del canal escuchado
- cambio real de preset/modo demodulador
- control de frecuencia RF del canal
- waterfall RF a partir de IQ local en el caso normal
- SNR/baseband/noise del receptor como cálculo primario
- gestión de SSRC, canal radiod, lifecycle de sesión real
- validaciones de límites de modo, ancho y acceso a IQ modes

Evidencias fuertes:

- control de canal radiod en [radiod.go](/home/ni/projects/ka9q_ubersdr/radiod.go:286)
- actualización de bordes de filtro en [radiod.go](/home/ni/projects/ka9q_ubersdr/radiod.go:475)
- audio cliente generado en backend en [websocket.go](/home/ni/projects/ka9q_ubersdr/websocket.go:980)
- spectrum RF generado en backend en [user_spectrum_websocket.go](/home/ni/projects/ka9q_ubersdr/user_spectrum_websocket.go:498)

Caso especial importante:

- [Confirmado por código] En cambio de modo, UberSDR hace un flujo en dos pasos y espera `500 ms` para que radiod recargue el preset antes de re-aplicar bordes de filtro en [websocket.go](/home/ni/projects/ka9q_ubersdr/websocket.go:815). La app no debe intentar “compensar” esto con DSP local.

# 6. Qué sí debe hacer la app Android

Responsabilidades recomendadas:

- abrir y mantener `POST /connection`
- abrir `/ws` para audio con `format=opus` preferentemente
- abrir `/ws/user-spectrum` para waterfall
- enviar `tune`, `get_status`, `ping`, `set_squelch`
- renderizar waterfall, cursor, ancho de filtro y S-meter
- gestionar UX local: bandas, memorias, favoritos, step tuning, snap, gestos, háptica, presets de modo
- persistir estado local de UI y preferencias del usuario
- manejar reconexión y restablecimiento de sesión cuando corresponda

Recomendaciones concretas:

- sintonía: remota, con estado local UX
- filtros: remotos, pero el control de tiradores y presets puede ser local
- audio: reproducción local; DSP final solo como opcional de salida, nunca como sustituto del canal remoto
- memorias: local primero; opcional sincronización con bookmarks remotos
- bandas: consumir `/api/bands` y complementar con accesos rápidos locales
- indicadores visuales: waterfall y medidores locales basados en datos del servidor

Clasificación útil para producto:

- A. NO implementar en local
  - AGC del receptor
  - demodulación
  - filtro principal del canal
  - waterfall RF desde IQ local
  - notch/NR/NB “de receptor” sin API backend real
- B. SÍ implementar en local
  - volumen final
  - mute
  - fades anti-click
  - buffer/jitter management
  - gestos, snap, háptica
  - memorias locales
  - layout y ergonomía móvil
- C. Mixto con decisión consciente
  - S-meter: datos remotos + render local
  - bookmarks/bands: remoto con caché local
  - EQ o compresión de salida: solo como postproceso local opcional
  - auto-tune visual sobre waterfall: lógica local basada en spectrum remoto

# 7. Archivos clave y mapa de código

Leer primero:

1. [websocket.go](/home/ni/projects/ka9q_ubersdr/websocket.go:337)
   Papel: protocolo real de audio, validación, `tune`, `set_squelch`, streaming binario.
2. [session.go](/home/ni/projects/ka9q_ubersdr/session.go:23)
   Papel: modelo de sesión y vínculo entre usuario, canal radiod y recursos de audio/spectrum.
3. [radiod.go](/home/ni/projects/ka9q_ubersdr/radiod.go:286)
   Papel: traducción real a comandos de ka9q-radio/radiod.
4. [user_spectrum_websocket.go](/home/ni/projects/ka9q_ubersdr/user_spectrum_websocket.go:107)
   Papel: protocolo real de spectrum/waterfall.
5. [audio.go](/home/ni/projects/ka9q_ubersdr/audio.go:17)
   Papel: recepción de audio desde radiod.
6. [user_spectrum.go](/home/ni/projects/ka9q_ubersdr/user_spectrum.go:176)
   Papel: polling y parseo de spectrum desde radiod.
7. [main.go](/home/ni/projects/ka9q_ubersdr/main.go:2304)
   Papel: handshake `/connection` y APIs auxiliares.
8. [static/minimal-radio.js](/home/ni/projects/ka9q_ubersdr/static/minimal-radio.js:1)
   Papel: referencia minimalista de cliente útil para Android.
9. [docs/architecture/android-audio-protocol.en.md](/home/ni/projects/ka9q_ubersdr/docs/architecture/android-audio-protocol.en.md:1)
   Papel: especificación práctica del binario de audio.

Archivos complementarios:

- [main.go](/home/ni/projects/ka9q_ubersdr/main.go:2745) `/api/bookmarks`
- [main.go](/home/ni/projects/ka9q_ubersdr/main.go:2809) `/api/bands`
- [main.go](/home/ni/projects/ka9q_ubersdr/main.go:2869) `/api/description`
- [static/app.js](/home/ni/projects/ka9q_ubersdr/static/app.js:1) para distinguir UX local de DSP remoto
- [static/carrier-detector.js](/home/ni/projects/ka9q_ubersdr/static/carrier-detector.js:1) como ejemplo de inteligencia visual local sobre spectrum remoto

# 8. Dudas abiertas y validaciones pendientes

Puntos no confirmados:

- [No confirmado] Parámetros backend para AGC detallado, notch, NR, NB, de-emphasis o pitch CW independientes en el protocolo principal cliente-servidor revisado.
- [No confirmado] Si ka9q-radio internamente tiene AGC/DSP adicionales no visibles aquí pero no expuestos por UberSDR.
- [No confirmado] Si existen otros clientes o APIs secundarios que expongan controles DSP avanzados fuera del flujo principal `/ws` y `/ws/user-spectrum`.

Inferencias razonables:

- [Inferido] `sam` implica sincronía AM en backend a nivel de preset, pero no aparece un control granular aparte de seleccionar el modo.
- [Inferido] La app Android ideal para vuestro caso es un “cliente tonto de audio/control” con una capa fuerte de UX de spectrum, no un cliente DSP.
- [Inferido] El pitch CW visible para usuario, si se quisiera, hoy habría que modelarlo como combinación de modo/filtro/UX y no como parámetro backend ya expuesto.

Validaciones recomendadas:

- probar en un servidor real si `cwu/cwl` con distintos `bandwidthLow/high` cubre suficientemente el caso de “pitch CW” para móvil
- confirmar con captura real si queréis usar siempre `opus` en Android o reservar `pcm-zstd` para casos especiales
- decidir si el S-meter del móvil se basará en `basebandPower`, `SNR = basebandPower - noiseDensity`, o ambos
- validar si queréis exponer squelch en la primera versión móvil; el backend sí lo soporta

Referencia externa antigua:

- [No confirmado] La URL externa compartida por el usuario no se pudo contrastar aquí de forma fiable; debe considerarse solo orientativa frente al código actual del repo.

# 9. Recomendación final de arquitectura Android

Arquitectura recomendada:

- Cliente Android delgado en RF y fuerte en UX.
- Backend como autoridad total de canal SDR.
- Dos WebSockets:
  - `/ws` para audio/control de canal
  - `/ws/user-spectrum` para waterfall/spectrum
- Un único `user_session_id` compartido entre ambos.

Modelo recomendado de responsabilidades:

- Servidor:
  - frecuencia real
  - modo real
  - filtro real
  - squelch real
  - demodulación
  - audio listo para reproducir
  - waterfall RF
  - límites y sesión
- Cliente:
  - reproducir audio
  - controlar tune/mode/filter
  - dibujar waterfall y filtro
  - mantener memorias, bandas y ergonomía
  - suavizar UX de audio y gestos

Respuesta breve a las preguntas de producto:

- ¿La app Android debe incluir AGC local?
  - No, no como AGC de receptor.
- ¿El filtro de audio debe ser remoto o local?
  - Remoto para el canal principal; local solo como postproceso opcional de salida.
- ¿El pitch de CW ya existe en backend?
  - No está confirmado como parámetro independiente expuesto.
- ¿NR/notch/shift ya están disponibles?
  - No están confirmados en el protocolo principal revisado.
- ¿Qué mensajes debe enviar la app?
  - `/connection`, `tune`, `ping`, `get_status`, `set_squelch`, y mensajes de spectrum `zoom/pan/reset/get_status`.
- ¿Qué estado debe mantener localmente?
  - sesión UI, frecuencia visible, modo seleccionado, bordes de filtro visibles, bandas, memorias, preferencias, estado de reconexión, configuración de waterfall y audio final.
- ¿Qué cosas son mera UX y cuáles son DSP real?
  - UX local: volumen, mute, gestos, snap, memorias, layout, render, háptica.
  - DSP real del receptor: demodulación, filtros, spectrum RF, squelch y canal remoto.
