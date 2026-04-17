# Feature Lab

## Estado
Documento base creado

## Última revisión
2026-04-17

## 1. Propósito

Este fichero mantiene un registro persistente y acumulativo del `Feature Lab`.

Su función es concentrar en un único punto:

- ideas experimentales
- estado actual de cada idea
- notas de investigación
- decisiones tomadas
- detalles de implementación cuando existan
- pendientes explícitos

El objetivo es preservar trazabilidad entre sesiones, chats y futuros cambios, sin mezclar este trabajo con el flujo principal del producto.

## 2. Reglas de funcionamiento del Lab

- el `Feature Lab` es un espacio separado del flujo principal
- primero medir y visualizar; después decidir; solo al final automatizar
- la primera línea de trabajo prioritaria es `CW`
- el primer experimento base es la detección de tono dominante en audio
- `SSB` se considera inicialmente un terreno de métricas y ayuda visual, no de autotune pleno
- los experimentos deben ser aislados, reversibles y con impacto mínimo en CPU y batería
- no tocar backend en esta fase
- no integrar nada en `RadioScreen` principal de entrada
- no registrar como hecho lo que todavía no esté investigado, diseñado o validado
- cuando falte información, dejar el pendiente explícito en vez de inferir implementación

## 3. Estados permitidos

- `idea`
- `investigacion`
- `diseño`
- `listo para implementar`
- `en curso`
- `pausado`
- `descartado`
- `integrado`

## 4. Lista maestra de ideas

| ID | Área | Título | Estado | Nota breve |
| --- | --- | --- | --- | --- |
| `CW-001` | `CW` | Detección de tono dominante en audio | `investigacion` | Primer experimento base priorizado. |
| `CW-002` | `CW` | Indicador de offset en Hz | `idea` | Dependiente de definir medición estable de tono/offset. |
| `CW-003` | `CW` | Botón "centrar CW" | `idea` | Requiere criterio de centrado todavía no definido. |
| `CW-004` | `CW` | Seguimiento suave de deriva | `idea` | Automación posterior a medición y visualización. |
| `CW-005` | `CW` | Decodificación CW en tiempo real | `idea` | Fuera de prioridad inicial; no hay diseño documentado. |
| `CW-006` | `CW` | Métricas de calidad CW | `idea` | Pendiente de decidir señales medibles útiles. |
| `CW-010` | `CW` | Notch / Autonotch en CW | `idea` | Línea futura de ayuda de recepción; sin base técnica cerrada aún. |
| `SSB-001` | `SSB` | Espectro de audio demodulado | `idea` | Encaja como ayuda visual inicial en `SSB`. |
| `SSB-002` | `SSB` | Métricas de ayuda de sintonía SSB | `idea` | Alineado con enfoque inicial de métricas. |
| `SSB-003` | `SSB` | Sugerencia de corrección fina en SSB | `idea` | Debe apoyarse antes en métricas observables. |
| `SSB-010` | `SSB` | SSB Tune Assist | `diseño` | Preparación de integración mínima sobre espectro RF sin autotune automático. |
| `LAB-001` | `Lab` | Análisis de "caligrafía" telegráfica | `idea` | Exploración abierta sin base técnica documentada aún. |
| `LAB-002` | `Lab` | Análisis de clicks y defectos de manipulación | `idea` | Exploración abierta sin investigación registrada todavía. |
| `LAB-003` | `Lab` | Comparativa entre señales u operadores | `idea` | Requiere definir antes métricas comparables. |

## 5. Entradas detalladas por idea

### `CW-001` — Detección de tono dominante en audio

- estado: `investigacion`
- objetivo actual: establecer el experimento base del Lab para `CW`
- definición operativa provisional:
  - "tono dominante" = frecuencia de pico dominante observada sobre el audio demodulado disponible en cliente, expresada en Hz dentro del dominio de audio
- salidas esperadas mínimas:
  - `peakHz`
  - `offsetHz`
  - `confidence`
- lo que sí sabemos:
  - es la primera línea de trabajo prioritaria dentro de `CW`
  - se considera el primer experimento base
  - debe plantearse de forma aislada, reversible y sin tocar backend
  - el cliente Android abre audio en `/ws` con `format=opus` y `version=2`
  - el audio entra en `AudioWsClient.onMessage(...)` como frame binario WebSocket y se entrega completo a `AudioPlayer.feedAudio(...)`
  - el cliente parsea una cabecera Opus v2 de `21` bytes antes de extraer el payload Opus
  - `sampleRate` y `channels` llegan embebidos en cada frame y se leen en `AudioPlayer.feedAudio(...)`
  - el audio utilizable para análisis local aparece tras `OpusDecoder.decode(...)`, como `ShortArray`
  - la reproducción actual usa `AudioTrack` en `PCM_16BIT`
