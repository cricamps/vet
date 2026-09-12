# Pruebas con Postman — Semana 5 (Actividad Sumativa 2)

Importa `docs/postman_collection_s5.json` en Postman (Import → File) y ajusta las variables de colección `baseUsuarios` y `baseRoles` con las URLs reales de tus dos Function Apps (y el `?code=<clave>` al final de cada URL si tus funciones no están en `AuthorizationLevel.ANONYMOUS`).

## Carpeta "REST - Usuarios" y "REST - Roles"

Prueba directa contra cada función (sin pasar por el BFF), para dejar explícita la comunicación REST función-a-cliente.

## Carpeta "GraphQL - Usuarios" (nueva esta semana)

Todo contra un único endpoint, `POST /graphql/usuarios`, cambiando el `query`:

- **Query `usuarios`**: lista todos los usuarios.
- **Query `usuario(id)`**: obtiene un usuario puntual.
- **Mutation `agregarUsuario`**: crea un usuario y devuelve el objeto creado (con su id generado).
- **Mutation `modificarUsuario`**: actualiza un usuario, devuelve `true`/`false`.
- **Mutation `eliminarUsuario`**: elimina un usuario, devuelve `true`/`false`.

> Antes de correr "Mutation: modificar usuario" / "Mutation: eliminar usuario", ajusta el `id` de las variables al id real que devolvió tu "Mutation: agregar usuario" (en la colección se usa `6` como ejemplo).

## Carpeta "GraphQL - Roles"

Ya verificada en la Semana 4 (`RolesGraphQL`) — se incluye de nuevo solo para mostrar en el mismo video que ambas entidades tienen GraphQL funcionando.

## Qué mostrar en el video (evidencia mínima para los 6 criterios de la pauta)

1. Un `GET /usuarios` en Postman contra `function-usuarios` — REST funcionando (criterio 2).
2. Una **query** `usuarios` en Postman contra `function-usuarios` `/graphql/usuarios` — GraphQL funcionando (criterio 3).
3. Una **mutation** `agregarUsuario` seguida de la query `usuarios` mostrando el usuario nuevo en la lista — evidencia de que la mutación realmente escribió en la base de datos.
4. Repetir brevemente con Roles (`GET /roles` y query `roles` contra `/graphql/roles`) para dejar constancia de que ambas entidades cumplen REST + GraphQL (criterio 1 y 4: 4 funciones construidas y desplegadas, diseñadas según el requerimiento).
5. Opcional pero recomendado: mostrar la respuesta de un `query` mal formado (un campo que no existe en el esquema) para mostrar que GraphQL devuelve un error de validación claro en `errors`, no un 500 genérico.
