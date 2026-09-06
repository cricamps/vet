# Guion del video (Teams) — Semana 4, Actividad Formativa 3

Duración pedida por el formato de respuesta: **mínimo 3, máximo 8 minutos**. Grupal (pareja), participación equitativa. Cubre los 5 criterios de la pauta: REST correcto, GraphQL correcto, dominio/coherencia explicando el video, despliegue correcto en la nube, trabajo colaborativo en Git.

## 0:00 – 0:20 — Introducción

- Nombres de ambos integrantes, sección, y nombre de la actividad: "Añadiendo comunicación Rest y GraphQL".
- Una frase resumen: "Sobre el sistema de Usuarios y Roles que ya teníamos en Azure, agregamos una capa GraphQL a la función de Roles, mientras que la función de Usuarios se mantiene como la evidencia de comunicación REST."

## 0:20 – 1:30 — Explicar qué se agregó (dominio del tema)

- Mostrar el diagrama/sección nueva de `docs/arquitectura.md` ("Semana 4 — Actividad Formativa 3").
- Explicar: "`function-usuarios` ya seguía el patrón REST desde la semana 3: un recurso, `/usuarios`, y un verbo HTTP por operación. La usamos tal cual para esta actividad."
- Explicar: "A `function-roles` le agregamos una quinta función, `RolesGraphQL`, con un único endpoint (`/api/graphql/roles`) que recibe una query o mutation en el body y responde en formato GraphQL estándar. Reutiliza el mismo `RolDao` y la misma base Oracle que las funciones REST de Roles — no duplicamos la lógica de negocio, solo agregamos una forma distinta de acceder al mismo recurso."
- Mencionar por qué GraphQL en Roles y no en Usuarios: decisión del equipo para repartir el trabajo, ambas entidades ya estaban desacopladas del BFF así que cualquiera podía tomar cualquiera.

## 1:30 – 2:00 — Evidencia de despliegue en la nube

- Mostrar el portal de Azure: la Function App de Roles (`func-roles-dsy2207-14376`) con la función **RolesGraphQL** listada junto a las REST existentes, estado "Running".
- Una frase: "No creamos infraestructura nueva, es la misma Function App de la Sumativa 1, solo la volvimos a desplegar con el código nuevo."

## 2:00 – 5:30 — Demostración en vivo con Postman

1. **REST (Usuarios)** — `GET /usuarios` en Postman contra `function-usuarios` directamente (sin pasar por el BFF) → mostrar la lista. Opcional: un `POST /usuarios` rápido.
2. **GraphQL (Roles)** — Query `roles` contra `/graphql/roles` → mostrar la lista de roles en el formato `{ "data": { "roles": [...] } }`.
3. **Mutation** `agregarRol` con un nombre nuevo (ej. `"SOPORTE"`) → mostrar la respuesta con el `idRol` generado.
4. Repetir la query `roles` → mostrar que el rol nuevo ya aparece (evidencia de que escribió en la misma base de datos).
5. **Mutation** `eliminarRol` sobre ese rol de prueba, para dejar la base como estaba, y una última query `roles` confirmando que ya no está.
6. Opcional (si alcanza el tiempo): una query mal formada para mostrar el bloque `errors` de GraphQL — evidencia de manejo de errores propio del protocolo.

## 5:30 – 7:00 — Trabajo colaborativo en Git

- Mostrar el repositorio en GitHub (`github.com/cricamps/vet`) y el historial de commits de esta semana, con aportes de **ambos** integrantes (criterio 5 de la pauta).
- Señalar el commit donde se agregó `function-roles/.../RolGraphQLFunction.java` y el esquema `schema.graphqls`.

## 7:00 – 8:00 — Cierre

- Reflexión breve: qué fue distinto entre implementar REST (ya lo sabían) y GraphQL (schema, resolvers, un solo endpoint para todo) — dificultades y cómo las resolvieron.
- Cierre y agradecimiento.

## Checklist antes de grabar

- [ ] `mvn clean package` y `mvn azure-functions:deploy` ejecutados en `function-roles` desde `C:\vet` (ver `docs/despliegue-s4.md`).
- [ ] Función `RolesGraphQL` visible en el portal de Azure.
- [ ] Colección de Postman (`docs/postman_collection_s4.json`) importada y probada antes de grabar — incluyendo el caso de error, si lo van a mostrar.
- [ ] `function-usuarios` sigue respondiendo con normalidad (no debería haber cambiado, pero conviene verificar).
- [ ] Commit y push hechos, ambos integrantes con al menos un commit.
- [ ] Grabación entre 3 y 8 minutos, subida a Teams, con el link pegado en `DSY2207_Exp2_S4_formato_de_respuesta.docx` junto al link del repositorio.
