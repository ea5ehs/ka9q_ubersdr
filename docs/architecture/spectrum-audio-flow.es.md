# Flujo de Audio y Espectro en UberSDR

## Estado
Confirmado por inspección del backend y validado en cliente Android real

## Última revisión
2026-04-14

---

## 1. Visión general

UberSDR gestiona dos flujos independientes:

- Audio: RTP -> WebSocket -> cliente
- Espectro: STATUS/FFT -> WebSocket -> cliente

En Android validado:

- primero se abre audio
- después de una espera breve se abre spectrum
- esa espera evita `429 Too Many Requests` en despliegues protegidos por Cloudflare

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

Todavía no implementado:

- zoom
- pan
- eje de frecuencia
- interacción táctil
- color mapping avanzado

---

## 5. Notas prácticas del backend

- no existe un parámetro documentado para forzar FULL frames
- FULL aparece al inicio, en resize o cuando el backend decide que delta deja de compensar
- el comportamiento normal tras el primer full es recibir delta
