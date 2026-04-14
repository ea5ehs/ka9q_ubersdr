# Visión General de la Arquitectura de UberSDR

## Estado
Confirmado por inspección del backend y validado en cliente Android real

## Última revisión
2026-04-14

---

## 1. Propósito

Este repositorio implementa la capa de aplicación de UberSDR sobre radiod.

Proporciona:

- gestión de sesiones
- APIs HTTP y WebSocket
- audio remoto
- espectro/waterfall remoto
- interfaz web
- lógica de administración y soporte

---

## 2. Arquitectura de alto nivel

radiod (motor SDR)  
↓  
UberSDR (backend Go)  
↓  
Clientes (web, Android, escritorio)

---

## 3. Estado actual del cliente Android

Pipeline validado:

1. `POST /connection`
2. `GET /api/description`
3. abrir `/ws` de audio
4. esperar brevemente
5. abrir `/ws/user-spectrum?user_session_id=...&mode=binary8`
6. recibir `config`
7. reconstruir `SPEC` full/delta en `binary8`
8. renderizar waterfall básico

Notas:

- audio y spectrum usan el mismo `user_session_id`
- la espera entre audio y spectrum evita `429` en despliegue detrás de Cloudflare
- `config` llega como binario gzip, no como texto

---

## 4. Responsabilidades principales

### Gestión de sesiones

Archivo principal:

- `session.go`

### Audio por WebSocket

Archivos principales:

- `audio.go`
- `websocket.go`

### Spectrum por WebSocket

Archivos principales:

- `user_spectrum.go`
- `user_spectrum_websocket.go`

### Frontend web de referencia

Archivos importantes:

- `static/app.js`
- `static/spectrum-display.js`
- `static/websocket-manager.js`

---

## 5. Notas de implementación Android (validado en cliente real)

Estado actual:

- bootstrap HTTP funcional
- audio WS funcional
- spectrum WS funcional
- `config` parseado correctamente
- reconstrucción de spectrum `0x03` y `0x04` funcional
- waterfall básico funcional

Render actual:

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

## 6. Implicaciones para clientes futuros

Cualquier cliente debe:

- soportar gzip en mensajes binarios de spectrum
- parsear header `SPEC` de `22 bytes`
- interpretar `flags` correctamente
- reconstruir deltas
- no asumir frames completos continuos
