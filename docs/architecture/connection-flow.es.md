# Visión General de la Arquitectura de UberSDR

## Estado
Confirmado (basado en inspección del repositorio y análisis asistido con Codex)

## Fuente
Inspección manual + exploración asistida con Codex

## Última revisión
2026-04-11

---

## 1. Propósito

Este repositorio implementa la capa de aplicación del sistema UberSDR.

Se sitúa delante del motor SDR (radiod, de ka9q-radio) y proporciona:

- gestión de sesiones de usuario
- APIs WebSocket (audio, control, espectro)
- interfaz web y paneles
- decodificadores digitales y analítica
- herramientas de administración y configuración

---

## 2. Arquitectura de alto nivel

radiod (motor SDR)
    ↓ (multicast / control)
UberSDR (backend en Go)
    ↓ (WebSocket / HTTP)
Clientes (web, Android, escritorio)

---

## 3. Responsabilidades principales

### 3.1 Gestión de sesiones

Cada usuario tiene:

- frecuencia, modo, ancho de banda
- canal asociado en radiod (SSRC)
- audio y espectro

Archivo principal:
- session.go

---

### 3.2 Integración con radiod

- multicast UDP
- control TLV

Archivo principal:
- radiod.go

---

### 3.3 WebSockets

Audio/control:
/ws

Espectro:
/ws/user-spectrum

Archivos:
- websocket.go
- user_spectrum_websocket.go

---

### 3.4 Flujo de audio

radiod → RTP → audio.go → sesión → websocket → cliente

---

### 3.5 Flujo de espectro

radiod → STATUS → user_spectrum.go → sesión → websocket → cliente

---

### 3.6 Frontend

static/

Archivos importantes:
- app.js
- spectrum-display.js
- websocket-manager.js

---

### 3.7 Decodificadores

- decoder*.go
- audio_extensions/

---

### 3.8 Administración

admin.go

---

## 4. Modelo mental

radiod es el motor SDR  
UberSDR es la capa de aplicación

---

## 5. Notas

- Parte del mapeo de frecuencia está en frontend
- Backend y frontend deben coincidir
- El diseño no es completamente modular