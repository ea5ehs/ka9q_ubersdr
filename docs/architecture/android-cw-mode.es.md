# Modo CW en Android

## Estado
Documentación técnica del comportamiento actual

## Última revisión
2026-04-17

## Nota temporal: contención de rotación

- Se ha detectado un bug al rotar el dispositivo a horizontal: la UI vuelve a un estado inicial sin `waterfall` mientras el audio previo sigue activo en segundo plano.
- Si después se inicia de nuevo la reproducción, pueden quedar dos audios solapados: el anterior no liberado y el nuevo de la sesión recreada.
- Decisión temporal y reversible: forzar la `MainActivity` a modo `portrait` en el `AndroidManifest.xml` hasta corregir correctamente el soporte horizontal y el ciclo de vida asociado.

## 1. Descripción general del modo CW

- El cliente Android representa `CW` usando la frecuencia real `frequencyHz` y un pasabanda simétrico alrededor de esa frecuencia.
- En `CWU` y `CWL`, el ancho por defecto derivado hoy es `-250..250 Hz`.
- El modo `CW` reutiliza el espectro RF ya disponible en el cliente para visualización y para una lógica experimental de laboratorio.
- Existe una vía experimental del `Feature Lab` para seleccionar un candidato RF en `CW` y una acción manual `A` para aplicar una única corrección de sintonía.

## 2. Modelo de sintonía (`frequencyHz` + pasabanda)

- `frequencyHz` es la frecuencia de sintonía real mantenida en `RadioUiState`.
- El espectro visible se expresa con:
  - `startFreq = centerFreq - totalBandwidth / 2`
  - `endFreq = centerFreq + totalBandwidth / 2`
- En `CW`, la representación visual del pasabanda no usa directamente `bandwidthLowHz` y `bandwidthHighHz` como límites asimétricos.
- El cliente calcula primero el ancho absoluto:
  - `passbandWidthHz = abs(bandwidthHighHz - bandwidthLowHz)`
- A partir de ese ancho, el pasabanda visual en `CW` queda centrado en `frequencyHz`:
  - `visualLowOffsetHz = -passbandWidthHz / 2`
  - `visualHighOffsetHz = passbandWidthHz / 2`
- El rango efectivo del pasabanda en frecuencia absoluta queda entonces como:
  - `passbandStartFrequencyHz = frequencyHz + visualLowOffsetHz`
  - `passbandEndFrequencyHz = frequencyHz + visualHighOffsetHz`

## 3. Control de ancho CW (`bandwidthLowHz` / `bandwidthHighHz`)

- `bandwidthLowHz` y `bandwidthHighHz` forman parte del estado actual del cliente.
- Para `CWU` y `CWL`, `RadioViewModel` fija hoy por defecto:
  - `bandwidthLowHz = -250`
  - `bandwidthHighHz = 250`
- En `CW`, el control de ancho visible opera sobre el ancho total de esa ventana simétrica.
- El detalle exacto de cómo se persiste o restaura el ajuste de ancho por sesión queda `pendiente de confirmar`.

## 4. Uso del espectro RF en CW

- El cliente recibe por sesión:
  - `centerFreq`
  - `binCount`
  - `binBandwidth`
  - `totalBandwidth`
  - fila `SPEC` reconstruida en `latestSpectrumRow`
- `latestSpectrumRow` está disponible como `ByteArray`.
- El contenido binario del `SPEC` se corrige visualmente con `unwrap` de media anchura para presentar izquierda = baja frecuencia y derecha = alta frecuencia.
- La conversión conceptual de bin visual a frecuencia absoluta es:
  - `startFreq = centerFreq - totalBandwidth / 2`
  - `freqHz = startFreq + (binIndex / binCount) * totalBandwidth`

## 5. Feature Lab `CW-001` (detección de pico y candidato)

- Existe una implementación experimental mínima para `CW-001` en `RadioScreen`.
- Alcance actual:
  - solo se ejecuta en `CW`
  - usa el `SPEC` RF ya disponible
  - no usa todavía DSP local de audio
  - no modifica por sí sola la sintonía
- La ventana de búsqueda actual coincide con el pasabanda seleccionado por el usuario.
- La búsqueda:
  - convierte `windowStartHz..windowEndHz` a `startBin..endBin`
  - recorre solo esos bins
  - toma el bin de mayor valor `uint8`
  - convierte ese bin visual a una frecuencia absoluta candidata
- Para respetar la orientación visual actual, la lectura del `ByteArray` compensa el orden crudo con un desplazamiento de media anchura antes de evaluar el máximo.
- Salida experimental actual:
  - bin inicial y final de la ventana
  - bin de pico
  - valor de pico
  - `candidateFrequencyHz`

## 6. Botón `A` (autoajuste manual)

- Existe un botón manual `A` visible solo en `CW`.
- Está situado a la derecha de la línea de control de ancho `CW`.
- Su función actual es estrictamente manual:
  - toma la frecuencia candidata RF ya calculada
  - hace una sola llamada a `tune()`
- Validación actual:
  - si no hay candidato válido, no actúa
  - el candidato debe seguir dentro del pasabanda `CW` actual
  - el candidato debe quedar dentro del rango válido general del cliente
- No existe seguimiento continuo.
- No existe autotune automático permanente.

## 7. Limitaciones actuales

- El candidato RF se basa en máximo simple dentro de la ventana actual.
- Gana la señal más fuerte dentro del ancho seleccionado, no necesariamente la señal `CW` deseada.
- El `SPEC` usado en Android actual es `binary8` cuantizado.
- No hay todavía heurísticas contra:
  - portadora continua
  - múltiples señales en ventana
  - interferente fuerte cercano
- No consta todavía una métrica de confianza más allá del pico detectado.
- No consta integración con DSP local de audio para validar el tono útil.

## 8. Interacción actual

- selección de modo `CWU` o `CWL`
- visualización del pasabanda `CW` centrado en `frequencyHz`
- ajuste manual del ancho `CW`
- visualización de telemetría experimental `CW Lab`
- pulsación manual del botón `A` para una única corrección de sintonía
