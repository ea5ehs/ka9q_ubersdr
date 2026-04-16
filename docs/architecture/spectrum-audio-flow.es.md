# Flujo de Audio y Espectro en UberSDR

## Estado
Confirmado por inspección del backend y validado en cliente Android real

## Última revisión
2026-04-16

---

## 1. Visión general

UberSDR gestiona dos flujos independientes:

- Audio: RTP -> WebSocket -> cliente
- Espectro: STATUS/FFT -> WebSocket -> cliente

En Android validado:

- primero se abre audio
- después de una espera breve se abre spectrum
- esa espera evita `429 Too Many Requests` en despliegues protegidos por Cloudflare

Estado funcional actual del cliente Android:

- audio operativo
- waterfall operativo
- bandas cargadas desde `/api/bands`
- barra superior con menú y power
- menú con paleta y telemetría
- línea rápida `MIN - + MAX C`
- frecuencia editable manualmente
- pan con un dedo operativo
- zoom por botones estable
- gesto de pinza descartado

---

## 2. Flujo de audio

### Origen

Generado por radiod a partir de la sesión de receptor.

### Backend

Archivo principal:

- `audio.go`

Responsabilidades:

- recibir RTP
- enrutar por SSRC
- entregar audio a la sesión

### Streaming

Archivo principal:

- `websocket.go`

Responsabilidades:

- codificar y enviar audio por `/ws`

### Cliente Android

Estado actual:

- apertura de audio WS validada
- reproducción y decode Opus fuera del alcance de esta nota

---

## 3. Flujo de espectro

### Origen

FFT generado por radiod.

### Backend

Archivos principales:

- `user_spectrum.go`
- `user_spectrum_websocket.go`

Responsabilidades:

- recibir datos de espectro
- distribuirlos a la sesión
- emitir `config`
- emitir frames `SPEC`

### Configuración inicial

Comportamiento real confirmado:

- `config` no llega como texto
- `config` llega como frame WebSocket binario comprimido con gzip
- `config` incluye `centerFreq`, `binCount`, `binBandwidth` y `totalBandwidth`
- `totalBandwidth = binCount * binBandwidth`

Implicación para clientes no web:

1. manejar `onMessage(bytes)`
2. descomprimir con `GZIPInputStream`
3. parsear el JSON resultante

### Frames `SPEC`

Header fijo confirmado:

- `SPEC`
- `version`
- `flags`
- `timestamp:uint64 LE`
- `frequency:uint64 LE`

Tamaño del header:

- `22 bytes`

Flags confirmados:

- `0x01` full float32
- `0x02` delta float32
- `0x03` full uint8
- `0x04` delta uint8

Para Android MVP:

- se usa `mode=binary8`
- se implementa `0x03` y `0x04`

Reconstrucción mínima necesaria:

1. `0x03` reemplaza el buffer completo
2. `0x04` aplica cambios sobre el último buffer completo
3. delta sin full previo debe ignorarse

Ejemplo de delta uint8:

```text
changeCount:uint16
repetir N veces:
  index:uint16
  value:uint8
```

Validación experimental en Android:

- `binCount = 1024`
- payload delta típico aproximado `100-200 bytes`
- tras reconstrucción:
  - buffer final = `1024`
  - `match=true`

Conclusión:

- el backend usa compresión delta de forma activa
- el cliente debe reconstruir buffer completo antes de renderizar

---

## 4. Render actual en Android

Estado actual validado:

- waterfall funcional
- escala de grises
- ancho lógico igual a `binCount`
- altura fija aproximada de `256` líneas
- scroll vertical continuo
- eje de frecuencia visible
- línea central visible
- zoom real validado contra backend
- pan real validado contra backend

Todavía no implementado:

- interacción táctil
- color mapping avanzado

Nota de estado actual del cliente Android spectrum:

- el backend soporta `zoom`, `pan`, `reset`, `ping` y `get_status`
- `zoom` usa `{"type":"zoom","frequency":<hz>,"binBandwidth":<objetivo>}`
- `pan` usa `{"type":"pan","frequency":<hz>}`
- `pan` no debe enviar `binBandwidth`
- el backend puede normalizar `binBandwidth` y ajustar `binCount`
- el enfoque de zoom visual local en UI no era la solución correcta
- el rango visible real debe recalcularse siempre desde la `config` devuelta por backend
- `totalBandwidth = binCount * binBandwidth`
- zoom in equivale a pedir menor `binBandwidth`
- zoom out equivale a pedir mayor `binBandwidth`
- el zoom out debe saturar en el mayor rango válido observado
- `MAX` debe enviar una sola acción de zoom máximo centrada en la frecuencia actual
- resultado observado: ya se distinguen señales CW y SSB con cierta facilidad

Reglas técnicas confirmadas:

- `MIN` restaura la vista más abierta válida con clamp seguro
- el pan funcional actual mueve sintonía real y vista coherente
- el zoom por pinza se descarta porque el backend normaliza `binBandwidth` y puede cambiar `binCount`, produciendo saltos poco predecibles

---

## 5. Selección de bandas y span visible

Estado actual validado:

- la fuente única de bandas es `GET /api/bands`
- Android usa `start` y `end` reales servidos por backend
- el centro se calcula como `(start + end) / 2`
- al pulsar banda no debe mantenerse el span global previo
- antes o junto con el centrado, Android debe pedir un zoom coherente con el ancho real de la banda

Secuencia correcta:

1. leer `start/end`
2. calcular `center`
3. derivar `binBandwidth` objetivo para mostrar esa banda
4. pedir `zoom`
5. centrar en `center`
6. aplicar `mode` de banda si existe
7. si no existe, usar fallback coherente con web

Bugs ya corregidos:

- selección de banda que dejaba el spectrum en el span inicial amplio
- aparición de límites incoherentes o negativos al conservar un rango previo demasiado abierto
- comportamiento incremental visible en `MAX`
- `MIN` con bordes negativos
- pan con referencia fija o bloqueo tras pocas interacciones
- intento de pinza descartado por mala usabilidad sobre zoom backend discreto

## 6. Pan y edición manual en estado actual

Comportamiento validado:

- drag horizontal con un dedo sobre el waterfall cambia `frequencyHz`
- la vista del spectrum acompaña con `centerFreq` coherente
- la frecuencia también puede editarse tocando el display y usando teclado numérico Android

Objetivo práctico actual:

- mover sintonía y vista de forma robusta
- no mantener un viewport desacoplado de la operación real

---

## 7. Notas prácticas del backend

- no existe un parámetro documentado para forzar FULL frames
- FULL aparece al inicio, en resize o cuando el backend decide que delta deja de compensar
- el comportamiento normal tras el primer full es recibir delta
