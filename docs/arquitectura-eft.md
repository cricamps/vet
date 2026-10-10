# Arquitectura final — Evaluación Final Transversal (Semana 9)

*Sistema de Gestión de Usuarios y Roles* desarrollado durante las tres experiencias de DSY2207: funciones serverless Java (REST + GraphQL), BFF Spring Boot que las orquesta por ambos canales y **Azure Event Grid** como tecnología de eventos. Las funciones generadoras y consumidoras de eventos están desarrolladas en **Java 17** y desplegadas en **Azure Functions**.

![Diagrama de arquitectura EFT](arquitectura-eft.png)

<details><summary>Fuente Mermaid del diagrama (docs/arquitectura-eft.mmd)</summary>

```mermaid
flowchart LR
    subgraph CLI["Cliente"]
        PM["Postman<br/>CRUD Usuarios y Roles<br/>+ consultas de eventos"]
    end

    subgraph AWS["AWS · EC2 + Docker"]
        BFF["BFF Spring Boot<br/>REST: /api/bff/usuarios · /api/bff/roles<br/>GraphQL: /api/bff/graphql/usuarios · /roles<br/>/api/bff/usuarios-roles/eventos/*<br/>reenvía headers X-Evento-*"]
    end

    subgraph AZ["Azure · Brazil South · rg-dsy2207-usuarios-roles"]
        direction LR
        subgraph PROD["Funciones GENERADORAS (Java)"]
            direction TB
            FU["func-usuarios-dsy2207-18514<br/>Agregar · Modificar · Eliminar · Listar · Obtener<br/>UsuariosGraphQL"]
            FR["func-roles-dsy2207-14376<br/>Agregar · Modificar · Eliminar · Listar · Obtener<br/>RolesGraphQL"]
        end
        TOPIC{{"Event Grid Topic<br/>usuarios-roles-events<br/>Event Grid Schema"}}
        subgraph SUBS["Suscripciones"]
            direction TB
            S1["sub-auditoria<br/>sin filtro"]
            S2["sub-usuarios<br/>UsuarioCreado · UsuarioModificado · UsuarioEliminado"]
            S3["sub-roles<br/>RolModificado · RolEliminado"]
        end
        subgraph CONS["func-eventos-usuarios-roles-dsy2207 · CONSUMIDORAS (Java)"]
            direction TB
            C1["AuditarEventoUsuariosRoles"]
            C2["ProcesarEventoUsuario<br/>rol por defecto · bienvenida<br/>cambio de rol · baja"]
            C3["ProcesarEventoRol<br/>quita el rol eliminado a sus usuarios<br/>avisa rol renombrado"]
            CQ["ConsultarEventosUsuariosRoles<br/>GET /api/consultas/{recurso}"]
        end
    end

    subgraph OCI["Oracle OCI · Autonomous DB usuariosroles"]
        DB1[("USUARIOS · ROLES")]
        DB2[("UR_AUDITORIA_EVENTOS<br/>UR_EVENTOS_PROCESADOS<br/>UR_NOTIFICACIONES")]
    end

    PM -->|HTTP/JSON| BFF
    BFF -->|REST + GraphQL| FU
    BFF -->|REST + GraphQL| FR
    BFF -->|GET consultas| CQ
    FU -->|1. CRUD JDBC| DB1
    FR -->|1. CRUD JDBC| DB1
    FU -->|2. publica UsuarioCreado/Modificado/Eliminado| TOPIC
    FR -->|2. publica RolCreado/Modificado/Eliminado| TOPIC
    TOPIC --> S1 --> C1
    TOPIC --> S2 --> C2
    TOPIC --> S3 --> C3
    C1 -->|3. INSERT| DB2
    C2 -->|3. UPDATE ID_ROL| DB1
    C2 -->|3. INSERT| DB2
    C3 -->|3. ID_ROL = NULL| DB1
    C3 -->|3. INSERT| DB2
    CQ -->|SELECT| DB2
    CQ -->|SELECT| DB1
```

