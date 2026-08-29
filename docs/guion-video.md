# Guion del video (Teams) — 4 a 8 minutos

Cubre explícitamente los puntos 8, 9 y 10 de la pauta: explicar lo que se visualiza con dominio del tema, mostrar el sistema cloud funcionando en tiempo real (todas las funcionalidades requeridas, cumpliendo todos los requerimientos del caso) y quedar entre 4 y 8 minutos. También responde a la observación de la entrega anterior: **"no explicaron la arquitectura del sistema"**.

Repártanse los minutos entre ambos integrantes — la pauta exige participación equitativa (criterio 2 y 4).

URLs y recursos reales a usar durante la grabación (ya verificados funcionando):

- BFF (AWS EC2, Docker): `http://3.91.233.73:8080`
  - `GET /api/bff/estado`
  - `GET / POST / PUT / DELETE /api/bff/usuarios`
  - `GET / POST / PUT / DELETE /api/bff/roles`
- Function App Usuarios (Azure): `func-usuarios-dsy2207-18514`, resource group `rg-dsy2207-usuarios-roles`
- Function App Roles (Azure): `func-roles-dsy2207-14376`, mismo resource group
- Base de datos: Oracle Autonomous Database `usuariosroles` (OCI, sa-santiago-1)
- Estado actual de datos en la DB (útil para mostrar los tres roles/permisos en la demo):

  | Usuario | Rol |
  |---|---|
  | Cristobal Camps | ADMINISTRADOR |
  | Cynthia Torres Leal | ADMINISTRADOR |
  | Usuario Demo Operador | OPERADOR |

## Minuto 0:00 – 0:30 — Introducción

- Nombres de ambos integrantes (Cristobal Camps De la Maza y Cynthia Torres Leal), sección, y nombre de la actividad: "Implementando un sistema con arquitectura Serverless".
- Una frase resumen: "Desarrollamos un Sistema de Gestión de Usuarios y Roles con arquitectura multicloud: un BFF en Spring Boot sobre AWS que orquesta 8 funciones serverless en Java sobre Azure, con la base de datos en Oracle."

## Minuto 0:30 – 2:30 — Explicación de la arquitectura

Compartir pantalla con el diagrama (`docs/arquitectura.md`, diagrama `ARQ-USUARIOS-ROLES`) y explicar, señalando cada caja:

1. **BFF (Spring Boot, Docker sobre AWS EC2)**: "Es el único punto de entrada — el endpoint del CRUD. No tiene lógica de negocio propia, solo orquesta las llamadas hacia las funciones."
2. **Funciones de Usuario (Azure)**: "Agregar, Listar, Modificar y Eliminar Usuarios — 4 funciones independientes, cada una atiende una sola operación, es stateless y abre su propia conexión a la base de datos."
3. **Funciones de Rol (Azure)**: "Mismas 4 operaciones pero para roles."
4. **Oracle (OCI)**: "Base de datos compartida por las 8 funciones, con las tablas USUARIOS (nombre, profesión, país, rol asociado) y ROLES (nombre del rol), relacionadas 1 a N."
5. Explicar **por qué** se decidió esta arquitectura: multicloud (cada proveedor para lo que mejor resuelve), separación de responsabilidades, escalado independiente por operación, y que el BFF evita exponer las funciones directamente al consumidor.

## Minuto 2:30 – 6:30 — Demostración en vivo (nube real, no local)

0. Mostrar en pantalla el portal de Azure (Function Apps `func-usuarios-dsy2207-18514` y `func-roles-dsy2207-14376`, estado "Running") y la consola de AWS (instancia EC2 `bff-service-dsy2207`, estado "running") — evidencia visual de que está implementado en la nube, no solo en el código.
1. `GET http://3.91.233.73:8080/api/bff/estado` → mostrar `200 UP`: el BFF en AWS está vivo.
2. `GET http://3.91.233.73:8080/api/bff/roles` → mostrar los 3 roles existentes (ADMINISTRADOR, OPERADOR, CONSULTA).
3. `GET http://3.91.233.73:8080/api/bff/usuarios` → mostrar los 3 usuarios actuales y sus roles (tabla de arriba) — permite mostrar en un mismo llamado que hay usuarios con distintos roles.
4. **Agregar** un rol nuevo (`POST /api/bff/roles`, body `{"nombreRol":"SOPORTE"}`) y luego un usuario asignado a ese rol (`POST /api/bff/usuarios`, body con `idRol` del rol recién creado) — mostrar la respuesta y luego el `GET` confirmando que quedaron guardados.
5. **Modificar** ese usuario recién creado (`PUT /api/bff/usuarios/{id}`) cambiando algún dato, y mostrar el `GET` reflejando el cambio.
6. **Eliminar** ese usuario y ese rol de prueba (`DELETE`), dejando la base tal como estaba antes de la demo, y confirmar con un `GET` que ya no aparecen.
7. Llamar directamente una función de Azure sin pasar por el BFF (ej. `GET https://func-roles-dsy2207-14376.azurewebsites.net/api/roles?code=<key>`) para evidenciar que es un componente independiente y desacoplado.
8. Mostrar brevemente el código: `UsuarioFunction.java` / `RolFunction.java` (la función serverless) y `UsuarioBffController.java` / `RolBffController.java` (el orquestador), explicando en una frase qué hace cada uno.
9. Mostrar el repositorio en GitHub (`github.com/cricamps/vet`): historial de commits con aportes de **ambos** integrantes.

## Minuto 6:30 – 8:00 — Cierre

- Reflexión: dificultades encontradas (ej. configuración de la conexión Oracle sin wallet, orquestación desde el BFF hacia dos Function Apps) y cómo las resolvieron.
- Mencionar explícitamente que se dio respuesta a la observación de la actividad formativa anterior (zip de código, link de repositorio, video mostrando código y nube funcionando, y explicación de la arquitectura).
- Cierre y agradecimiento.

## Checklist antes de grabar — hacer esto primero, en este orden

- [ ] `git push -u origin main` ya ejecutado desde `C:\vet` (verificar en `github.com/cricamps/vet` que ya NO aparece vacío).
- [ ] Cynthia clonó el repo e hizo al menos un commit propio (participación equitativa evidenciada en Git).
- [ ] `GET http://3.91.233.73:8080/api/bff/estado` responde 200 antes de empezar a grabar.
- [ ] Diagrama de arquitectura abierto y listo para compartir pantalla.
- [ ] Postman (o similar) con las llamadas de la sección de demo ya armadas y probadas, para no perder tiempo en vivo.
- [ ] Ambos integrantes tienen sus minutos asignados para hablar.
- [ ] Grabación entre 4 y 8 minutos, subida a Teams, con el link copiado en el Formato de respuesta junto al link del repositorio.
