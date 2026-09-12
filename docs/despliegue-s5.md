# Despliegue — Semana 5 (Actividad Sumativa 2)

## Qué cambió respecto a la Semana 4

Solo `function-usuarios` tiene código nuevo (la función GraphQL que le faltaba). `function-roles` ya tenía su capa GraphQL desde la Semana 4 (`RolesGraphQL`) y no cambió. El `bff-service` tampoco cambió — no es necesario redesplegarlo para esta actividad.

Con esto, al terminar la Semana 5 el sistema completo cumple lo que pide la Sumativa 2: **ambas** funciones (Usuarios y Roles) exponen REST **y** GraphQL, en vez de tener una función solo-REST y otra solo-GraphQL como quedó en la Formativa 3.

Archivos nuevos/modificados:

- `function-usuarios/pom.xml` → se agregó la dependencia `com.graphql-java:graphql-java:21.5`. **Además se corrigió el mismo bug que se encontró en `function-roles` durante la S4**: `functionAppName`/`resourceGroup` apuntaban a un recurso Azure que no es el real (traían `function-usuarios-dsy2207` / `rg-dsy2207` / `appServicePlanName asp-dsy2207`, en vez de `func-usuarios-dsy2207-18514` / `rg-dsy2207-usuarios-roles`, que es la Function App realmente desplegada desde la Sumativa 1). Se corrigió antes de desplegar para no crear un recurso nuevo por error — revisa el paso 3 igual, por si acaso.
- `function-usuarios/src/main/resources/schema.graphqls` → esquema GraphQL de Usuario (nuevo).
- `function-usuarios/src/main/java/.../UsuarioGraphQLSchemaProvider.java` → arma el `GraphQLSchema` ejecutable (nuevo).
- `function-usuarios/src/main/java/.../UsuarioGraphQLFunction.java` → función Azure que expone `POST /api/graphql/usuarios` (nuevo).
- `UsuarioFunction.java`, `UsuarioDao.java`, `Usuario.java` → **sin cambios**.
- `function-roles/**` → **sin cambios** (ya cumplía REST + GraphQL desde la S4).
- `function-usuarios/Dockerfile` y `function-roles/Dockerfile` → se corrigió el `COPY --from=build` final, que apuntaba al nombre viejo de la carpeta de salida (`function-usuarios-dsy2207` / `function-roles-dsy2207`). Como el `functionAppName` de ambos `pom.xml` ya no es ese (ver corrección arriba, y la que ya tenía `function-roles` desde la S4), Maven ahora genera `target/azure-functions/func-usuarios-dsy2207-18514` y `target/azure-functions/func-roles-dsy2207-14376`; sin este ajuste, `docker compose build` habría fallado (o construido una imagen vacía) para ambas funciones.

## ⚠️ Importante: verificar la compilación

Este código se escribió calcando exactamente la estructura de `RolGraphQLFunction` / `RolGraphQLSchemaProvider` (que ya están verificados y funcionando en producción desde la S4), cambiando solo los nombres y campos propios de Usuario. Aun así, corre `mvn compile` en tu máquina (`C:\vet`) antes de desplegar para confirmar que compila sin errores.

Antes de desplegar, en `C:\vet\function-usuarios`:

```powershell
mvn clean compile
```

Si algo no compila, pégame el error y lo corrijo.

## Pasos para desplegar

1. Compilar y empaquetar:
   ```powershell
   cd C:\vet\function-usuarios
   mvn clean package
   ```
2. Desplegar a la Function App ya existente (**`func-usuarios-dsy2207-18514`**, resource group `rg-dsy2207-usuarios-roles` — mismos datos de la Sumativa 1, ahora también correctos en el `pom.xml`):
   ```powershell
   mvn azure-functions:deploy
   ```
   (usa la sesión de `az login` que ya tengas activa; si expiró, `az login` de nuevo antes).
3. **Verificar en el portal de Azure** que el despliegue haya ido a la Function App `func-usuarios-dsy2207-18514` (no a una nueva) y que muestre la nueva función **UsuariosGraphQL** junto a las 5 REST que ya existían. Si Maven llegara a crear una Function App / Storage Account con otro nombre, es la misma señal de alerta que en la S4: revisar el `pom.xml` antes de seguir.
4. Verificar en el portal que `function-roles` (`func-roles-dsy2207-14376`) sigue con sus 6 funciones (5 REST + `RolesGraphQL`) — no debería haber cambiado.
5. Probar con Postman (ver `docs/postman-s5.md` / `docs/postman_collection_s5.json`).
6. Confirmar que el BFF sigue funcionando igual (no cambió, pero es rápido de verificar): `GET http://<ip-ec2>:8080/api/bff/usuarios`.

## Variables de entorno

`UsuarioGraphQLFunction` reutiliza `UsuarioDao` → `DbConnection`, que ya lee `ORACLE_DB_URL`, `ORACLE_DB_USER`, `ORACLE_DB_PASSWORD` desde la configuración de la Function App (ya estaban configuradas desde la Sumativa 1). No hay variables nuevas que configurar.

## Commit y Git (criterio 6 de la pauta S5)

```powershell
cd C:\vet
git add function-usuarios/ docs/ README.md
git commit -m "S5S2: agrega capa GraphQL en function-usuarios (corrige pom.xml al recurso real) y documentacion"
git push
```

Recuerda que la pauta evalúa participación equitativa: si Cynthia también va a aportar un commit propio esta semana, lo ideal es que sea algo concreto (por ejemplo, revisar/probar la colección de Postman, o ajustar el guion del video).