- formato confirmado en cliente:
  - entrada de red: frame binario WebSocket con cabecera Opus v2 + payload Opus
  - punto decodificado: `ShortArray` PCM firmado de 16 bits
  - endianness en el punto decodificado: no aplica como flujo de bytes; el dato ya está materializado en muestras `Short`
- sample rate, tipo de muestra y buffer:
  - `sampleRate`: variable por frame, leído desde cabecera; no existe un valor fijo documentado en el cliente
  - `channels`: variable por frame, leído desde cabecera; la documentación técnica indica que el backend actual sugiere mono, pero el cliente confía en el campo recibido
  - tipo de muestra disponible para observación mínima: `ShortArray` PCM 16-bit firmado
  - buffer de decode provisional: `maxFrameSize = sampleRate / 10`, con `ShortArray(maxFrameSize * channels)` en `OpusDecoder`
  - buffer de reproducción: `AudioTrack.getMinBufferSize(...) * 2`
- ventana inicial provisional de trabajo:
  - `100 ms` como ventana máxima observable con fundamento directo en el buffer provisional de `OpusDecoder`
  - resolución, solape y ventana efectiva de análisis: `pendiente de confirmar`
- punto mínimo de observación recomendado:
  - `AudioPlayer.feedAudio(...)`, inmediatamente después de `OpusDecoder.decode(...)` y antes de `AudioTrack.write(...)`
  - motivo: ahí ya existe audio demodulado en PCM 16-bit y el cambio futuro podría aislarse sin tocar websocket ni backend
- notas de investigación:
  - [android-client/app/src/main/java/es/niceto/ubersdr/data/websocket/AudioWsClient.kt](/mnt/d/dev/ka9q_ubersdr/android-client/app/src/main/java/es/niceto/ubersdr/data/websocket/AudioWsClient.kt:78)
    - punto de entrada de audio en cliente: `onMessage(...)`
    - URL de audio construida con `format=opus` y `version=2`
  - [android-client/app/src/main/java/es/niceto/ubersdr/audio/AudioPlayer.kt](/mnt/d/dev/ka9q_ubersdr/android-client/app/src/main/java/es/niceto/ubersdr/audio/AudioPlayer.kt:44)
    - parseo de cabecera Opus v2
    - extracción de `sampleRate` y `channels`
    - punto exacto donde aparece PCM decodificado y se escribe en `AudioTrack`
  - [android-client/app/src/main/java/es/niceto/ubersdr/audio/OpusDecoder.kt](/mnt/d/dev/ka9q_ubersdr/android-client/app/src/main/java/es/niceto/ubersdr/audio/OpusDecoder.kt:16)
    - el decode devuelve `ShortArray`
    - el buffer provisional usa `sampleRate / 10`
  - [docs/architecture/android-audio-protocol.en.md](/mnt/d/dev/ka9q_ubersdr/docs/architecture/android-audio-protocol.en.md:96)
    - confirma layout Opus v2 y que `sampleRate` y `channels` viajan en cada frame
    - documenta que el backend actual sugiere mono, aunque el cliente debe confiar en el campo transmitido
