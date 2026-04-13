# Purpose

Document the exact binary audio protocol sent by the backend on `/ws`, in a form that a native Android client can implement directly.

# Endpoint

- WebSocket endpoint: `/ws`
- Query parameters relevant to binary audio output:
  - `format=opus`
  - `format=pcm-zstd`
  - `version=1`
  - `version=2`

The backend selects the output path in `WebSocketHandler.streamAudio(...)` in `websocket.go`.

# Backend source of truth

Confirmed backend functions that define the `/ws` binary audio protocol:

- `WebSocketHandler.streamAudio(...)` in `websocket.go`
- `NewOpusEncoderForClient(...)` in `opus_support.go`
- `OpusEncoderWrapper.EncodeBinary(...)` in `opus_support.go`
- `NewPCMBinaryEncoderWithVersionAndLevel(...)` in `pcm_binary.go`
- `PCMBinaryEncoder.EncodePCMPacketWithSignalQuality(...)` in `pcm_binary.go`
- `PCMBinaryEncoder.buildFullHeaderPacket(...)` in `pcm_binary.go`
- `PCMBinaryEncoder.buildMinimalHeaderPacket(...)` in `pcm_binary.go`
- `AudioReceiver.routeAudio(...)` in `audio.go`

Additional source for the audio timestamp and sample rate values:

- `AudioReceiver.routeAudio(...)` writes `AudioPacket.GPSTimeNs`
- `AudioReceiver.routeAudio(...)` writes `AudioPacket.SampleRate`

# Binary audio frame path

The `/ws` handler sends binary audio in `WebSocketHandler.streamAudio(...)`.

There are two protocol families:

- Opus path:
  - raw Opus payload with a small custom header
  - header is built directly in `WebSocketHandler.streamAudio(...)`
- PCM path:
  - custom PCM packet format with either a full header or a minimal header
  - packet is built by `PCMBinaryEncoder`
  - entire packet is zstd-compressed before WebSocket transmission

Runtime selection in `WebSocketHandler.streamAudio(...)`:

- If `format == "opus"`:
  - backend creates an encoder with `NewOpusEncoderForClient(...)`
  - raw PCM is encoded by `OpusEncoderWrapper.EncodeBinary(...)`
  - `streamAudio(...)` prepends a custom header and sends the result
- If `format == "pcm-zstd"`:
  - backend creates a `PCMBinaryEncoder` with `NewPCMBinaryEncoderWithVersionAndLevel(...)`
  - raw PCM is packed by `EncodePCMPacketWithSignalQuality(...)`
  - packet is zstd-compressed inside `EncodePCMPacketWithSignalQuality(...)`

Important fallback behavior in `WebSocketHandler.streamAudio(...)`:

- If Opus encoder creation fails, backend falls back to `pcm-zstd`
- If current mode is IQ (`iq`, `iq48`, `iq96`, `iq192`, `iq384`), backend forces lossless `pcm-zstd` even if `format=opus` was requested

# Opus output path

## Backend functions

- `NewOpusEncoderForClient(...)` in `opus_support.go`
- `OpusEncoderWrapper.EncodeBinary(...)` in `opus_support.go`
- frame header assembly in `WebSocketHandler.streamAudio(...)` in `websocket.go`

## Payload

`OpusEncoderWrapper.EncodeBinary(...)` returns raw Opus bytes.

The backend prepends a custom binary header before the Opus payload. There is no magic number field in this header.

## Endianness

- integer fields: little-endian
- float fields: IEEE-754 `float32`, written as little-endian bits

## Opus frame layout, protocol version 1

Built in `WebSocketHandler.streamAudio(...)`.

Offset | Size | Type | Field
---|---:|---|---
0 | 8 | `uint64 LE` | GPS timestamp in nanoseconds
8 | 4 | `uint32 LE` | sample rate in Hz
12 | 1 | `uint8` | channel count
13 | N | `bytes` | raw Opus payload

Total header size: `13` bytes

## Opus frame layout, protocol version 2

Built in `WebSocketHandler.streamAudio(...)`.

Offset | Size | Type | Field
---|---:|---|---
0 | 8 | `uint64 LE` | GPS timestamp in nanoseconds
8 | 4 | `uint32 LE` | sample rate in Hz
12 | 1 | `uint8` | channel count
13 | 4 | `float32 LE` | baseband power in dBFS
17 | 4 | `float32 LE` | noise density in dBFS
21 | N | `bytes` | raw Opus payload

Total header size: `21` bytes

## Notes for Android

