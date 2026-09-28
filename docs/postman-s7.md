# Pruebas Postman — Semana 7 (Formativa 5)

Importar `docs/postman_collection_s7.json` en Postman y completar las **variables de la colección**:

| Variable | Valor |
|---|---|
| `bffUrl` | `http://<ip-publica-ec2>:8080` |
| `prodUrl` | `https://func-eventos-productora-dsy2207.azurewebsites.net/api` |
| `prodKey` | function key de la productora (`az functionapp keys list ... --query functionKeys.default`) |
| `consUrl` | `https://func-eventos-consumidora-dsy2207.azurewebsites.net/api` |
| `idUsuarioAdmin` | `1` (Cristobal Camps, ADMINISTRADOR, base usuariosroles) |
| `idUsuarioConsulta` | id del usuario con rol CONSULTA creado en `despliegue-s7.md` paso 6 |
| `idTurno` | se llena solo al ejecutar 1.1 |

Datos de la base **VeterinariaCloud** usados en los bodies: mascota `1` (Firulais, dueña Ana Soto), veterinario `1` (Camila Rojas), ítem `22` (Meloxicam, stock 6 / mínimo 5). `fechaHora` debe ser futura: si se graba después del 05-10-2026, cambiarla.

## Carpeta 1 — Flujo completo vía BFF (orden sugerido para el video)

| # | Request | Resultado esperado |
|---|---|---|
| 1.1 | POST `/api/bff/eventos/turnos` | 202, `eventType = VetCare.TurnoAgendado`, `idRecurso = TUR-XXXXXXXX` |
| 1.2 | GET `/consultas/citas` | la cita aparece en `CITAS` con estado `AGENDADA` e `ID_TURNO` = TUR-… |
| 1.3 | GET `/consultas/comunicaciones` | email `PROGRAMADO` a ana@test.cl para 24 h antes de la cita |
| 1.4 | POST `/medicacion` (ítem 22 × 2) | 202 |
| 1.5 | GET `/consultas/inventario` | Meloxicam baja de 6 a 4 |
| 1.6 | GET `/consultas/alertas` | alerta `STOCK_BAJO` |
| 1.7 | POST `/laboratorio` | 202 |
| 1.8 | GET `/consultas/resultados` | hemograma en `RESULTADOS_LABORATORIO` (+ aviso en comunicaciones) |
| 1.9 | POST `/turnos/{idTurno}/cancelar` | 202 → cita `CANCELADA`, recordatorio `CANCELADO`, aviso de cancelación |
| 1.10 | GET `/consultas/procesados` | cada evento con `OK: …` (o `RECHAZADO: …`) |
| 1.11 | GET `/consultas/eventos` | todos los eventos en `EVENTOS_LOG` con su JSON completo |

Entre un POST y el GET siguiente pueden pasar 1-3 segundos: el procesamiento es **asíncrono** (buen punto para explicar en el video).

## Carpeta 2 — Casos de error

| # | Caso | Esperado |
|---|---|---|
| 2.1 | Usuario con rol CONSULTA | 403, el evento **no** se publica |
| 2.2 | Usuario 9999 | 404 propagado desde `function-usuarios` |
| 2.3 | Sin `X-Usuario-Id` | 400 |
| 2.4 | Sin `idVeterinario` | 400 con detalle del campo |
| 2.5 | Mascota 999 | 202 (el productor no conoce la base) y luego `RECHAZADO: La mascota 999 no existe` en `/consultas/procesados` |
| 2.6 | Productora sin `x-functions-key` | 401 (authLevel FUNCTION) |

## Carpeta 3 — Funciones directas

Permite mostrar cada pieza por separado: la productora publica sin pasar por el BFF (útil si la EC2 no está disponible) y las consultas de la consumidora.
