# Analisis del cliente `clients/ubersdr-audio`

Este documento resume la logica realmente implementada en el cliente Go `clients/ubersdr-audio` del repo `ka9q_ubersdr`, centrada solo en protocolo, control de conexion, sintonia, modo, filtros, sesion y reconexion.

Objetivo: servir como referencia practica y trazable para un cliente Android, sin refactorizar ni inventar comportamiento no presente en el codigo.

## Criterio

- Hechos observados: salen directamente del codigo citado.
- Inferencias: se marcan aparte y son conclusiones razonables a partir del codigo.
- Si hay varias descripciones posibles, prevalece la implementacion ejecutable sobre comentarios o README.

## 1. Mapa de archivos y funciones relevantes

- [clients/ubersdr-audio/client.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:77)
  - `FetchDescription()`
  - Hace `GET /api/description` y obtiene `default_frequency`, `default_mode`, `max_session_time`, `max_clients` y metadatos del receptor.

- [clients/ubersdr-audio/client.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:103)
  - `FetchStats()`
  - Hace `GET /stats` para mostrar usuarios activos. No forma parte del flujo minimo de audio.

- [clients/ubersdr-audio/client.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:342)
  - `parseBaseURL()`
  - Normaliza `BaseURL`. Si el usuario no pone esquema, fuerza `http://`.

- [clients/ubersdr-audio/client.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:361)
  - `buildWSURL()`
  - Construye la URL WebSocket real a partir de `BaseURL` y fija los query params efectivos.

- [clients/ubersdr-audio/client.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:398)
  - `checkConnectionAllowed()`
  - Hace `POST /connection` con `user_session_id` y `password` antes de abrir el WebSocket.

- [clients/ubersdr-audio/client.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:443)
  - `Connect()`
  - Genera nuevo `user_session_id` y arranca la conexion real en background.

- [clients/ubersdr-audio/client.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:474)
  - `ConnectForce()`
  - Fuerza una reconexion aunque el estado anterior aun no se haya asentado del todo.

- [clients/ubersdr-audio/client.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:498)
  - `Disconnect()`
  - Cancela contexto y cierra el WebSocket para desbloquear la lectura.

- [clients/ubersdr-audio/client.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:514)
  - `Tune()`
  - Envia un mensaje JSON `type:"tune"` por el WebSocket existente para cambiar frecuencia, modo y filtros sin reconectar.

- [clients/ubersdr-audio/client.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:537)
  - `runLoop()`
  - Flujo real de conexion: `POST /connection` -> `DialContext()` del WebSocket -> keepalive -> recepcion de audio.

- [clients/ubersdr-audio/client.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:673)
  - `handleBinary()`
  - Elige el decoder efectivo segun `Format`: Opus o PCM-zstd.

- [clients/ubersdr-audio/client.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:780)
  - `deliverAudio()`
  - Entrega PCM a la salida de audio y recrea la salida si cambian `sampleRate` o `channels`.

- [clients/ubersdr-audio/pcm_decoder.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/pcm_decoder.go:64)
  - `DecodePCMBinary()`
  - Decodifica tramas `pcm-zstd` con headers `PC` v1/v2 y `PM`.

- [clients/ubersdr-audio/opus_decoder_linux.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/opus_decoder_linux.go:103)
  - `decodeOpusFrame()`
  - Parsea la trama Opus v2 real y recrea el decoder si cambian `sampleRate` o `channels`.

- [clients/ubersdr-audio/main.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:35)
  - `bwSliderMax()`
  - Define el maximo de ancho de banda por modo.

- [clients/ubersdr-audio/main.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:45)
  - `bwDefaultSlider()`
  - Define el ancho por defecto por modo.

- [clients/ubersdr-audio/main.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:65)
  - `bwToLoHi()`
  - Convierte el valor de UI a `bandwidthLow` y `bandwidthHigh` reales para el servidor.

- [clients/ubersdr-audio/main.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:88)
  - `clampFreq()`
  - Limita la frecuencia a `10 kHz .. 30 MHz`.

- [clients/ubersdr-audio/main.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:261)
  - `sendTune()`
  - Actualiza el estado local y llama a `client.Tune()`.

- [clients/ubersdr-audio/main.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:560)
  - `doConnect()`
  - Recoge el estado actual de UI, hace `FetchDescription()` best-effort y llama a `client.Connect()`.

- [clients/ubersdr-audio/main.go](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:761)
  - `profileConnectAndClose()`
  - Aplica un perfil, desconecta, espera a estado terminal y usa `ConnectForce()`.

## 2. Flujo real de conexion

### Paso a paso desde inicio hasta audio recibido

1. La UI llama a `doConnect()` y fija `client.BaseURL` con el valor actual del campo URL.
   - Referencia: [main.go:560](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:560)

2. Antes de conectar, intenta `GET /api/description`.
   - Se usa para:
   - obtener `default_frequency`
   - obtener `default_mode`
   - obtener `max_session_time`
   - obtener `max_clients`
   - construir la etiqueta de estacion
   - Referencias: [client.go:77](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:77), [main.go:571](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:571)

3. La UI calcula frecuencia, modo y filtros efectivos.
   - Frecuencia: parsea el campo en kHz y lo clamp a rango valido.
   - Filtros: usa `bwToLoHi(currentMode, bwSlider.Value)`.
   - Referencias: [main.go:622](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:622), [main.go:632](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:632)

4. `client.Connect()` genera un `user_session_id` nuevo y lanza `runLoop()`.
   - Referencia: [client.go:443](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:443)

5. `runLoop()` entra en `StateConnecting`.
   - Referencia: [client.go:537](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:537)

6. `runLoop()` hace `POST /connection`.
   - Body JSON:
   ```json
   {
     "user_session_id": "<uuid>",
     "password": "<optional>"
   }
   ```
   - Si el servidor rechaza, la conexion no sigue.
   - Referencia: [client.go:398](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:398)

7. Si `POST /connection` permite, guarda `MaxSessionTime` y `Bypassed` y construye la URL WebSocket.
   - Referencias: [client.go:550](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:550), [client.go:556](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:556)

8. Abre el WebSocket con `DialContext()`.
   - URL base `/ws`
   - esquema `ws` o `wss` segun `http` o `https`
   - Referencia: [client.go:575](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:575)

9. Tras conectar, cambia a `StateConnected`.
   - Referencia: [client.go:585](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:585)

10. Inicializa decoders y workers.
   - PCM-zstd: `NewPCMBinaryDecoder()`
   - Opus: worker con `opusDecodeCh`
   - entrega PCM: worker con `pcmDeliverCh`
   - Referencias: [client.go:587](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:587), [client.go:598](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:598), [client.go:612](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:612)

11. Arranca keepalive.
   - Envia `{"type":"ping"}` cada 30 segundos.
   - Referencia: [client.go:627](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:627)

12. Entra en bucle de lectura del WebSocket.
   - Ignora mensajes JSON.
   - Procesa solo `BinaryMessage`.
   - Referencia: [client.go:648](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:648)

13. Si el formato es Opus:
   - `handleOpusBinary()` encola la trama
   - `decodeAndDeliverOpus()` parsea header v2 y decodifica
   - `deliverAudio()` la entrega a audio
   - Referencias: [client.go:679](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:679), [client.go:712](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:712), [client.go:733](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:733)

14. Si el formato es PCM-zstd:
   - `handlePCMBinary()` descomprime y decodifica
   - encola PCM ya listo
   - `deliverAudio()` lo entrega
   - Referencias: [client.go:686](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:686), [pcm_decoder.go:64](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/pcm_decoder.go:64)

## 3. Tabla de parametros de conexion

| Parametro exacto | Ejemplo | Donde se fija | Cuando cambia | Afecta al servidor o solo UI |
| --- | --- | --- | --- | --- |
| `frequency` | `14200000` | WS query en [client.go:384](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:384), tune JSON en [client.go:525](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:525) | al conectar y en retune | servidor |
| `mode` | `usb` | WS query en [client.go:385](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:385), tune JSON en [client.go:526](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:526) | al conectar y al cambiar modo | servidor |
| `bandwidthLow` | `-2400` | WS query en [client.go:389](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:389), tune JSON en [client.go:527](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:527) | al conectar y al cambiar slider/modo | servidor |
| `bandwidthHigh` | `2400` | WS query en [client.go:390](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:390), tune JSON en [client.go:528](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:528) | al conectar y al cambiar slider/modo | servidor |
| `format` | `opus` o `pcm-zstd` | WS query en [client.go:373](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:373) | solo al reconectar | servidor |
| `version` | `2` | WS query en [client.go:387](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:387) | no cambia | servidor |
| `user_session_id` | UUID | `POST /connection` en [client.go:410](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:410) y WS query en [client.go:388](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:388) | nuevo por cada `Connect()` o `ConnectForce()` | servidor |
| `password` | `secret` | `POST /connection` en [client.go:412](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:412) y query opcional en [client.go:391](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:391) | si el usuario lo cambia | servidor |
| `DeviceID` | id local del dispositivo | estado del cliente en [main.go:640](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:640) | cuando cambia salida de audio | solo cliente |
| volumen/canal | `0.75`, `Left` | setters locales en [main.go:490](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:490), [main.go:514](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:514) | cuando cambia UI | solo cliente |

