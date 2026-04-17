# Android Client – Agent Rules

## Scope

- Este directorio (`android-client`) es el ámbito principal de trabajo.
- No modificar archivos fuera de esta carpeta salvo orden explícita.
- Ignorar otros módulos del repositorio.

## Referencias

- Usar como referencia: `../docs/architecture`
- No asumir comportamiento fuera de lo documentado.

---

## Estilo de cambios

- Hacer siempre el cambio mínimo necesario.
- No refactorizar fuera del alcance pedido.
- No renombrar funciones, variables, clases o archivos salvo petición expresa.
- No reformatear código no modificado.
- No introducir mejoras no solicitadas.

---

## Método de trabajo

- Trabajar en un solo fichero por iteración, salvo necesidad clara.
- Antes de cambiar nada, explicar en 1–3 frases qué se cree que ocurre.
- Proponer siempre cambios pequeños y trazables.
- Si hay varias opciones, elegir la más conservadora.
- Si faltan datos, pedirlos antes de actuar.

---

## Restricciones

- No modificar:
  - persistencia
  - reconexión
  - estado global
  - arquitectura

- No añadir:
  - persistencia
  - DSP local
  - nuevas capas de arquitectura

---

## Debugging

- Una sola hipótesis por iteración.
- Una sola acción por iteración.
- Primero localizar el problema, luego corregir.
- Priorizar trazabilidad sobre rapidez.

---

## Salida esperada

- Explicación breve
- Cambio mínimo (diff o código concreto)
- Riesgos si los hay

---

## Reglas de seguridad

- No usar rutas fuera de `/mnt/d/dev/...`
- No usar `/root`
- No asumir ejecución dentro de WSL para Android Studio

---

## Definición de terminado

- El cambio compila
- No afecta a otras partes del sistema
- Es fácil de revisar
- Respeta todas las reglas anteriores