- The Android client must parse the custom header before passing the remaining bytes to the Opus decoder.
- `sampleRate` is included in every Opus frame.
- `channels` is included in every Opus frame.
- The backend creates the Opus encoder as mono in `NewOpusEncoderForClient(...)`:
  - `opus.NewEncoder(sampleRate, 1, opus.AppVoIP)`
- The header still includes `channels`, so the client should trust the transmitted field, but current backend behavior strongly suggests Opus payloads are mono.

# PCM output path

## Backend functions

- `NewPCMBinaryEncoderWithVersionAndLevel(...)` in `pcm_binary.go`
- `PCMBinaryEncoder.EncodePCMPacketWithSignalQuality(...)` in `pcm_binary.go`
- `PCMBinaryEncoder.buildFullHeaderPacket(...)` in `pcm_binary.go`
- `PCMBinaryEncoder.buildMinimalHeaderPacket(...)` in `pcm_binary.go`

## Compression

Confirmed behavior from `PCMBinaryEncoder.EncodePCMPacketWithSignalQuality(...)`:

- backend first builds the PCM packet
- backend then compresses the entire packet with zstd
- the WebSocket binary message contains the zstd-compressed packet bytes

Android must:

1. zstd-decompress the entire WebSocket message
2. parse the resulting PCM packet header
3. extract PCM payload
4. convert PCM payload to the format expected by Android playback code

## PCM magic values

Defined in `pcm_binary.go`:

- full header magic: `0x5043` (`"PC"`)
- minimal header magic: `0x504D` (`"PM"`)

Both are written with `binary.LittleEndian.PutUint16(...)`.

## PCM payload format

Confirmed in `pcm_binary.go` and `opus_support.go`:

- PCM payload bytes are raw PCM samples
- comments in `pcm_binary.go` describe them as `big-endian int16 samples from radiod`
- `OpusEncoderWrapper.EncodeBinary(...)` reads PCM input using `binary.BigEndian.Uint16(...)`

Android should therefore treat PCM payload as:

- signed 16-bit PCM
- big-endian byte order

## PCM full header layout, protocol version 1

Built in `PCMBinaryEncoder.buildFullHeaderPacket(...)`.

Offset | Size | Type | Field
---|---:|---|---
0 | 2 | `uint16 LE` | magic `0x5043` (`"PC"`)
2 | 1 | `uint8` | protocol version
3 | 1 | `uint8` | format type
4 | 8 | `uint64 LE` | GPS timestamp in nanoseconds
12 | 8 | `uint64 LE` | wall-clock timestamp in milliseconds
20 | 4 | `uint32 LE` | sample rate in Hz
24 | 1 | `uint8` | channel count
25 | 4 | `uint32 LE` | reserved
29 | N | `bytes` | PCM payload

Full header size, v1: `29` bytes

Format type values defined in `pcm_binary.go`:

- `0 = PCMFormatUncompressed`
- `1 = PCMFormatOpus`
- `2 = PCMFormatZstd`

Current `/ws` PCM path uses `PCMFormatZstd` because `PCMBinaryEncoder` is created with compression enabled.

## PCM full header layout, protocol version 2

Built in `PCMBinaryEncoder.buildFullHeaderPacket(...)`.

Offset | Size | Type | Field
---|---:|---|---
0 | 2 | `uint16 LE` | magic `0x5043` (`"PC"`)
2 | 1 | `uint8` | protocol version
3 | 1 | `uint8` | format type
4 | 8 | `uint64 LE` | GPS timestamp in nanoseconds
12 | 8 | `uint64 LE` | wall-clock timestamp in milliseconds
20 | 4 | `uint32 LE` | sample rate in Hz
24 | 1 | `uint8` | channel count
25 | 4 | `float32 LE` | baseband power in dBFS
29 | 4 | `float32 LE` | noise density in dBFS
33 | 4 | `uint32 LE` | reserved
37 | N | `bytes` | PCM payload

Full header size, v2: `37` bytes

## PCM minimal header layout

Built in `PCMBinaryEncoder.buildMinimalHeaderPacket(...)`.

Offset | Size | Type | Field
---|---:|---|---
0 | 2 | `uint16 LE` | magic `0x504D` (`"PM"`)
2 | 1 | `uint8` | protocol version
3 | 8 | `uint64 LE` | GPS timestamp in nanoseconds
11 | 2 | `uint16 LE` | reserved
13 | N | `bytes` | PCM payload

Minimal header size: `13` bytes

Important consequence:

- minimal header packets do not carry sample rate
- minimal header packets do not carry channel count
- minimal header packets do not carry baseband power
- minimal header packets do not carry noise density