- investigación backend primero:
  - decisión provisional: investigar backend primero antes de DSP local
  - datos reutilizables confirmados hoy por sesión / websocket / protocolo útil para Android:
    - `user-spectrum`: `centerFreq`, `binCount`, `binBandwidth`, `totalBandwidth`
    - `user-spectrum`: frames `SPEC` con bins de espectro y delta encoding
    - `/ws` audio v2: `sampleRate` y `channels` por frame
    - `/ws` audio v2: `basebandPower` y `noiseDensity` por frame
    - `/ws` audio `status`: `frequency`, `mode`, `sampleRate`, `channels`, `sessionId`, `info`
  - datos reutilizables confirmados hoy y consumidos ya por Android:
    - de espectro: `centerFreq`, `binCount`, `binBandwidth`, `totalBandwidth`, filas `SPEC` reconstruidas
    - de audio: `sampleRate` y `channels` para decode/reproducción
  - datos disponibles hoy por sesión pero que Android actual no consta que consuma:
    - `/ws` audio v2: `basebandPower`
    - `/ws` audio v2: `noiseDensity`
    - `/ws` audio `status`: `frequency`, `mode`, `sampleRate`, `channels`, `sessionId`, `info`
  - datos que existen en el sistema pero no constan disponibles para Android por sesión:
    - `FreqOffset`, `PllSnr`, `DopplerFrequency`, `DopplerFrequencyRate` en `ChannelStatus`
    - monitor global `FrequencyReferenceMonitor` con `detected_frequency`, `frequency_offset`, `signal_strength`, `snr`, `noise_floor`
  - equivalencias buscadas y estado actual:
    - `peakHz`: no consta disponible hoy por sesión para Android
    - `offsetHz`: no consta disponible hoy por sesión para Android
    - `drift`: no consta disponible hoy por sesión para Android
    - `snr` por sesión: no consta como campo explícito en el flujo Android actual; podría derivarse a partir de `basebandPower - noiseDensity` si Android parsea ambos campos
  - datos que siguen sin constar en el repo como disponibles para Android:
    - pico dominante de audio demodulado por sesión ya calculado
    - `confidence` para ayuda de sintonía
  - alternativa inicial con espectro RF:
    - posibilidad confirmada conceptualmente:
      - sí parece viable explorar un pico dominante en espectro RF antes de DSP audio
      - Android ya recibe una fila `SPEC` reconstruida como `ByteArray`, con tamaño igual a `binCount`
      - el render actual aplica `unwrap` de media anchura antes de pintar, de modo que izquierda = baja frecuencia y derecha = alta frecuencia
      - con `centerFreq` y `totalBandwidth` ya presentes en UI, puede definirse una ventana alrededor de la frecuencia actual y buscar el bin de valor máximo dentro de esa ventana
    - ventana de búsqueda alrededor de la frecuencia actual:
      - `frequencyHz` es hoy la referencia de sintonía real en el estado UI
      - el criterio vigente pasa a ser el ancho actualmente seleccionado en `bandwidthLowHz` y `bandwidthHighHz`
      - en `CWU` y `CWL`, ese ancho hoy resuelve por defecto a `-250..250 Hz`
      - por tanto, la búsqueda experimental usa el pasabanda actualmente seleccionado, no una ventana fija independiente adicional
      - justificación:
        - reutiliza la selección activa del usuario
        - mantiene coherencia entre visualización, pasabanda y búsqueda experimental
        - evita introducir una segunda ventana paralela con criterio distinto
      - una alternativa fija más amplia como `frequencyHz ± 500 Hz` queda solo como referencia investigada, no como criterio vigente
    - fórmula conceptual `binIndex -> frecuencia`:
      - `startFreq = centerFreq - totalBandwidth / 2`
      - `freqHz = startFreq + (binIndex / binCount) * totalBandwidth`
      - equivalencia práctica ya usada por la UI:
        - `totalBandwidth ~= binCount * binBandwidth`
    - precisión exacta de borde o centro de bin: `pendiente de confirmar`
    - fórmula conceptual `Hz -> bins` para una ventana alrededor de `frequencyHz`:
      - `windowStartHz` y `windowEndHz` deben definirse primero alrededor de `frequencyHz`
      - `startRatio = (windowStartHz - startFreq) / totalBandwidth`
      - `endRatio = (windowEndHz - startFreq) / totalBandwidth`
      - `startBin = floor(clamp(startRatio, 0, 1) * binCount)`
      - `endBin = floor(clamp(endRatio, 0, 1) * binCount)`
      - si `startBin > endBin`, habría que intercambiarlos
      - tratamiento exacto del borde superior y si conviene usar `binCount - 1` como máximo: `pendiente de confirmar`
    - nota sobre orientación y `unwrap`:
      - el `SPEC` binario llega reconstruido en `latestSpectrumRow`, pero el render visual actual aplica `unwrap` de media anchura antes de dibujar
      - como la geometría visible izquierda->derecha ya usa `startFreq..endFreq`, conceptualmente resulta más seguro razonar la ventana de búsqueda sobre bins ya orientados visualmente
      - trabajar sobre el buffer original previo al `unwrap` exigiría aplicar la misma corrección de orientación para no mezclar bins con frecuencias equivocadas
      - no consta todavía una función común reutilizable en Android para hacer ese mapping de forma única fuera del render
  - viabilidad operativa mínima:
    - identificar un máximo relativo en bins RF dentro de una ventana alrededor de `frequencyHz` sí parece posible con los datos actuales
    - convertir ese bin a frecuencia absoluta también parece posible con el mapping ya usado por la geometría del waterfall
    - limitaciones conocidas:
      - resolución limitada por `binBandwidth`
      - el backend puede discretizar `binBandwidth` y ajustar `binCount`, por lo que la resolución cambia con el zoom
      - el contenido `binary8` es cuantizado a `uint8`, no magnitud flotante completa
      - puede haber ruido, varias señales cercanas o picos dominantes fuera de la señal objetivo
      - un pico RF dominante no equivale necesariamente a tono CW útil en audio
      - no consta todavía una regla objetiva de ventana, umbral o desempate entre múltiples máximos
      - usar simplemente el máximo RF tiene riesgos claros:
        - portadora continua o componente estrecho no deseado
        - múltiples señales CW dentro de la misma ventana
        - pico fuerte adyacente pero no alineado con la intención del operador
        - resolución insuficiente si `binBandwidth` es demasiado grande para ajuste fino
        - `ByteArray` `binary8` cuantizado:
          - reduce detalle fino de amplitud
          - puede producir empates aparentes o máximos inestables entre bins contiguos
          - no conserva magnitud float32 completa en el cliente Android actual
  - decisión provisional:
      - explorar pico en espectro RF antes de DSP audio
      - empezar por autotune manual por pulsación, no continuo
      - antes de evaluar acumulación temporal de `SPEC`, hacer investigación previa de cadencia backend/protocolo/cliente y medir después en cliente real
    - nota previa sobre cadencia de `SPEC`:
      - backend observado en repo:
        - `user_spectrum.go` hace `poll` periódico a radiod con `poll_period_ms`; el valor por defecto configurado es `100 ms`, equivalente a `10 Hz`, pero esto depende de configuración
        - `user_spectrum_websocket.go` empaqueta cada respuesta como frame `SPEC` full/delta y `websocket.go` usa una cola no bloqueante de 30 frames; si la cola se llena, el frame se descarta
      - protocolo/documentación observada:
        - `SPEC` incluye cabecera con `timestamp` y `center frequency`
        - el contrato expone `centerFreq`, `binCount`, `binBandwidth` y `totalBandwidth`
        - la resolución visible depende de `binBandwidth` y `binCount`; el backend puede normalizar `binBandwidth` y ajustar `binCount`, por lo que zoom/configuración afectan la granularidad útil
      - Android observado hoy:
        - conecta con `mode=binary8`
        - reconstruye full/delta (`0x03` / `0x04`) y actualiza `latestSpectrumRow`
        - no consta throttling explícito en `SpectrumWsClient`, pero la UI consume solo la última fila expuesta en estado, así que filas intermedias pueden quedar sobrescritas visualmente aunque hayan llegado
        - Android actual no parsea ni expone hoy el `timestamp`/secuencia del header `SPEC`
      - factores que pueden afectar la tasa útil:
        - `poll_period_ms` del backend
        - normalización de `binBandwidth` y cambios de `binCount` asociados al zoom
        - carga/backpressure con descarte en colas de backend/websocket
        - posible sobrescritura visual en cliente al conservar solo la última fila
      - decisión provisional:
        - investigar backend primero y medir después en cliente real antes de diseñar acumulación temporal para `CW-001`
        - el laboratorio Android ya implementa un buffer corto local de hasta `10` filas `SPEC` recientes
        - `N` es ahora configurable en ajustes avanzados como `CW AutoTune Averaging`
        - rango permitido: `1..10`
        - valor por defecto actual: `6`
        - la acumulación se aplica solo sobre filas comparables dentro de la misma geometría útil del espectro
        - objetivo de esta fase:
          - encontrar el punto a partir del cual la acumulación mejora estabilidad del candidato RF y luego empeora por exceso de acumulación/latencia
    - primera implementación experimental ya disponible:
      - existe una implementación mínima local en cliente Android que calcula un candidato CW RF sin sintonizar la radio
      - alcance exacto de esta iteración:
        - usa el `SPEC` actual reconstruido
        - aplica la misma orientación visual implícita que el `unwrap` del render
        - mantiene un buffer local corto de hasta `10` filas `SPEC` ya orientadas para laboratorio
        - usa `CW AutoTune Averaging` para seleccionar el `N` activo `1..10`
        - el valor por defecto `6` prioriza algo más de estabilidad frente a latencia que `N` bajos
        - valores menores deberían responder antes pero con más variación; valores mayores deberían estabilizar más pero con más retardo
        - toma como ventana el ancho actualmente seleccionado en `bandwidthLowHz/highHz`
        - en `CW` actual, eso equivale por defecto a `-250..250 Hz`
        - convierte la ventana a `startBin..endBin`
        - busca el bin máximo dentro de esa ventana
        - convierte ese bin a frecuencia absoluta candidata
        - expone el resultado solo en telemetría de laboratorio, incluyendo `N` activo
      - fuera de alcance en esta iteración:
        - no llama a `tune()`
        - no hace seguimiento continuo
        - no añade suavizado temporal
        - no aplica heurísticas antiportadora ni antiinterferente
      - limitaciones conocidas de esta implementación:
        - depende del `binary8` cuantizado actual
        - usa máximo simple dentro del ancho seleccionado actual
        - no valida si el pico elegido es realmente la señal CW deseada
        - dentro de esa ventana ganará la señal más fuerte, no necesariamente la más conveniente
        - la visualización actual queda contenida en la telemetría existente, no en una UI de laboratorio dedicada
    - hallazgos prácticos:
      - en pruebas reales, usar acumulación con `N` cercano a `10` ha dado muy buena estabilidad del candidato RF `CW`
      - la percepción práctica de latencia sigue siendo buena incluso con ese `N`
      - el coste dominante no parece estar en el cálculo del pico; la acumulación y la búsqueda del máximo resultan prácticamente instantáneas frente a:
        - render del waterfall
        - actualización de UI
        - interacción del usuario
        - respuesta observable de sintonía
      - por tanto, un `N` alto resulta viable para:
        - autotune manual
        - verificación de frecuencia, por ejemplo en `WWV`
      - el límite práctico observado parece venir más del pipeline completo extremo a extremo que del cálculo en sí
    - botón manual `Auto` ya disponible:
      - existe un botón manual de laboratorio `A`, visible solo en `CW`
      - alcance exacto de esta iteración:
        - usa la frecuencia candidata RF ya calculada localmente
        - al pulsarlo hace una sola llamada de sintonía
        - no hace seguimiento continuo
        - no activa autotune automático permanente
        - no añade heurísticas nuevas
        - no afecta a otros modos
        - queda insertado a la derecha de la línea de ancho `CW`
      - validación actual del candidato:
        - si no hay candidato, no actúa
        - solo permite la acción si la frecuencia candidata sigue dentro de la ventana CW actual
        - también exige que la frecuencia candidata quede dentro del rango válido general del cliente
      - limitaciones actuales:
        - la validación sigue dependiendo de la ventana y del máximo simple del `SPEC`
        - no resuelve ambigüedad entre dos señales dentro de la ventana
        - no diferencia todavía portadora continua de señal CW útil
      - siguientes pruebas recomendadas:
        - señal CW limpia
        - dos señales dentro de la ventana
        - portadora continua cercana
