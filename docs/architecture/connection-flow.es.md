# Flujo de Conexión Android UberSDR

## Estado
Validado en cliente Android real contra backend de producción

## Última revisión
2026-04-14

---

## 1. Secuencia real de conexión

Orden confirmado:

1. `POST /connection`
2. `GET /api/description`
3. abrir WebSocket de audio `/ws`
4. esperar brevemente
5. abrir WebSocket de spectrum `/ws/user-spectrum?user_session_id=...&mode=binary8`

La sesión queda operativa cuando:

- `POST /connection` devuelve `allowed=true`
- audio WS entra en estado `OPEN`
- spectrum WS entra en estado `OPEN`
- llega `config`

---

## 2. Bootstrap HTTP

### `POST /connection`

Uso:

- validar acceso
- asociar `user_session_id`
- obtener estado `allowed`

Notas:

- Android genera `user_session_id` al iniciar la sesión.
- El mismo `user_session_id` se reutiliza en audio y spectrum.
- No se deben abrir WebSocket antes de este paso.

### `GET /api/description`

Uso:

- leer `default_frequency`
- leer `default_mode`
- leer límites y metadatos del servidor

Resultado validado en cliente Android:

- `default_frequency = 7062000`
- `default_mode = lsb`

---

## 3. Audio WS

Endpoint:

```text
/ws?user_session_id=<uuid>&frequency=<hz>&mode=<mode>&format=opus&version=2&bandwidthLow=<hz>&bandwidthHigh=<hz>
```

Estado esperado:

- `AUDIO WS CONNECTING`
- `AUDIO WS OPEN`

---

## 4. Espera breve antes de spectrum

Observación validada:

- abrir spectrum inmediatamente tras audio puede provocar `HTTP 429 Too Many Requests`

Contexto observado:

- backend detrás de Cloudflare Tunnel o protección anti flood equivalente

Mitigación validada:

- introducir una espera breve entre `AUDIO WS OPEN` y la apertura de spectrum

---

## 5. Spectrum WS

Endpoint:

```text
/ws/user-spectrum?user_session_id=<uuid>&mode=binary8
```

Estado esperado:

- `SPECTRUM WS CONNECTING`
- `SPECTRUM WS OPEN`

Comportamiento confirmado:

- el cliente puede enviar `{"type":"get_status"}`
- el backend puede enviar `config`, `pong` o `error`
- `config` puede emitirse al abrir la sesión

---

## 6. Notas de implementación Android

Validado en cliente real:

- bootstrap HTTP completo operativo
- audio WS operativo
- spectrum WS operativo
- `config` recibido y parseado
- renderer básico de waterfall operativo

Implicaciones:

- no asumir que spectrum responde como texto
- no abrir spectrum demasiado rápido tras audio
- no asumir frames completos continuos en waterfall