</details>

## 1. Qué parte del requerimiento resuelve Event Grid

El CRUD de usuarios y roles sigue siendo **síncrono** (REST/GraphQL a través del BFF). Lo que se resuelve con eventos son las **reacciones** a esos cambios, que no deben bloquear la respuesta al cliente ni acoplar a las funciones CRUD con cada interesado:

| Necesidad del sistema | Evento | Quién reacciona | Efecto |
|---|---|---|---|
| Trazabilidad de todo cambio sobre usuarios y roles | Todos (6 tipos) | `AuditarEventoUsuariosRoles` | Fila en `UR_AUDITORIA_EVENTOS` con el JSON completo del evento |
| **Requerimiento 1:** al crear un usuario, asignarle un rol por defecto | `UsuarioCreado` / `UsuarioModificado` sin `idRol` | `ProcesarEventoUsuario` | Se asigna el rol por defecto `CONSULTA` (`USUARIOS.ID_ROL`) + notificación `ROL_ASIGNADO` |
| Informar al usuario al ser creado | `UsuarioCreado` | `ProcesarEventoUsuario` | Notificación `BIENVENIDA` con su rol final |
| Informar cambios de permisos | `UsuarioModificado` con `cambioDeRol = true` | `ProcesarEventoUsuario` | Notificación `CAMBIO_ROL` ("de OPERADOR a ADMINISTRADOR") |
| Registrar bajas | `UsuarioEliminado` | `ProcesarEventoUsuario` | Notificación `BAJA` (se conserva aunque el usuario ya no exista) |
| **Requerimiento 2:** al eliminar un rol, quitarlo a los usuarios que lo tenían | `RolEliminado` (con `usuariosAfectados`) | `ProcesarEventoRol` | Los usuarios afectados quedan **sin rol** (`ID_ROL = NULL`) + notificación `ROL_QUITADO` |
| Avisar cuando un rol cambia de nombre | `RolModificado` | `ProcesarEventoRol` | Notificación `ROL_RENOMBRADO` a cada usuario con ese rol |

La FK `FK_USUARIOS_ROL` impide borrar un rol que todavía tiene usuarios (`ORA-02292`). Por eso `function-roles` deja a esos usuarios con `ID_ROL = NULL` y borra el rol **en una sola transacción**, y luego publica `RolEliminado` con la lista de afectados. La consumidora `ProcesarEventoRol` reacciona al evento: garantiza (de forma idempotente) que ningún usuario siga apuntando al rol eliminado y notifica `ROL_QUITADO` a cada afectado. **No se reasigna ningún rol**: el usuario queda sin rol, tal como pide el requerimiento. El rol por defecto (`CONSULTA`) no puede eliminarse (HTTP 409), porque lo necesita el requerimiento 1.

## 2. Componentes