- referencias backend relevantes:
  - [websocket.go](/mnt/d/dev/ka9q_ubersdr/websocket.go:1056)
    - el backend obtiene `BasebandPower` y `NoiseDensity` desde `GetChannelStatus(...)` y los envía en audio v2
  - [SPECTRUM_BINARY_PROTOCOL.md](/mnt/d/dev/ka9q_ubersdr/SPECTRUM_BINARY_PROTOCOL.md:21)
    - documenta `SPEC`, bins y delta encoding de espectro
  - [user_spectrum_websocket.go](/mnt/d/dev/ka9q_ubersdr/user_spectrum_websocket.go:840)
    - el `config` del websocket de espectro expone `centerFreq`, `binCount`, `binBandwidth`, `totalBandwidth`
  - [android-client/app/src/main/java/es/niceto/ubersdr/data/websocket/SpectrumWsClient.kt](/mnt/d/dev/ka9q_ubersdr/android-client/app/src/main/java/es/niceto/ubersdr/data/websocket/SpectrumWsClient.kt:122)
    - `SPEC` llega como `ByteArray`; Android reconstruye el buffer completo `binary8`
  - [android-client/app/src/main/java/es/niceto/ubersdr/ui/radio/RadioScreen.kt](/mnt/d/dev/ka9q_ubersdr/android-client/app/src/main/java/es/niceto/ubersdr/ui/radio/RadioScreen.kt:112)
    - la UI ya calcula `startFreq` y `endFreq` desde `centerFreq` y `totalBandwidth`
  - [android-client/app/src/main/java/es/niceto/ubersdr/presentation/radio/RadioUiState.kt](/mnt/d/dev/ka9q_ubersdr/android-client/app/src/main/java/es/niceto/ubersdr/presentation/radio/RadioUiState.kt:7)
    - `frequencyHz` es la frecuencia de sintonía representada hoy en el estado del cliente
  - [android-client/app/src/main/java/es/niceto/ubersdr/presentation/radio/RadioViewModel.kt](/mnt/d/dev/ka9q_ubersdr/android-client/app/src/main/java/es/niceto/ubersdr/presentation/radio/RadioViewModel.kt:220)
    - `CWU` y `CWL` usan por defecto un pasabanda `-250..250 Hz`
  - [android-client/app/src/main/java/es/niceto/ubersdr/ui/radio/RadioScreen.kt](/mnt/d/dev/ka9q_ubersdr/android-client/app/src/main/java/es/niceto/ubersdr/ui/radio/RadioScreen.kt:263)
    - el render actual hace `unwrap` de media anchura sobre la fila `SPEC`
  - [android-client/app/src/main/java/es/niceto/ubersdr/ui/radio/RadioScreen.kt](/mnt/d/dev/ka9q_ubersdr/android-client/app/src/main/java/es/niceto/ubersdr/ui/radio/RadioScreen.kt:157)
    - cálculo experimental local del candidato CW RF y exposición en telemetría
  - [android-client/app/src/main/java/es/niceto/ubersdr/ui/radio/RadioScreen.kt](/mnt/d/dev/ka9q_ubersdr/android-client/app/src/main/java/es/niceto/ubersdr/ui/radio/RadioScreen.kt:398)
    - botón manual `A` de laboratorio, limitado a `CW`, insertado junto al control de ancho `CW`
  - [docs/architecture/frontend-frequency-mapping.es.md](/mnt/d/dev/ka9q_ubersdr/docs/architecture/frontend-frequency-mapping.es.md:37)
    - documenta la geometría `startFreq -> freq` y el mapeo de bins
  - [websocket.go](/mnt/d/dev/ka9q_ubersdr/websocket.go:1415)
    - el websocket de audio ya envía un `status` por sesión con `frequency`, `mode`, `sampleRate`, `channels`, `sessionId` e `info`
  - [radiod_status.go](/mnt/d/dev/ka9q_ubersdr/radiod_status.go:165)
    - `ChannelStatus` incluye `FreqOffset`, `BasebandPower`, `NoiseDensity`, `PllSnr`, `DopplerFrequency` y `DopplerFrequencyRate`
  - [frequency_reference.go](/mnt/d/dev/ka9q_ubersdr/frequency_reference.go:13)
    - existe lógica backend de detección de pico y offset, pero para un monitor global de referencia externa
  - [instance_reporter.go](/mnt/d/dev/ka9q_ubersdr/instance_reporter.go:225)
    - el sistema ya publica `detected_frequency`, `frequency_offset`, `signal_strength`, `snr`, `noise_floor` en otra ruta del sistema
