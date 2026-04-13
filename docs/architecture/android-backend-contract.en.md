# Android Backend Contract

## Status
Partially confirmed (based on repository inspection and Codex-assisted analysis)

## Source
Manual inspection + Codex-assisted exploration

## Last reviewed
2026-04-11

---

## 1. Purpose

This document defines the minimum backend contract needed to build a native Android client for UberSDR.

The initial Android client should focus on:

- connection preflight
- audio/control WebSocket
- tuning
- audio playback

Spectrum support can be added later.

---

## 2. Minimum Client Scope

The first Android client does not need to implement:

- KiwiSDR compatibility
- waterfall rendering
- browser-specific UI logic
- admin features
- decoder dashboards

The first milestone is:

1. connect
2. tune
3. receive audio
4. play audio

---

## 3. Preflight Endpoint

## Endpoint

`POST /connection`

## Purpose

Checks whether a client is allowed to connect before opening WebSockets.

## Backend handler

- `main.go`
- `handleConnectionCheck(...)`

## Responsibilities

- validate password or bypass password if required
- apply rate limiting
- check bans
- return client metadata and allowed connection parameters

## Expected client behavior

Android should call `/connection` before opening `/ws`.

The client should be prepared to handle:

- success
- rejection
- timeout-related information
- connection policy restrictions

---

## 4. Audio and Control WebSocket

## Endpoint

`/ws`

## Purpose

This is the main Android client endpoint.

It is used for both:

- control messages
- audio streaming

## Backend handler

- `websocket.go`
- `HandleWebSocket(...)`

## Query parameters

Observed parameters include:

- `frequency`
- `mode`
- `bandwidthLow`
- `bandwidthHigh`
- `user_session_id`
- `format`
- `version`

## Notes

A minimal Android client should provide at least:

- frequency
- mode
- bandwidthLow
- bandwidthHigh
- user_session_id
- format

Likely values:

- `format=opus` for normal audio operation
- `version=2` if protocol v2 is supported by the client

---

## 5. Session Model

The backend creates a per-user session for the audio/control WebSocket.

## Relevant files

- `session.go`
- `websocket.go`
- `radiod.go`

## Session responsibilities

A session holds:

- tuned frequency
- mode
- bandwidth edges
- assigned radiod channel
- audio routing state

The backend creates the session and links it to a radiod channel during WebSocket initialization.

---

## 6. Control Messages

Control messages are sent over `/ws`.

## Known message type

### tune

Observed in frontend flow and backend handler.

## Path

Frontend equivalent:

- `static/app.js`
- `tune()`

Backend handling:

- `websocket.go`
- `HandleWebSocket(...)`
- `case "tune"`

## Typical fields

- `type`
- `frequency`
- `mode`
- `bandwidthLow`
- `bandwidthHigh`

## Effect

The backend:

1. validates requested values
2. updates session state
3. updates the radiod channel

Backend update path:

- `session.go`
- `UpdateSessionWithEdges(...)`

which calls:

- `radiod.go`
- `UpdateChannel(...)`
- `UpdateChannelWithSquelch(...)`

## radiod frequency TLV

Observed TLV tags:

- `0x21` = radio frequency
- `0x27` = low edge
- `0x28` = high edge

---

## 7. Audio Output

Audio is returned over the same `/ws` WebSocket as binary frames.

## Backend path

- `audio.go`
- `websocket.go`
- `streamAudio(...)`

## Possible output formats

### Opus

Recommended default for Android.

Advantages:

- lower bandwidth
- standard mobile-friendly codec

### PCM

Fallback or special modes.

### Lossless PCM for IQ modes

IQ-related modes may force lossless transport.

## Recommendation for Android MVP

Implement Opus first.

---

## 8. Audio Source Path

Audio flow is:

`radiod -> RTP multicast -> audio.go -> session.AudioChan -> websocket.go -> Android client`

## Relevant functions

- `audio.go`
- `receiveLoop()`
- `routeAudio(...)`

- `websocket.go`
- `streamAudio(...)`

---

## 9. Android Client Responsibilities

The Android client should implement:

### Connection

- call `POST /connection`
- open `/ws`
- provide initial tuning parameters

### Control

- send `tune` messages when frequency or mode changes

### Audio

- receive binary WebSocket frames
- decode Opus
- send decoded PCM to Android audio output

### Session handling

- keep track of `user_session_id`
- reconnect cleanly if socket drops

---

## 10. Error Handling

The Android client should expect:

- HTTP rejection during `/connection`
- WebSocket close during or after setup
- invalid parameter rejection
- server-side limits or bans
- unsupported mode or bandwidth combinations

The exact error payload format should be documented separately after runtime inspection.

---

## 11. Reconnection Strategy

Recommended behavior:

1. detect WebSocket close or failure
2. repeat `/connection`
3. open a new `/ws`
4. restore last known tuning state

The client should not assume the old session remains valid after reconnect.

---

## 12. Out of Scope for MVP

Not required in the first Android milestone:

- `/ws/user-spectrum`
- waterfall rendering
- binary spectrum parsing
- decoder integration
- admin APIs
- browser-specific timing hacks

---

## 13. Open Questions

The following items should be verified directly against runtime traffic or code inspection before implementation is finalized:

1. exact JSON shape of `tune` messages accepted by `/ws`
2. exact binary frame structure for Opus audio
3. whether protocol version 2 is mandatory or optional
4. exact server responses for connection errors
5. heartbeat or ping/pong expectations
6. whether authentication tokens or passwords are ever required for normal users

---

## 14. Recommended Next Document

After this file, the next useful document is:

`android-audio-protocol.en.md`

That document should describe:

- binary frame structure
- Opus handling
- timestamps
- sample rate
- channel count
- Android playback considerations