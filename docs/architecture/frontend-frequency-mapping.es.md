# Mapeo de Frecuencia en Frontend

## Estado
Confirmado en el cliente Android actual

## Fuente
Análisis asistido con Codex + inspección manual

## Última revisión
2026-04-16

---

## 1. Visión general

La frecuencia mostrada se calcula en el frontend.

El backend proporciona:

- frecuencia central
- número de bins
- ancho de bin

Estado funcional hoy validado en Android:

- waterfall correcto tras `unwrap`
- tap en waterfall para sintonía
- pan con un dedo que cambia la frecuencia real
- edición manual de frecuencia desde el display
- zoom estable solo mediante `-`, `+`, `MAX`, `MIN`
- gesto de pinza descartado por ahora

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

Regla operativa:

- si el spectrum binario llega en orden FFT crudo, el cliente no debe pintarlo tal cual
- antes de dibujar izquierda->derecha en frecuencia debe aplicar `unwrap` o `fftshift` de media anchura
- la geometría puede ser correcta y aun así la imagen ser engañosa si falta este paso

Síntoma típico:

- una señal parece cambiar de lado al hacer zoom aunque `centerFreq`, eje, tap y cursor usen el mapping correcto

Hallazgo crítico validado:

- `binary8` llega en orden FFT crudo
- el problema no estaba en la geometría de frecuencia sino en el orden visual de bins
- el cliente Android actual corrige esto aplicando `unwrap` de media anchura antes de pintar

Bug corregido asociado:

- sin `unwrap`, la geometría de frecuencia podía ser correcta mientras la imagen quedaba desplazada o invertida

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

## 8. Pan y sintonía operativos

Regla funcional actual validada:

- el drag horizontal con un dedo sobre el waterfall no mueve solo un viewport abstracto
- el drag actualiza la `frequencyHz` real
- la vista del spectrum acompaña de forma coherente con esa frecuencia

Conversión usada:

- `deltaHz` se deriva de `deltaX / width * totalBandwidth`
- el signo del delta está ajustado para que:
  - arrastrar a la derecha aumente frecuencia
  - arrastrar a la izquierda disminuya frecuencia

Clamp confirmado:

- el clamp se hace solo contra los límites válidos globales
- no debe anclarse a una referencia fija ni a un centro agotable dentro del gesto

Bug corregido:

- el pan se bloqueaba cuando reutilizaba referencias fijas o estado intermedio ambiguo
- la solución funcional actual es tuning incremental por gesto, no pan visual puro

## 9. Zoom efectivo y decisión de UX

La geometría del mapping admite zoom continuo, pero el zoom real servido por backend no es visualmente continuo:

- el backend puede normalizar `binBandwidth`
- puede ajustar `binCount`
- por tanto cambian `totalBandwidth` y el rango visible en saltos discretos

Conclusión de UX actual:

- mantener botones de zoom
- no usar pinza de dos dedos por ahora

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

## 10. Regla superior y ticks adaptativos

Estado actual validado en Android:

- la regla superior usa el mismo mapping geométrico que tap, hover, cursor y waterfall
- el rango visible se calcula desde:
  - `startFreq = centerFreq - totalBandwidth / 2`
  - `endFreq = centerFreq + totalBandwidth / 2`
- los ticks se generan de forma adaptativa según el ancho visible y el ancho en pantalla
- existen ticks mayores y menores
- la etiqueta visible se limita a ticks mayores no solapados

Reglas de render:

- no forzar etiquetas en los extremos solo por estar en el borde
- evitar solapes entre etiquetas contiguas
- no desacoplar la regla de la geometría común de frecuencia

Implicación:

- si cambia `binBandwidth`, cambia `totalBandwidth`
- si cambia `totalBandwidth`, cambian el rango visible y la selección de ticks
- la regla no debe usar una escala independiente ni hardcodeada

## 11. Estado actual y límites

Estado actual validado en Android:

- tap-to-tune funcional
- hover funcional
- cursor de sintonía coherente con el eje
- zoom/pan funcionales
- waterfall correcto tras fix de `unwrap`
- regla superior adaptativa operativa
- etiquetas sin solape visible
- no se fuerzan extremos del eje cuando no encajan

Limitaciones actuales:

- no hay indicador visual de ancho de banda todavía
- la UI sigue siendo técnica y no final