- lo que no sabemos todavía:
  - método exacto de detección
  - umbrales de estabilidad o confianza
  - superficie de UI o visualización concreta
  - coste esperado en CPU y batería
  - frecuencia objetivo o rango operativo de interés para CW en esta UX
  - definición exacta de `offsetHz` respecto a qué referencia local se calcularía
  - tamaño real de frame observado en ejecución para cada modo útil
  - si existe ya una API o websocket de cliente general que exponga `ChannelStatus` por sesión al Android actual
- riesgos o incógnitas:
  - el cliente actual no separa análisis y reproducción; el punto de observación mínimo comparte ruta con `AudioTrack.write(...)`
  - `sampleRate` no es constante en el código; cualquier análisis debe tolerar cambios o reconfiguración
  - `channels` queda pendiente de confirmar en práctica real, aunque la documentación sugiere mono
  - no hay aún contrato definido para interpretar `confidence`
  - parte de la telemetría útil existe en backend, pero no consta integrada en el contrato que usa hoy Android
  - siguientes pasos inmediatos:
    - confirmar si el `status` del websocket de audio puede reutilizarse desde Android sin cambios de backend
    - confirmar si Android puede parsear `basebandPower` y `noiseDensity` sin alterar el flujo principal
    - localizar si existe ya una ruta de cliente general para `ChannelStatus`; si no, dejarlo fuera de alcance actual
    - validar si una ventana pequeña alrededor de `frequencyHz` sobre `SPEC` resulta suficiente como experimento base para `CW-001`
    - si se prueba más adelante, priorizar una acción manual por pulsación sobre un seguimiento continuo
    - siguiente paso recomendado tras esta investigación:
      - si se abre una siguiente iteración, documentar primero una única convención de bins orientados y una ventana CW mínima antes de cualquier lógica de selección de pico
      - después decidir si el candidato actual merece activar un botón manual de sintonía
    - mantener FFT local como no recomendada mientras no se agoten los datos backend ya presentes

