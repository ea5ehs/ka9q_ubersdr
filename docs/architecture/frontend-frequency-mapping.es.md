# Mapeo de Frecuencia en Frontend

## Estado
Parcialmente confirmado (requiere validación en ejecución)

## Fuente
Análisis asistido con Codex + inspección manual

## Última revisión
2026-04-11

---

## 1. Visión general

La frecuencia mostrada se calcula en el frontend.

El backend proporciona:

- frecuencia central
- número de bins
- ancho de bin

---

## 2. Fórmula principal

startFreq = centerFreq - totalBandwidth / 2  
freq = startFreq + (x / width) * totalBandwidth

---

## 3. Tooltip

Funciones:

- updateTooltip()
- updateLineGraphTooltip()

Calculan la frecuencia desde la posición del cursor.

---

## 4. Click para sintonía

Handlers:

- eventos de ratón en canvas

Proceso:

- x relativo al canvas
- freq calculada geométricamente

Importante:

- no usa bins
- no usa contenido real

---

## 5. Mapeo de bins

binIndex = floor((x / width) * binCount)

Asume:

- izquierda = baja frecuencia
- derecha = alta frecuencia

---

## 6. Caminos de datos

### JSON

- reordena bins (unwrap)

### Binario

- puede mantener orden FFT

---

## 7. Riesgos de divergencia

### Geometría vs contenido

Geometría:

- basada en parámetros backend

Contenido:

- depende del orden real de datos

---

### JSON vs binario

- JSON corrige orden
- binario puede no hacerlo

---

### Click vs contenido

- el click usa geometría, no señal real

---

### Movimiento predictivo

La UI puede mover:

- escala
- cursor

sin mover datos reales

---

## 8. Autoridad

- Geometría: backend
- Render: frontend
- Interacción: frontend

---

## 9. Notas

- El backend no define la visualización final
- El frontend es determinante
- Desajustes provocan errores visuales