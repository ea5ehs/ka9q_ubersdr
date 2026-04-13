# UberSDR Connection Flow

## Status
Confirmed (based on repository inspection and Codex-assisted analysis)

## Source
Manual inspection + Codex-assisted exploration

## Last reviewed
2026-04-11

---

## 1. Overview

Each user connection follows a three-step process:

1. HTTP preflight (`/connection`)
2. Audio/control WebSocket (`/ws`)
3. Spectrum WebSocket (`/ws/user-spectrum`)

A shared `user_session_id` links all parts of the session.

---

## 2. Preflight Request (/connection)

### Frontend

Triggered from:

- `static/websocket-manager.js`
- `WebSocketManager.checkConnection()`
- `WebSocketManager.connect()`

Also used by:

- `static/spectrum-display.js`
- `SpectrumDisplay.connect()`

### Backend

- Route registered in `main.go`
- Handler: `handleConnectionCheck(...)`

### Responsibilities

- Validate access (password if required)
- Apply rate limiting
- Check bans
- Return:
  - `client_ip`
  - timeout info
  - allowed modes

---

## 3. Audio + Control WebSocket (/ws)

### Frontend

Created in:

- `static/websocket-manager.js`
- `WebSocketManager.connect()`

Example URL parameters:

- `frequency`
- `mode`
- `bandwidthLow`
- `bandwidthHigh`
- `user_session_id`
- `format` (opus / pcm)

---

### Backend

Handler:

- `websocket.go`
- `HandleWebSocket(...)`

### Initialization steps

1. Validate parameters
2. Create session:

   - `session.go`
   - `CreateSessionWithBandwidthAndPassword(...)`

3. Create radiod channel:

   - `radiod.go`
   - `CreateChannelWithSquelch(...)`

---

### Runtime responsibilities

- Receive control messages:
  - tune
  - mode change
  - bandwidth update
- Stream audio:
  - Opus or PCM

---

## 4. Spectrum WebSocket (/ws/user-spectrum)

### Frontend

- `static/spectrum-display.js`
- `SpectrumDisplay.connect()`

---

### Backend

Handler:

- `user_spectrum_websocket.go`
- `HandleSpectrumWebSocket(...)`

### Initialization

Creates spectrum-only session:

- `session.go`
- `CreateSpectrumSessionWithUserIDAndPassword(...)`

Creates radiod spectrum channel:

- `radiod.go`
- `CreateSpectrumChannel(...)`

---

### Runtime responsibilities

- Handle control messages:
  - zoom
  - pan
  - reset
- Stream spectrum data

---

## 5. Shared Session Linking

Both WebSockets use:
