# Despliegue — Semana 4 (Actividad Formativa 3)

## Qué cambió respecto a la Sumativa 1

Solo `function-roles` tiene código nuevo (la función GraphQL). `function-usuarios` y el `bff-service` **no cambiaron** — no es necesario redesplegarlos para esta actividad.

Archivos nuevos/modificados:

- `function-roles/pom.xml` → se agregó la dependencia `com.graphql-java:graphql-java:21.5`.
- `function-roles/src/main/resources/schema.graphqls` → esquema GraphQL de Rol (nuevo).
- `function-roles/src/main/java/.../RolGraphQLSchemaProvider.java` → arma el `GraphQLSchema` ejecutable (nuevo).
- `function-roles/src/main/java/.../RolGraphQLFunction.java` → función Azure que expone `POST /api/graphql/roles` (nuevo).
- `RolFunction.java`, `RolDao.java`, `Rol.java` → **sin cambios**.

## ⚠️ Importante: verificar la compilación

Este código se escribió con cuidado revisando la API de `graphql-java` 21.x, pero conviene correr `mvn compile` en tu máquina (`C:\vet`) antes de desplegar, para confirmar que compila sin errores.

Antes de desplegar, en `C:\vet\function-roles`:

```powershell
mvn clean compile
```

Si algo no compila, el error de Maven va a decir exactamente qué línea — probablemente algo menor (nombre de un método de la API de graphql-java). Pégame el error si aparece y lo corrijo.

## Pasos para desplegar

1. Traer los cambios a tu copia local (`C:\vet`). La forma más simple: aplicar el parche/zip que te dejo, o decirme y te preparo un `git diff` para aplicar con `git apply`.
2. Compilar y empaquetar:
   ```powershell
   cd C:\vet\function-roles
   mvn clean package
   ```
3. Desplegar a la Function App ya existente (`func-roles-dsy2207-14376`, resource group `rg-dsy2207-usuarios-roles` — mismos datos de la Sumativa 1):
   ```powershell
   mvn azure-functions:deploy
   ```
   (usa la sesión de `az login` que ya tengas activa; si expiró, `az login` de nuevo antes).
4. Verificar en el portal de Azure que la Function App muestre la nueva función **RolesGraphQL** junto a las 5 REST que ya existían.
5. Probar con Postman (ver `docs/postman-s4.md` / `docs/postman_collection_s4.json`).
6. Confirmar que `function-usuarios` sigue respondiendo igual que antes (no debería haber cambiado nada, pero es rápido de verificar): `GET https://func-usuarios-dsy2207-18514.azurewebsites.net/api/usuarios`.

## Variables de entorno

`RolGraphQLFunction` reutiliza `RolDao` → `DbConnection`, que ya lee `ORACLE_DB_URL`, `ORACLE_DB_USER`, `ORACLE_DB_PASSWORD` desde la configuración de la Function App. No hay variables nuevas que configurar.

## Commit y Git (criterio 5 de la pauta)

```powershell
cd C:\vet
git add function-roles/
git add docs/
git commit -m "S4F3: agrega capa GraphQL en function-roles (mantiene REST en function-usuarios)"
git push
```

Si Cynthia también va a aportar un commit (participación equitativa, mismo criterio que en la Sumativa 1), lo ideal es que ella agregue algo concreto — por ejemplo, ella podría escribir las pruebas o revisar/ajustar el guion del video.
