# Flujo de Audio y Espectro en UberSDR

## Estado
Confirmado (basado en inspección del repositorio y análisis asistido con Codex)

## Fuente
Inspección manual + exploración asistida con Codex

## Última revisión
2026-04-11

---

## 1. Visión general

UberSDR gestiona dos flujos:

- Audio (RTP → WebSocket → cliente)
- Espectro (STATUS → bins → WebSocket → cliente)

Ambos provienen de radiod.

---

## 2. Flujo de audio

### Origen

Generado por radiod (RTP multicast)

---

### Backend

Archivo: audio.go

- receiveLoop()
- routeAudio(...)

Funciones:
- recibir RTP
- enrutar por SSRC

---

### Sesión

Archivo: session.go

- mapear SSRC
- enviar a AudioChan

---

### Streaming

Archivo: websocket.go

- streamAudio(...)
- codificar y enviar

---

### Cliente

- decodificar
- reproducir audio

---

## 3. Flujo de espectro

### Origen

FFT generado por radiod

---

### Backend

Archivo: user_spectrum.go

- pollLoop()
- parseStatusPacket(...)

---

### Distribución

- distributeSpectrum(...)
- enviar a SpectrumChan

---

### Streaming

Archivo: user_spectrum_websocket.go

- streamSpectrum(...)

---

## 4. Geometría

totalBandwidth = binCount * binBandwidth

low = centerFreq - totalBandwidth / 2  
high = centerFreq + totalBandwidth / 2

---

## 5. Resumen

Audio:
radiod → backend → sesión → WebSocket → cliente

Espectro:
radiod → backend → sesión → WebSocket → cliente

---

## 6. Notas

- Audio y espectro independientes
- SSRC enlaza datos
- FFT viene de radiod