## 4. Modos y filtros

### Modos soportados detectados

- `usb`
- `lsb`
- `am`
- `fm`
- `cwu`
- `cwl`

Referencia: [main.go:32](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:32)

### Nombres exactos usados internamente

- Nombres visibles en UI:
  - `USB`
  - `LSB`
  - `AM`
  - `FM`
  - `CWU`
  - `CWL`

- Nombres reales enviados al servidor:
  - `usb`
  - `lsb`
  - `am`
  - `fm`
  - `cwu`
  - `cwl`

Referencia: [main.go:77](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:77)

### Filtros/passband por defecto por modo

- `usb` y `lsb`: slider por defecto `2700`
- `cwu` y `cwl`: slider por defecto `600`
- `am`: slider por defecto `4000`
- `fm`: slider por defecto `5000`

Referencia: [main.go:45](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:45)

### Conversion real a `bandwidthLow` / `bandwidthHigh`

- `usb` y `cwu`: `lo=0`, `hi=+val`
- `lsb` y `cwl`: `lo=-val`, `hi=0`
- `am` y `fm`: `lo=-val`, `hi=+val`

Referencia: [main.go:60](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:60)

### Maximos del control de ancho

- `am` y `fm`: maximo `6000`
- resto: maximo `5000`

Referencia: [main.go:34](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:34)

### Casos especiales de CW/USB/LSB/AM

- Hecho observado:
  - `CWU` comparte la logica de passband con `USB`.
  - `CWL` comparte la logica de passband con `LSB`.
  - `AM` y `FM` usan passband simetrico.
  - No hay otro tratamiento especial por modo en `client.go`.

- Hecho observado:
  - `AllowedIQModes` aparece en la respuesta de `/connection`, pero este cliente no lo usa para validar ni limitar los modos de audio.
  - Referencia: [client.go:140](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:140)

## 5. Retune / cambio de modo

### Como se hace

- Hecho observado:
  - El retune normal se hace enviando un JSON por el WebSocket ya abierto.
  - Referencia: [client.go:514](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:514)

```json
{
  "type": "tune",
  "frequency": 14200000,
  "mode": "usb",
  "bandwidthLow": -2400,
  "bandwidthHigh": 2400
}
```

### Que se reutiliza

- La conexion WebSocket existente.
- El `user_session_id` actual.
- El contexto activo.
- Los workers de lectura/decodificacion activos.

Referencia principal: [client.go:514](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:514)

### Que se recrea

- En retune normal: nada a nivel de sesion/conexion.
- Durante reproduccion, la salida de audio si puede recrearse si cambian `sampleRate` o `channels`.
- En Opus, el decoder se recrea si cambian `sampleRate` o `channels`.

Referencias: [client.go:794](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:794), [opus_decoder_linux.go:116](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/opus_decoder_linux.go:116)

### Si mantiene session id o no

- Hecho observado:
  - En retune normal, si.
  - En reconexion por cambio de formato, perfil o nueva conexion, no.
  - `Connect()` y `ConnectForce()` generan un UUID nuevo.

Referencias: [client.go:452](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:452), [client.go:482](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:482)

### Si hay debounce o throttle

- Hecho observado:
  - No hay debounce temporal para retune.
  - Cada Enter en frecuencia, click de step, cambio de modo y fin de drag del slider dispara `sendTune()` inmediato.

Referencias: [main.go:277](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:277), [main.go:291](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:291), [main.go:315](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:315), [main.go:331](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:331)

### Reconexiones que si existen

- Cambio de formato `Compressed` / `Uncompressed`:
  - hace `Disconnect()`
  - espera 300 ms
  - llama `Connect()`
  - Referencia: [main.go:389](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:389)

- Carga de perfil o cambio de instancia:
  - hace `Disconnect()`
  - espera a estado terminal
  - usa `ConnectForce()`
  - Referencia: [main.go:783](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:783)

- Auto-reconnect tras error:
  - cuenta atras de 5 s
  - si el usuario no ha desconectado explicitamente, llama a `doConnect()`
  - Referencia: [main.go:1408](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:1408)

## 6. Cosas trasladables al cliente Android

### Prioridad alta

- Implementar `parseBaseURL()` y `buildWSURL()` con la misma logica.
  - `http -> ws`
  - `https -> wss`
  - incluir `version=2`
  - incluir `user_session_id`
  - incluir `bandwidthLow` y `bandwidthHigh`
  - Referencias: [client.go:342](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:342), [client.go:361](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:361)

