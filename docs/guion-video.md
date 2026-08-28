# Guion sugerido para el video (Teams) — 4 a 8 minutos

La observación de la entrega anterior fue explícita: **"no explicaron la arquitectura del sistema"**. Este guion está diseñado para que eso no vuelva a pasar. Repártanse los minutos entre ambos integrantes (participación equitativa la evalúa el criterio 2 y 4 de la pauta).

## Minuto 0:00 – 0:30 — Introducción
- Nombres de ambos integrantes, sección, y nombre de la actividad: "Implementando un sistema con arquitectura Serverless".
- Una frase resumen: "Desarrollamos un Sistema de Gestión de Usuarios y Roles con arquitectura multicloud: un BFF en Spring Boot sobre AWS que orquesta 8 funciones serverless en Java sobre Azure, con la base de datos en Oracle."

## Minuto 0:30 – 2:30 — Explicación de la arquitectura (la parte que faltó)
Mostrar el diagrama (`docs/arquitectura.md` / el diagrama del equipo `ARQ-USUARIOS-ROLES`) en pantalla y explicar, señalando cada caja:

1. **BFF (Spring Boot, Docker sobre AWS EC2)**: "Es el único punto de entrada — el endpoint del CRUD. No tiene lógica de negocio propia, solo orquesta las llamadas hacia las funciones."
2. **Funciones de Usuario (Azure)**: "Agregar, Listar, Modificar y Eliminar Usuarios — 4 funciones independientes, cada una atiende una sola operación, es stateless y abre su propia conexión a la base de datos."
3. **Funciones de Rol (Azure)**: "Mismas 4 operaciones pero para roles."
4. **Oracle (OCI)**: "Base de datos compartida por las 8 funciones, con las tablas USUARIOS (nombre, profesión, país, rol asociado) y ROLES (nombre del rol), relacionadas 1 a N."
5. Explicar **por qué** se decidió esta arquitectura: multicloud (cada proveedor para lo que mejor resuelve), separación de responsabilidades, escalado independiente por operación, y que el BFF evita exponer las funciones directamente al consumidor.

## Minuto 2:30 – 6:30 — Demostración en vivo

**Importante — esta vez SÍ se muestra la nube real, no solo local** (fue la observación principal de la formativa anterior). Usar Postman contra la IP/URLs reales:

0. Abrir el portal de Azure (Function Apps `func-usuarios-dsy2207-18514` y `func-roles-dsy2207-14376`) y la consola de AWS (instancia EC2 `bff-service-dsy2207`) para mostrar que los recursos existen y están corriendo — evidencia visual de "implementado en la nube", no solo código.
1. Llamar `GET http://<IP_EC2>:8080/api/bff/estado` y mostrar el `200 UP` — el BFF en AWS está vivo.
2. Agregar un rol (`POST http://<IP_EC2>:8080/api/bff/roles`).
3. Agregar un usuario asignándole ese rol (`POST .../api/bff/usuarios` con `idRol`) — mostrar que si se manda un `idRol` inexistente, el BFF responde 400 sin crear el usuario (evidencia de orquestación real, cruzando de la función de Roles a la de Usuarios).
4. Listar usuarios y roles (`GET .../api/bff/usuarios`, `GET .../api/bff/roles`).
5. Modificar un usuario (`PUT .../api/bff/usuarios/{id}`).
6. Eliminar un usuario o rol (`DELETE`).
7. Llamar directamente una función de Azure (ej. `GET https://func-roles-dsy2207-14376.azurewebsites.net/api/roles`) para evidenciar que es un componente independiente y desacoplado del BFF.
8. Mostrar brevemente el código: la clase `UsuarioFunction.java` (o `RolFunction.java`) y el controller del BFF, explicando en una frase qué hace cada uno.
9. Mostrar el repositorio Git: commits de ambos integrantes, ramas usadas.

> Si al momento de grabar la base Oracle real todavía no está conectada (ver README sección 7), los pasos 2-6 devolverán `500` en vez del resultado esperado — igual vale mostrarlo y explicar en el video que la conectividad multicloud (AWS→Azure) está probada y que falta apuntar la base de datos, o bien completar esa conexión antes de grabar.

## Minuto 6:30 – 8:00 — Cierre
- Reflexión: dificultades encontradas (ej. configuración de la conexión Oracle, orquestación desde el BFF) y cómo las resolvieron.
- Mencionar explícitamente que se dio respuesta a la observación de la actividad formativa anterior (zip de código, link de repositorio, video de código funcionando, y explicación de la arquitectura).
- Cierre y agradecimiento.

## Checklist antes de grabar

- [ ] `docker compose up --build` corre sin errores.
- [ ] Los 3 servicios responden (`/api/bff/estado`, función usuarios, función roles).
- [ ] Colección de Postman probada de principio a fin.
- [ ] Diagrama de arquitectura visible para compartir pantalla.
- [ ] Ambos integrantes tienen minutos asignados para hablar.
- [ ] Grabación entre 4 y 8 minutos, subida a Teams, con el link copiado al Formato de respuesta.
