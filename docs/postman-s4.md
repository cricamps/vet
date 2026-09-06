# Pruebas con Postman — Semana 4 (Actividad Formativa 3)

Importa `docs/postman_collection_s4.json` en Postman (Import → File) y ajusta las variables de colección `baseUsuarios` y `baseRoles` con las URLs reales de tus dos Function Apps (y el `?code=<clave>` al final de cada URL si tus funciones no están en `AuthorizationLevel.ANONYMOUS`).

## Carpeta "REST - Usuarios"

Prueba directa contra `function-usuarios` (sin pasar por el BFF), para dejar explícita la comunicación REST función-a-cliente que pide la actividad:

- `GET /usuarios` — lista
- `GET /usuarios/{id}` — obtiene uno
- `POST /usuarios` — crea
- `PUT /usuarios/{id}` — modifica
- `DELETE /usuarios/{id}` — elimina

## Carpeta "GraphQL - Roles"

Todo contra un único endpoint, `POST /graphql/roles`, cambiando el `query`:

- **Query `roles`**: lista todos los roles.
- **Query `rol(id)`**: obtiene un rol puntual.
- **Mutation `agregarRol`**: crea un rol y devuelve el objeto creado (con su id generado).
- **Mutation `modificarRol`**: actualiza el nombre de un rol, devuelve `true`/`false`.
- **Mutation `eliminarRol`**: elimina un rol, devuelve `true`/`false`.

## Qué mostrar en el video (evidencia mínima)

1. Un `GET /usuarios` en Postman contra `function-usuarios` — REST funcionando.
2. Una **query** `roles` en Postman contra `function-roles` `/graphql/roles` — GraphQL funcionando.
3. Una **mutation** `agregarRol` seguida de la query `roles` mostrando el rol nuevo en la lista — evidencia de que la mutación realmente escribió en la base de datos (mismo Oracle que usa el resto del sistema).
4. Opcional pero recomendado: mostrar la respuesta de un `query` mal formado (por ejemplo, pedir un campo que no existe en el esquema) para mostrar que GraphQL devuelve un error de validación claro en `errors`, no un 500 genérico.
