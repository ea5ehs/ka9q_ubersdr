# Frontend Frequency Mapping

## Status
Partially confirmed (requires runtime validation)

## Source
Codex-assisted analysis + manual inspection

## Last reviewed
2026-04-11

---

## 1. Overview

Frequency display and interaction are handled in the frontend.

The backend provides geometry parameters:

- center frequency
- bin count
- bin bandwidth

---

## 2. Core Formula

Used across the UI:

startFreq = centerFreq - totalBandwidth / 2  
freq = startFreq + (x / width) * totalBandwidth

---

## 3. Tooltip Frequency

Functions:

- updateTooltip()
- updateLineGraphTooltip(x, y)

These compute frequency directly from pixel position.

---

## 4. Click-to-Tune

Handlers:

- setupMouseHandlers()
- setupLineGraphMouseHandlers()

Process:

- x = mouse position relative to canvas
- freq = startFreq + (x / width) * totalBandwidth

Important:

- uses geometry only
- does not use FFT bins

---

## 5. Bin Mapping

Rendering uses:

binIndex = floor((x / width) * binCount)

Assumption:

- left = lowest frequency
- right = highest frequency

---

## 6. Data Paths

Two main spectrum paths exist:

### JSON path

- reorders bins from FFT order to low-to-high

### Binary path

- may keep FFT order unchanged

---

## 7. Divergence Risks

### Geometry vs Content

Geometry uses:

- centerFreq
- totalBandwidth

Content depends on:

- order of spectrumData

---

### JSON vs Binary

- JSON unwraps bins
- Binary may not

This can cause visual misalignment.

---

### Click vs Display

- click uses geometry only
- ignores actual signal position

---

### Predicted Movement

UI may shift:

- frequency scale
- cursor

without shifting actual data

---

## 8. Authority

- Geometry: backend config
- Rendering: frontend
- Interaction: frontend

---

## 9. Notes

- Backend helpers are not authoritative for display
- Frontend defines final mapping
- Mismatch leads to visual offset