### `CW-002` — Indicador de offset en Hz

- estado: `idea`
- relación con otras ideas:
  - depende de una medición de tono/offset suficientemente estable
- pendiente explícito:
  - definir qué representa exactamente el offset y cómo se mostraría

### `CW-003` — Botón "centrar CW"

- estado: `idea`
- relación con otras ideas:
  - depende de disponer de una referencia fiable para centrado
- pendiente explícito:
  - definir la acción exacta, sus límites y su integración experimental

### `CW-004` — Seguimiento suave de deriva

- estado: `idea`
- relación con otras ideas:
  - viene después de medir y visualizar
  - no debe asumirse automatización en esta fase inicial
- pendiente explícito:
  - decidir si tiene sentido tras validar `CW-001` y `CW-002`

### `CW-005` — Decodificación CW en tiempo real

- estado: `idea`
- observación:
  - no existe diseño ni alcance definidos en la documentación actual
- pendiente explícito:
  - aclarar si este trabajo forma parte del Lab inicial o de una fase posterior

### `CW-006` — Métricas de calidad CW

- estado: `idea`
- observación:
  - puede servir como soporte para análisis posteriores, pero no hay métricas definidas aún
- pendiente explícito:
  - listar posibles métricas observables antes de proponer implementación

### `CW-010` — Notch / Autonotch en CW

- estado: `idea`
- objetivo:
  - documentar una futura ayuda de recepción en `CW` orientada a atenuar componentes no deseados dentro del audio útil
- tipos previstos:
  - `manual notch`
  - `autonotch`
- casos de uso:
  - atenuar una portadora continua o tono interferente cercano durante recepción `CW`
  - reducir la molestia de un pico estable no deseado sin modificar todavía la lógica principal de sintonía
- dependencias técnicas:
  - posible necesidad de audio demodulado disponible para análisis o filtrado local
  - posible necesidad de soporte backend si se concluye que el cliente no dispone de datos o punto de inserción suficientes
  - punto exacto de observación o aplicación: `pendiente de confirmar`