| # | Componente | Tecnología / nube | Finalidad |
|---|---|---|---|
| 1 | **BFF** `bff-service` | Spring Boot 3 + Docker, AWS EC2 | Punto de entrada único. Orquesta el CRUD por **REST** (`/api/bff/usuarios`, `/api/bff/roles`) y por **GraphQL** (`/api/bff/graphql/usuarios`, `/api/bff/graphql/roles`, que reenvían la operación a `UsuariosGraphQL` y `RolesGraphQL`). Valida que el rol exista antes de crear/modificar un usuario, reenvía los headers `X-Evento-*` de las funciones y expone las consultas del flujo de eventos. |
| 2 | **Funciones de Usuario** `func-usuarios-dsy2207-18514` | Azure Functions Java 17 (HTTP trigger) | CRUD sobre `USUARIOS` (REST + GraphQL). **Generadoras**: publican `UsuarioCreado`, `UsuarioModificado` (incluye `idRolAnterior` y `cambioDeRol`) y `UsuarioEliminado` (foto previa). |
| 3 | **Funciones de Rol** `func-roles-dsy2207-14376` | Azure Functions Java 17 (HTTP trigger) | CRUD sobre `ROLES` (REST + GraphQL). **Generadoras**: publican `RolCreado`, `RolModificado` (incluye `nombreAnterior`) y `RolEliminado` (incluye `usuariosAfectados`). |
| 4 | **Event Grid Topic** `usuarios-roles-events-NNNNN` | Azure Event Grid (custom topic, Event Grid Schema) | Bus de eventos: recibe cada evento una vez y lo distribuye (*fan-out*) a todas las suscripciones cuyo filtro coincida. Reintenta la entrega si el consumidor falla. |
| 5 | **Suscripciones** `sub-auditoria`, `sub-usuarios`, `sub-roles` | Event Grid event subscriptions (endpoint *Azure Function*) | Enrutan por `eventType`: auditoría sin filtro; usuarios y roles con *included event types*. 10 intentos de entrega, TTL 24 h. |
| 6 | **Funciones consumidoras** `func-eventos-usuarios-roles-dsy2207` | Azure Functions Java 17 (**Event Grid trigger**) | `AuditarEventoUsuariosRoles`, `ProcesarEventoUsuario`, `ProcesarEventoRol`: aplican los efectos de la tabla anterior de forma **idempotente** (`UR_EVENTOS_PROCESADOS`). |
| 7 | **Función de consultas** `ConsultarEventosUsuariosRoles` | Azure Functions Java 17 (HTTP trigger, misma app) | `GET /api/consultas/{auditoria|procesados|notificaciones|usuarios}`: evidencia el último eslabón del flujo. |
| 8 | **Base de datos** `usuariosroles` | Oracle Autonomous Database (OCI) | `USUARIOS`, `ROLES` (S1) + `UR_AUDITORIA_EVENTOS`, `UR_EVENTOS_PROCESADOS`, `UR_NOTIFICACIONES` (S8, `db/script_eventos_s8.sql`). |

## 3. Catálogo de eventos

Todos usan el **Event Grid Schema**: `id`, `subject`, `eventType`, `eventTime`, `data`, `dataVersion = "1.0"`.

| eventType | subject | data |
|---|---|---|
| `UsuariosRoles.UsuarioCreado` | `/usuarios/{id}` | `idUsuario, nombreUsuario, profesionUsuario, pais, idRol, canal` |
| `UsuariosRoles.UsuarioModificado` | `/usuarios/{id}` | lo anterior + `idRolAnterior, cambioDeRol, nombreAnterior` |
| `UsuariosRoles.UsuarioEliminado` | `/usuarios/{id}` | datos del usuario antes de eliminarlo |
| `UsuariosRoles.RolCreado` | `/roles/{id}` | `idRol, nombreRol, canal` |
| `UsuariosRoles.RolModificado` | `/roles/{id}` | `idRol, nombreRol, nombreAnterior, canal` |
| `UsuariosRoles.RolEliminado` | `/roles/{id}` | `idRol, nombreRol, usuariosAfectados[], canal` |

`canal` indica si el cambio llegó por `REST` o por `GraphQL`: ambos canales pasan por la misma capa de servicio (`UsuarioService` / `RolService`) y generan exactamente el mismo evento.

## 4. Flujo de punta a punta (ejemplo: crear un usuario sin rol)

1. Postman → `POST /api/bff/usuarios` `{ "nombreUsuario": "Ana Pérez", ... }` (sin `idRol`).
2. BFF → `POST func-usuarios/api/usuarios`.
3. `AgregarUsuario` inserta en `USUARIOS` (con `ID_ROL = NULL`) y **publica** `UsuarioCreado` en el topic. Responde `201` con headers `X-Evento-Tipo`, `X-Evento-Id`, `X-Evento-Publicado: true`, que el BFF reenvía a Postman.
4. Event Grid entrega el evento a `sub-auditoria` y a `sub-usuarios` (en paralelo).
5. `AuditarEventoUsuariosRoles` guarda el evento en `UR_AUDITORIA_EVENTOS`.
6. `ProcesarEventoUsuario` asigna el rol `CONSULTA`, crea las notificaciones `ROL_ASIGNADO` y `BIENVENIDA` y marca el evento `PROCESADO`.
7. Postman → `GET /api/bff/usuarios/{id}` (ahora tiene `idRol` de CONSULTA) y `GET /api/bff/usuarios/{id}/notificaciones`.