- Implementar el flujo `POST /connection` antes del WebSocket.
  - Leer y conservar `MaxSessionTime` y `Bypassed`.
  - Referencias: [client.go:398](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:398), [client.go:540](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:540)

- Implementar el retune con el JSON exacto `type:"tune"`.
  - Referencia: [client.go:523](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:523)

- Copiar la politica de filtros por modo:
  - defaults de `bwDefaultSlider()`
  - conversion `bwToLoHi()`
  - Referencias: [main.go:45](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:45), [main.go:65](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:65)

### Prioridad media

- Clamp de frecuencia `10 kHz .. 30 MHz`.
  - Referencia: [main.go:82](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:82)

- Keepalive `{"type":"ping"}` cada 30 segundos.
  - Referencia: [client.go:627](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:627)

- Politica de reconexion segura:
  - desconectar
  - esperar estado terminal
  - reconectar
  - Referencia: [main.go:783](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/main.go:783)

### Prioridad baja

- `FetchDescription()` y `FetchStats()` para UX.
  - etiqueta de estacion
  - usuarios conectados
  - temporizador de sesion
  - Referencias: [client.go:77](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:77), [client.go:103](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:103)

## 7. Riesgos o detalles sutiles

- Hecho observado:
  - El README describe el protocolo, pero la implementacion efectiva anade `version=2` al WebSocket y soporta cabeceras PCM v2 con `basebandPower` y `noiseDensity`.
  - Para implementar Android, deben prevalecer `client.go` y `pcm_decoder.go`.
  - Referencias: [README.md:97](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/README.md:97), [client.go:387](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:387), [pcm_decoder.go:42](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/pcm_decoder.go:42)

- Hecho observado:
  - Si `POST /connection` falla por red o parseo, el cliente devuelve una respuesta permisiva y aun intenta abrir el WebSocket.
  - El error real puede verse luego en el dial del WS o en la lectura.
  - Referencia: [client.go:422](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:422)

- Hecho observado:
  - El password se envia en dos sitios:
  - en `POST /connection`
  - y tambien como query param del WebSocket si existe
  - Referencias: [client.go:410](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:410), [client.go:391](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:391)

- Hecho observado:
  - `AllowedIQModes` existe en la respuesta del servidor, pero este cliente no lo usa.
  - No hay validacion local de modo basada en esa lista.
  - Referencia: [client.go:140](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:140)

- Hecho observado:
  - El cliente suelta frames si los workers se quedan momentaneamente atras, para no bloquear el receive loop del WebSocket.
  - Esto aparece tanto en PCM como en Opus.
  - Referencias: [client.go:704](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:704), [client.go:725](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/client.go:725)

- Inferencia:
  - Para un cliente Android, la parte mas trasladable y fiable del repo es:
  - `client.go`
  - `bwDefaultSlider()` y `bwToLoHi()`
  - la semantica de `Connect/Disconnect/Tune`
  - La UI Fyne no es trasladable tal cual, pero si su politica de control.

## 8. Comparacion breve cuando hay varias fuentes

### README vs codigo ejecutable

- README:
  - resume bien el flujo general
  - Referencia: [README.md:93](/home/ni/projects/ka9q_ubersdr/clients/ubersdr-audio/README.md:93)

- Codigo efectivo:
  - es mas preciso
  - incluye `version=2`
  - define la semantica exacta de `POST /connection`
  - define la politica real de reconexion
  - define los defaults de modo y filtros

### Cual parece ser la implementacion efectiva

- Hecho observado:
  - La implementacion efectiva es `clients/ubersdr-audio/client.go` junto con la logica de control en `clients/ubersdr-audio/main.go`.
  - `README.md` sirve como apoyo, no como fuente normativa.

## 9. Resumen ejecutivo

- El cliente hace `GET /api/description` como paso de apoyo, no estrictamente necesario para el audio.
- El flujo de sesion real empieza con `POST /connection`.
- La conexion de audio real usa `WebSocket /ws` con query params completos.
- El retune normal no reconecta: envia `type:"tune"` sobre el mismo WS.
- Los modos efectivos detectados son `usb`, `lsb`, `am`, `fm`, `cwu`, `cwl`.
- Los filtros reales se derivan localmente con `bwToLoHi()`.
- `user_session_id` se regenera en cada nueva conexion, pero no en cada retune.
- Hay reconexion automatica tras error y reconexion explicita para cambio de formato o perfil.