- riesgos:
  - atenuar la señal `CW` útil si el criterio de notch no discrimina bien entre objetivo e interferencia
  - introducir artefactos audibles o degradar inteligibilidad
  - requerir una definición más precisa de control manual frente a automatismo antes de cualquier implementación
- pendiente explícito:
  - aclarar si esta línea debe apoyarse en audio demodulado local, en datos derivados del backend o en ambos
  - acotar primero el caso de uso prioritario antes de plantear diseño técnico

### `SSB-001` — Espectro de audio demodulado

- estado: `idea`
- encaje actual:
  - consistente con la línea inicial de ayuda visual en `SSB`
- notas de investigación:
  - datos reutilizables confirmados hoy:
    - el servidor ya entrega espectro RF por sesión mediante `centerFreq`, `binCount`, `binBandwidth`, `totalBandwidth` y frames `SPEC`
    - Android ya consume ese espectro RF y reconstruye filas binarias para waterfall
    - el flujo de audio v2 ya incluye `basebandPower` y `noiseDensity`, reutilizables como ayuda métrica básica si se parsean
  - datos que existen en el sistema pero no constan disponibles para Android por sesión:
    - `FreqOffset`, `PllSnr` y otra telemetría de `ChannelStatus`
    - monitor global de referencia con detección de frecuencia y offset
  - datos que siguen sin constar en el repo:
    - espectro de audio demodulado específico por sesión para Android
    - pico de audio demodulado por sesión
    - offset fino SSB ya calculado por sesión para Android
  - decisión provisional:
    - el Lab puede arrancar en `SSB` con datos backend ya existentes solo en clave de métricas/ayuda visual básica
    - sigue pendiente cualquier ayuda más fina que requiera espectro de audio, pico u offset por sesión
- pendiente explícito:
  - concretar utilidad operacional y formato de visualización
  - confirmar si un espectro RF existente cubre parte de la necesidad antes de plantear espectro de audio demodulado aparte

### `SSB-002` — Métricas de ayuda de sintonía SSB

- estado: `idea`
- encaje actual:
  - consistente con el enfoque inicial de métricas y ayuda visual
- pendiente explícito:
  - definir qué métricas serían útiles y observables

### `SSB-003` — Sugerencia de corrección fina en SSB

- estado: `idea`
- relación con otras ideas:
  - debe apoyarse antes en métricas de ayuda de sintonía
  - no se considera autotune pleno en esta fase
- pendiente explícito:
  - delimitar si la sugerencia sería solo visual, textual o accionable

### `SSB-010` — SSB Tune Assist

- estado: `pausado`
- objetivo:
  - preparar una integración mínima y aislada para una ayuda de sintonía `SSB` basada primero en espectro RF ya disponible en Android
- alcance de esta fase:
  - implementación mínima de laboratorio sobre RF
  - sin botón de autotune `SSB`
  - sin cambios automáticos de frecuencia
  - sin retune continuo
  - sin refinado por audio
- puntos confirmados hoy en el cliente Android:
  - `SpectrumWsClient` reconstruye `SPEC` `binary8` full/delta y entrega `reconstructedData`
  - `RadioViewModel` recibe `onSpecFrame(...)` y guarda la última fila en `latestSpectrumRow`
  - `RadioUiState` ya contiene `frequencyHz`, `mode`, `spectrumCenterFreqHz`, `spectrumBinCount`, `spectrumBinBandwidthHz`, `spectrumTotalBandwidthHz` y `latestSpectrumRow`
  - `RadioViewModel.tune(...)` es el punto actual de ejecución de una orden de sintonía
  - `RadioScreen` ya dispone de zona de telemetría/lab donde hoy se muestra `CW Lab`
- punto de integración propuesto:
  - crear un módulo nuevo y aislado `SsbAutoTuneAnalyzer`
  - llamarlo en `RadioScreen`, en el mismo tramo donde hoy se calcula `cwRfCandidate`, una vez disponibles:
    - fila `SPEC` orientada visualmente
    - `frequencyHz`
    - `mode` `USB/LSB`
    - `spectrumCenterFreqHz`
    - `spectrumBinCount`
    - `spectrumTotalBandwidthHz`
  - en esta fase el resultado se limitaría a telemetría/lab, sin llamar a `tune()`
- estructuras sugeridas:
  - `SsbCandidate`
    - `startBin: Int`
    - `endBin: Int`
    - `widthHz: Double`
    - `lowerEdgeHz: Long`
    - `upperEdgeHz: Long`
    - `centroidHz: Long`
    - `totalEnergy: Double`
    - `referenceFrequencyHz: Long`
    - `roundingBonus: Double`
    - `score: Double`
  - `SsbAutoTuneResult`
    - `suggestedFrequencyHz: Long?`
    - `score: Double`
    - `candidateCount: Int`
    - `bestCandidate: SsbCandidate?`
    - `candidates: List<SsbCandidate>`