So the Android client must keep the latest metadata from the most recent full header packet.

## When full vs minimal headers are used

Confirmed from `PCMBinaryEncoder.EncodePCMPacketWithSignalQuality(...)`:

Full header is sent when:

- first packet
- sample rate changed
- channel count changed
- caller sets `forceFullHeader = true`

Otherwise minimal header is sent.

Confirmed call site behavior in `WebSocketHandler.streamAudio(...)`:

- non-IQ PCM path calls `EncodePCMPacketWithSignalQuality(..., forceFullHeader = true)`
  - effect: full header every packet
- IQ PCM path calls `EncodePCMPacketWithSignalQuality(..., forceFullHeader = false)`
  - effect: minimal headers may be used after metadata stabilizes

# Protocol v2 fields

Protocol version 2 adds signal quality fields.

## Confirmed v2 fields

- `basebandPower`
- `noiseDensity`

Source:

- `WebSocketHandler.streamAudio(...)` in `websocket.go`
- `PCMBinaryEncoder.buildFullHeaderPacket(...)` in `pcm_binary.go`

Serialization:

- type: `float32`
- encoding: IEEE-754 bits written in little-endian order

Value source:

- `channelStatus.BasebandPower`
- `channelStatus.NoiseDensity`

Defaults when unavailable:

- `-999.0`

The backend may add configured display gain adjustments before serializing these fields.

# Android parsing requirements

## Common

The Android client must branch by requested output path.

It cannot assume a single frame format for all `/ws` binary messages.

## If using `format=opus`

For each WebSocket binary message:

1. Read the first 13 or 21 bytes depending on negotiated protocol version.
2. Parse:
   - timestamp nanoseconds
   - sample rate
   - channels
   - v2 only: baseband power, noise density
3. Pass the remaining bytes to the Opus decoder as raw Opus data.

Practical parse rules:

- version 1 header length: `13`
- version 2 header length: `21`
- payload begins immediately after that header
- there is no magic field in Opus frames

## If using `format=pcm-zstd`

For each WebSocket binary message:

1. zstd-decompress the message.
2. Read first 2 bytes as little-endian `uint16` magic.
3. If magic is `0x5043`, parse a full PCM header.
4. If magic is `0x504D`, parse a minimal PCM header.
5. Extract PCM payload after the header.
6. Interpret payload as big-endian signed 16-bit PCM.
7. Reuse last known sample rate and channel count when packet type is minimal.

Practical parse rules:

- full header v1 length: `29`
- full header v2 length: `37`
- minimal header length: `13`
- determine version from byte `2`
- determine format type from byte `3` only on full-header packets

## Metadata state the Android client should keep

For robust playback on PCM path, client should cache:

- last full-header sample rate
- last full-header channel count
- last full-header protocol version
- last seen baseband power and noise density if UI needs them

## Timestamp handling

Confirmed timestamp fields:

- primary timestamp: GPS-synchronized Unix time in nanoseconds
- PCM full-header secondary timestamp: milliseconds version of the same time

Android should treat the nanosecond timestamp as the primary timing field.

# Confirmed facts

- `/ws` binary audio frames are sent by `WebSocketHandler.streamAudio(...)`.
- There are two distinct binary output paths: Opus and PCM-zstd.
- Opus frames have a custom header built directly in `streamAudio(...)`.
- PCM-zstd frames are built by `PCMBinaryEncoder`.
- PCM-zstd packets are zstd-compressed after header construction.
- PCM full-header packets use magic `0x5043` (`"PC"`).
- PCM minimal-header packets use magic `0x504D` (`"PM"`).
- Protocol version 2 adds `basebandPower` and `noiseDensity`.
- Opus v1 header size is `13` bytes.
- Opus v2 header size is `21` bytes.
- PCM full-header v1 size is `29` bytes.
- PCM full-header v2 size is `37` bytes.
- PCM minimal-header size is `13` bytes.
- Opus frames always include sample rate and channel count.
- PCM minimal-header frames do not include sample rate or channel count.
- PCM payload is documented and consumed as big-endian 16-bit PCM.
- IQ modes force PCM-zstd even if Opus was requested.

# Open questions

- The comments in `pcm_binary.go` label one timestamp field as `RTP timestamp`, but the implementation writes GPS-synchronized Unix time in nanoseconds. The implementation should be treated as authoritative.
- `channels` is serialized in Opus headers, but `NewOpusEncoderForClient(...)` currently creates a mono Opus encoder. Current backend behavior strongly suggests Opus payloads are mono, even if future changes could alter that.