## 5. Decisiones de diseño

- **Publicar después del commit**: el evento se publica solo si el cambio en Oracle fue exitoso; así ningún consumidor reacciona a algo que no ocurrió.
- **Publicación *best effort***: si Event Grid no responde, el CRUD no se revierte; se informa con `X-Evento-Publicado: false` y queda en el log de la función.
- **Idempotencia**: Event Grid garantiza entrega *al menos una vez*. Cada consumidor registra `(ID_EVENTO, CONSUMIDOR)` en `UR_EVENTOS_PROCESADOS` dentro de la misma transacción que sus efectos; un duplicado se ignora.
- **Errores**: un error de Oracle relanza la excepción → Event Grid reintenta (hasta 10 veces). Un error de negocio (usuario ya eliminado, rol por defecto inexistente) se guarda como `RECHAZADO` y no se reintenta.
- **Eliminar rol**: la transacción de `function-roles` es corta (quitar el rol a sus usuarios y borrarlo, exigido por la FK); las reacciones (verificación idempotente, notificaciones, auditoría) ocurren de forma asíncrona en las consumidoras.
- **Secretos fuera del código**: `EVENTGRID_TOPIC_ENDPOINT`, `EVENTGRID_TOPIC_KEY` y `ORACLE_DB_*` son App Settings de cada Function App.
- **Multicloud**: BFF en AWS, cómputo serverless y bus de eventos en Azure, base de datos en Oracle OCI. La guía S8 compara Event Grid con Amazon EventBridge y Google Cloud Pub/Sub, que serían los equivalentes si se moviera el bus de eventos a otra nube.

## 6. Cumplimiento del requerimiento EFT

| Exigencia (instrucciones S9) | Cómo se cumple | Dónde se ve |
|---|---|---|
| Microservicios Spring Boot, respuestas JSON | `bff-service` (Spring Boot 3.3, Java 17) | `bff-service/`, Postman carpetas 1 y 3 |
| Funciones serverless Java con API REST (mín. 2) | `func-usuarios` y `func-roles`: Agregar, Listar, Obtener, Modificar, Eliminar | `function-usuarios/`, `function-roles/` |
| Funciones con GraphQL | `UsuariosGraphQL`, `RolesGraphQL` (queries + mutations) | `schema.graphqls` de cada función |
| BFF que orquesta las funciones (REST **y** GraphQL) | `UsuarioBffController`, `RolBffController` (REST) y `GraphQlBffController` (GraphQL) | Postman carpeta 1 (REST) y 3 (GraphQL) |
| Componente de eventos (mín. 1) | Azure Event Grid topic + 3 suscripciones + `func-eventos-usuarios-roles-dsy2207` | Portal Azure, Postman carpeta 2 |
| Usuario creado → rol por defecto | `UsuarioCreado` → `ProcesarEventoUsuario` asigna `CONSULTA` | Postman 1.1–1.3 y 3.2–3.3 |
| Rol eliminado → se quita a sus usuarios | `RolEliminado` → `ProcesarEventoRol`, usuarios con `idRol = null` | Postman 1.8–1.10 y 3.6–3.7 |
| Script BD Oracle | `db/script_oracle.sql` + `db/script_eventos_s8.sql` (o `db/script_completo_eft.sql`) | carpeta `db/` |
| Docker | `Dockerfile` en cada componente, `docker-compose.yml`; BFF corriendo en contenedor en AWS EC2 | `deploy` en EC2 |
| Git colaborativo | Repositorio github.com/cricamps/vet, ramas por entrega y Pull Requests revisados por el otro integrante | historial de GitHub |