- firmas sugeridas:
  - `class SsbAutoTuneAnalyzer`
  - `fun analyze(visualSpectrumRow: ByteArray, binCount: Int, centerFreqHz: Long, totalBandwidthHz: Double, mode: RadioMode): SsbAutoTuneResult`
  - `fun detectCandidates(visualSpectrumRow: ByteArray, binCount: Int, centerFreqHz: Long, totalBandwidthHz: Double): List<SsbCandidate>`
  - `fun scoreCandidate(candidate: SsbCandidate, mode: RadioMode): Double`
  - `fun estimateReferenceFrequencyHz(candidate: SsbCandidate, mode: RadioMode): Long`
- fase 1 prevista: solo RF
  - detectar candidatos `SSB` agrupando bins contiguos en blobs
  - filtrar blobs por ancho aproximado `2.2..4.0 kHz`
  - calcular por candidato:
    - `startBin`, `endBin`
    - `widthHz`
    - `lowerEdgeHz`, `upperEdgeHz`
    - `centroidHz`
    - `totalEnergy`
  - estimar frecuencia base:
    - en `USB`, usar el borde inferior como referencia principal
    - en `LSB`, usar el borde superior como referencia principal
  - aplicar una heurística suave de frecuencia redondeada:
    - cercanía a múltiplos de `100 Hz`, `500 Hz` y `1 kHz`
    - solo como bonificación en el `score`, nunca como regla obligatoria
  - producir:
    - frecuencia sugerida
    - `score` / confianza
    - número de candidatos
- fase futura prevista: audio
  - refinar o validar el mejor candidato RF con rasgos de audio demodulado cuando exista una base suficiente para ello
- riesgos y limitaciones:
  - con solo RF, un blob ancho no garantiza que la sintonía de voz sea correcta
  - varias señales `SSB` cercanas pueden producir candidatos ambiguos
  - el `binary8` cuantizado limita detalle fino de amplitud
  - la heurística de frecuencia redondeada ayuda, pero no debe imponerse sobre la energía y la forma observada
  - sin audio, no habrá validación de inteligibilidad ni de tono natural
  - conviene mantener este análisis aislado de `tune()` hasta validar telemetría y estabilidad
- estado práctico actual:
  - la línea `RF-only` se probó en el cliente Android con telemetría de laboratorio
  - los resultados iniciales fueron inestables y fluctuaban con rapidez de frame a frame
  - la detección basada solo en RF no dio robustez suficiente para ayuda de sintonía fiable
  - la variabilidad natural de la voz `SSB` complicó la segmentación de blobs y el `scoring`
  - el código experimental se retira del cliente para no dejar caminos muertos ni telemetría residual
  - la idea queda documentada para una posible revisión futura con enfoque híbrido `RF + audio`

### `LAB-001` — Análisis de "caligrafía" telegráfica

- estado: `idea`
- observación:
  - idea exploratoria sin investigación documentada todavía
- pendiente explícito:
  - definir hipótesis de valor y señales observables

### `LAB-002` — Análisis de clicks y defectos de manipulación

- estado: `idea`
- observación:
  - idea exploratoria sin investigación documentada todavía
- pendiente explícito:
  - definir tipos de defectos y métricas asociadas

### `LAB-003` — Comparativa entre señales u operadores

- estado: `idea`
- relación con otras ideas:
  - depende de disponer antes de métricas comparables
- pendiente explícito:
  - definir eje de comparación y condiciones de validez

## 6. Decisiones globales tomadas hasta ahora

- el `Feature Lab` queda separado del flujo principal del producto
- la secuencia de trabajo es: medir y visualizar -> decidir -> automatizar
- `CW` es la primera línea de trabajo prioritaria
- `CW-001` es el primer experimento base
- `SSB` se aborda al inicio como espacio de métricas y ayuda visual
- los experimentos deben ser aislados, reversibles y de bajo impacto
- no se toca backend en esta fase
- no se integra nada directamente en `RadioScreen` principal de entrada

## 7. Siguientes pasos

- registrar la primera nota de investigación para `CW-001`
- concretar qué salida mínima de visualización o métrica permitiría validar `CW-001`
- revisar si `CW-002` debe pasar a `investigacion` tras cerrar la base de `CW-001`
- mantener `SSB` y `LAB-*` en estado `idea` hasta disponer de hipótesis y mediciones más concretas

## Cómo actualizar este fichero

- mantener este documento como fuente única del `Feature Lab`
- añadir nuevas ideas primero en la lista maestra y después en entradas detalladas
- no cambiar un estado sin dejar una nota breve que explique el motivo
- registrar decisiones globales solo cuando estén explícitamente acordadas
- si aparece una implementación, documentar solo hechos confirmados y su alcance mínimo
- si algo no se sabe, escribir `pendiente explícito` en vez de completar con supuestos
