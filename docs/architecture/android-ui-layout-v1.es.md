# Android UI Layout v1

## Estado
Confirmado en el cliente Android actual

## Última revisión
2026-04-17

## 1. Objetivo

El layout actual prioriza:

- waterfall visible y utilizable
- frecuencia protagonista
- controles rápidos compactos
- ahorro de altura vertical
- espacio reservado para selección rápida de bandas HF

## 2. Estructura visual actual

Orden vertical validado:

1. barra superior compacta con menú y power
2. waterfall/spectrum con regla superior integrada
3. franja compacta de sintonía
4. línea rápida de acciones
5. controles inferiores funcionales
6. grid compacto de bandas

## 3. Franja compacta de sintonía

La franja inmediatamente bajo el waterfall integra en una sola línea:

- botón triangular de bajar frecuencia
- frecuencia principal centrada y editable al tocar
- modo actual en la misma línea
- botón triangular de subir frecuencia
- selector compacto de `step`

Restricciones funcionales ya validadas:

- la frecuencia no se parte en varias líneas
- el modo no ocupa una línea independiente
- los botones laterales hacen lo mismo que la sintonía por pasos previa
- el selector mantiene los valores:
  - `10`
  - `100`
  - `1k`
  - `5k`
  - `10k`

## 4. Línea rápida de controles

Debajo de la franja de sintonía existe una línea rápida compacta con solo:

- `MIN`
- `-`
- `+`
- `MAX`
- `C`

Comportamiento validado:

- `MIN`: restaura la vista más abierta válida con clamp seguro
- `-`: zoom out
- `+`: zoom in
- `MAX`: zoom máximo válido en una sola acción, centrado en la frecuencia actual
- `C`: centra el spectrum en la frecuencia actual

Regla de diseño:

- no añadir texto auxiliar ni subtítulos en esta línea
- los botones deben caber en horizontal en móvil

## 5. Controles inferiores preservados

El rediseño actual no elimina los controles ya funcionales situados más abajo:

- modos
- volumen

Objetivo de esta decisión:

- compactar solo la zona superior de frecuencia/sintonía
- no romper controles ya probados en operación real

## 6. Grid de bandas

El cliente Android actual muestra un grid compacto de bandas HF:

- `160`
- `80`
- `60`
- `40`
- `30`
- `20`
- `17`
- `15`
- `12`
- `10`

Fuente de verdad:

- `GET /api/bands`

Comportamiento validado:

- al pulsar una banda se usan `start` y `end` reales
- se calcula el centro de banda
- se ajusta el span visible a esa banda
- se aplica `mode` de API si existe
- si no existe, se usa el mismo fallback operativo que el cliente web

## 7. Decisiones explícitas de layout

No forma parte del layout actual:

- selector visible de paleta
- telemetría/debug visibles en cabecera principal
- botón textual `PWR` en la línea rápida
- botón grande `Connect`
- línea separada dedicada solo al modo

Razón:

- reducir altura
- evitar overlays que interfieran con futuras interacciones sobre el waterfall
- aproximar la ergonomía al cliente web sin copiar un layout de escritorio

Elementos movidos al menú superior:

- selector de paleta del waterfall
- telemetría/debug
- espacio para opciones futuras no críticas

## 8. Bugs de layout y comportamiento ya evitados

- frecuencia partida o en vertical
- banda oscura excesiva bajo el waterfall
- desaparición accidental de la línea rápida de controles
- selección de banda que cambia frecuencia pero deja el zoom global previo
- `MAX` con recorrido visual por múltiples niveles intermedios
- `MIN` con bordes negativos
- barra superior inexistente o sin iconos reales
- duplicidad de `power` entre barra y línea rápida

## 9. Criterio actual de compactación móvil

- mantener siempre visible la operativa principal:
  - waterfall
  - frecuencia/sintonía
  - línea rápida
  - modos
  - volumen
  - bandas
- mover al menú lo no esencial de uso inmediato
- evitar overlays o gestos complejos que degraden la usabilidad real

## 10. Nota de versión `v0.61b`

Cambios introducidos en cliente Android:

- mejora de UI en pantallas pequeñas:
  - reducción de problemas de altura por wrap de texto en botones
  - ajuste de etiquetas compactas `CWU` y `CWL` para evitar salto de línea
  - comportamiento más compacto y estable en dispositivos estrechos
- mejora en sintonía:
  - añadido `500 Hz` al selector de salto de frecuencia
  - mejora de usabilidad para ajuste fino en `SSB`
