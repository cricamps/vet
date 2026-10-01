# Postman — Semana 8 (Sumativa 3)

Importar `docs/postman_collection_s8.json` y completar las **variables de la colección**:

| Variable | Valor |
|---|---|
| `bffUrl` | `http://100.52.252.86:8080` (IP al 30-09-2026; cambia si se reinicia el lab; ver `despliegue-s8.md` paso 7) |
| `usuariosFuncUrl` | `https://func-usuarios-dsy2207-18514.azurewebsites.net/api` |
| `consUrl` | `https://func-eventos-usuarios-roles-dsy2207.azurewebsites.net/api` |
| `idRolPorDefecto` | `5` (CONSULTA en la base `usuariosroles`) |
| `idUsuarioNuevo`, `idRolNuevo`, `idUsuarioGraphQL` | se llenan solos al ejecutar |

Ejecutar en orden (o con *Run collection*). Las peticiones que dependen de un evento esperan 5 s en el *pre-request* porque Event Grid entrega de forma asíncrona.

| Carpeta | Qué demuestra |
|---|---|
| 0 | BFF arriba en EC2 |
| 1.1 – 1.3 | `UsuarioCreado` sin rol → la consumidora asigna **CONSULTA** + `BIENVENIDA` / `ROL_ASIGNADO` |
| 1.4 – 1.6 | `UsuarioModificado` con cambio de rol → `CAMBIO_ROL`; `RolCreado` (solo auditoría) |
| 1.7 | `RolModificado` → `ROL_RENOMBRADO` a los usuarios del rol |
| 1.8 – 1.10 | `RolEliminado` de un rol **en uso** → reasignación automática a CONSULTA + `REASIGNACION_ROL` |
| 1.11 | Regla de negocio: el rol por defecto no se elimina (409) |
| 1.12 | `UsuarioEliminado` → `BAJA` |
| 2.x | Último eslabón: auditoría, eventos procesados, notificaciones y estado final (vía BFF) |
| 3.x | Las mutations **GraphQL** generan los mismos eventos (`data.canal = "GraphQL"`); consulta directa a la consumidora |

En cada respuesta del CRUD, la pestaña **Headers** muestra `X-Evento-Tipo`, `X-Evento-Id` y `X-Evento-Publicado: true`: el `X-Evento-Id` es el mismo `ID_EVENTO` que luego aparece en `auditoria` y `procesados`.
