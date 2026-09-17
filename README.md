# Sistema de Gestión de Usuarios y Roles — DSY2207 Semana 3

Actividad Sumativa 1: **"Implementando un sistema con arquitectura Serverless"**
Desarrollo Cloud Native II (DSY2207) — Duoc UC

## 1. Descripción del caso

Requerimiento: *"Sistema de Gestión de Usuarios y Roles"*. Sistema backend compuesto por varios componentes, que permite operaciones CRUD sobre usuarios y roles, ejecutadas mediante funciones serverless.

Alcance implementado (arquitectura **multicloud**, según el diagrama de equipo `ARQ-USUARIOS-ROLES`):

- Sin componente frontend (no requerido por el caso).
- 1 microservicio **BFF** (Spring Boot, Docker, pensado para desplegarse en **AWS EC2**) que orquesta las llamadas.
- 8 funciones **serverless** (Java, Azure Functions): Agregar/Listar/Modificar/Eliminar para Usuarios, y las mismas 4 para Roles.
- Base de datos **Oracle** (OCI en el diseño objetivo; Oracle XE local para desarrollo) compartida por las funciones, con relación 1 a N entre usuario y rol (sin tabla intermedia).

La explicación completa de la arquitectura está en [`docs/arquitectura.md`](docs/arquitectura.md) — **léela antes de grabar el video**, porque en la entrega anterior quedó pendiente explicar la arquitectura del sistema.

## 2. Estructura del repositorio

```
proyecto-dsy2207-s3/
├── db/
│   └── script_oracle.sql          # DDL + datos de ejemplo (USUARIOS, ROLES)
├── function-usuarios/              # Función serverless CRUD de usuarios (Java)
├── function-roles/                 # Función serverless CRUD de roles (Java)
├── bff-service/                    # Microservicio BFF (Spring Boot)
├── docs/
│   └── arquitectura.md             # Diagrama + explicación de la arquitectura
├── docker-compose.yml              # Levanta todo el sistema junto
└── README.md
```

## 3. Cómo ejecutar todo con Docker

Requisitos: Docker y Docker Compose instalados.

```bash
# 1. Clonar el repositorio
git clone <URL_DEL_REPOSITORIO>
cd proyecto-dsy2207-s3

# 2. Levantar todo el sistema (Oracle + 2 funciones + BFF)
docker compose up --build
```

Esto expone:

| Componente             | URL local                        |
| ----------------------- | --------------------------------- |
| BFF                     | http://localhost:8080/api/bff     |
| Función Usuarios (directa) | http://localhost:7071/api/usuarios |
| Función Roles (directa)    | http://localhost:7072/api/roles    |
| Oracle DB                | localhost:1521 (servicio XEPDB1)  |

> **Nota Docker Lab:** una vez subido el proyecto al Docker Lab del ramo, este generará sus propias URLs/puertos para cada contenedor. Solo hay que actualizar las variables de entorno `FUNCION_USUARIOS_URL` y `FUNCION_ROLES_URL` del servicio `bff-service` en `docker-compose.yml` (o en la configuración del Lab) — no se debe recompilar ningún código, ya que el BFF las lee en tiempo de ejecución.

## 4. Endpoints principales

### BFF (orquestador — usar estos para la demo)

| Método | Ruta | Descripción |
| --- | --- | --- |
| GET | `/api/bff/usuarios` | Lista usuarios |
| GET | `/api/bff/usuarios/{id}` | Obtiene un usuario |
| POST | `/api/bff/usuarios` | Agrega usuario (`nombreUsuario`, `profesionUsuario`, `pais`, `idRol` opcional — el BFF valida que el rol exista antes de crear) |
| PUT | `/api/bff/usuarios/{id}` | Modifica usuario |
| DELETE | `/api/bff/usuarios/{id}` | Elimina usuario |
| GET | `/api/bff/roles` | Lista roles |
| GET | `/api/bff/roles/{id}` | Obtiene un rol |
| POST | `/api/bff/roles` | Agrega rol (`nombreRol`) |
| PUT | `/api/bff/roles/{id}` | Modifica rol |
| DELETE | `/api/bff/roles/{id}` | Elimina rol |
| GET | `/api/bff/estado` | Health check rápido del BFF |

### Funciones serverless (acceso directo, para explicar en el video que son independientes)

- Usuarios (Agregar/Listar/Modificar/Eliminar): `GET/POST http://localhost:7071/api/usuarios`, `GET/PUT/DELETE http://localhost:7071/api/usuarios/{id}`
- Roles (Agregar/Listar/Modificar/Eliminar): `GET/POST http://localhost:7072/api/roles`, `GET/PUT/DELETE http://localhost:7072/api/roles/{id}`

## 5. Base de datos

Ejecutar `db/script_oracle.sql` contra la instancia Oracle (el `docker-compose.yml` ya lo monta como script de arranque para desarrollo local; en el despliegue real apunta a Oracle OCI). Crea las tablas `ROLES` (`ID_ROL`, `NOMBRE_ROL`) y `USUARIOS` (`ID_USUARIO`, `NOMBRE_USUARIO`, `PROFESION_USUARIO`, `PAIS`, `ID_ROL` como FK), sus secuencias/triggers de autoincremento, y datos de ejemplo.

## 6. Flujo de trabajo colaborativo en Git

Para cumplir el criterio 4 de la pauta ("Usa herramientas de trabajo colaborativo y repositorios en git... con participación equitativa de los integrantes"):

1. **Repositorio compartido**: crear el repo en GitHub/GitLab/Azure DevOps y agregar a ambos integrantes de la pareja como colaboradores.
2. **Rama por integrante/funcionalidad**: por ejemplo `feature/funcion-usuarios`, `feature/funcion-roles`, `feature/bff`. Cada integrante desarrolla su parte en su rama.
3. **Commits frecuentes y descriptivos** de ambos integrantes (no todo el trabajo en un solo commit de una sola persona — esto es justamente lo que revisa el evaluador para verificar participación equitativa).
4. **Pull Requests** hacia `main`/`develop` revisados por el otro integrante antes de hacer merge.
5. Sugerencia de reparto:
   - Integrante A: función serverless de Usuarios + script Oracle.
   - Integrante B: función serverless de Roles + microservicio BFF.
   - Ambos: diagrama de arquitectura, Docker Compose y video de presentación.

## 7. Despliegue real en la nube (verificado)

Además del entorno local con Docker Compose, el sistema fue **desplegado de verdad** en Azure y AWS — esto es lo que hay que mostrar en el video para responder a la observación "no explicaron/mostraron el funcionamiento en la nube":

| Componente | Dónde | URL real |
| --- | --- | --- |
| BFF (Spring Boot, Docker) | AWS EC2 (`t3.micro`, instancia `i-03a5f366b29b6d0c5`), imagen publicada en Amazon ECR | `http://3.80.61.149:8080` |
| Función Usuarios | Azure Functions (Java) | `https://func-usuarios-dsy2207-18514.azurewebsites.net/api` |
| Función Roles | Azure Functions (Java) | `https://func-roles-dsy2207-14376.azurewebsites.net/api` |

Prueba rápida de que todo está vivo y conectado de punta a punta (BFF en AWS → funciones en Azure → Oracle en OCI):

```bash
curl http://3.80.61.149:8080/api/bff/estado
# {"servicio":"bff-service","descripcion":"BFF orquestador de Usuarios y Roles - DSY2207 S3","estado":"UP"}

curl http://3.80.61.149:8080/api/bff/roles
# [{"idRol":1,"nombreRol":"ADMINISTRADOR"},{"idRol":4,"nombreRol":"OPERADOR"},{"idRol":5,"nombreRol":"CONSULTA"}]

curl http://3.80.61.149:8080/api/bff/usuarios
# [{"idUsuario":1,"nombreUsuario":"Cristobal Camps",...},{"idUsuario":3,"nombreUsuario":"Cynthia Torres Leal",...},{"idUsuario":4,"nombreUsuario":"Usuario Demo Operador",...}]

# Manejo de errores del BFF (GlobalExceptionHandler) verificado en vivo:
curl -i http://3.80.61.149:8080/api/bff/usuarios/9999
# HTTP/1.1 404
# {"timestamp":"...","status":404,"error":"Not Found","mensaje":"{\"error\":\"Usuario 9999 no encontrado\"}"}
```

> **Nota:** la IP pública de la instancia EC2 (`3.80.61.149`) es dinámica y cambiará si la instancia se detiene y reinicia. Antes de grabar el video, verificar la IP actual con `aws ec2 describe-instances --instance-ids i-03a5f366b29b6d0c5 --query 'Reservations[0].Instances[0].PublicIpAddress'`.

## 8. Buenas prácticas aplicadas (guía Semana 3)

- **Enfoque en funciones**: cada función es pequeña, stateless y con una sola responsabilidad (usuarios o roles).
- **Manejo de errores**: todas las funciones capturan `SQLException` y devuelven códigos HTTP apropiados (404, 500) con un cuerpo JSON de error; el BFF además centraliza el manejo de errores con un `@RestControllerAdvice` (`GlobalExceptionHandler`) que reenvía el código de estado real de la función serverless downstream (`WebClientResponseException`) y normaliza sus propias validaciones de negocio (`ResponseStatusException`, ej. rol inexistente) al mismo formato JSON — verificado en vivo en la sección 7.
- **Seguridad**: las credenciales de Oracle nunca están hardcodeadas, se inyectan por variables de entorno.
- **Versionado y despliegue**: separación clara por commits/ramas en Git, Docker para builds reproducibles.
- **Pruebas**: recomendado probar cada endpoint con Postman antes de grabar el video (colección sugerida: crear rol → crear usuario con ese rol → listar → actualizar → eliminar).

## 9. Checklist de entrega (según Formato de respuesta y observaciones de la formativa anterior)

- [ ] Archivo comprimido (.zip/.rar) con todo el código fuente (microservicio BFF en Java/Spring, script Oracle, funciones en Java) — ✅ incluido en este proyecto.
- [ ] Diagrama del diseño del sistema — ✅ `docs/arquitectura.md`.
- [ ] Link al repositorio Git — completar en `Formato_de_respuesta.docx`.
- [ ] Video grabado en Teams (4 a 8 minutos) — **debe explicar la arquitectura del sistema** (pendiente de la entrega anterior) y mostrar el funcionamiento en tiempo real.
- [ ] Link del video — completar en `Formato_de_respuesta.docx`.
- [ ] Participación equitativa de ambos integrantes evidenciada en Git y en el video.

## 10. Semana 4 — Actividad Formativa 3: "Añadiendo comunicación Rest y GraphQL"

Sobre esta misma base se agregó una capa **GraphQL** a `function-roles` (nueva función `RolesGraphQL`, endpoint `POST /api/graphql/roles`), mientras que `function-usuarios` se mantiene como la evidencia de comunicación **REST** (ya cumplía el patrón desde la Semana 3, sin cambios). Ver:

- `docs/arquitectura.md` → sección "Semana 4 — Actividad Formativa 3" (diseño y ejemplos de queries/mutations).
- `docs/despliegue-s4.md` → cómo compilar y desplegar el `function-roles` actualizado.
- `docs/postman-s4.md` + `docs/postman_collection_s4.json` → pruebas REST y GraphQL listas para importar en Postman.

> **Nota:** el código de esta sección se escribió y documentó en un entorno sin acceso a Maven Central, por lo que **no se pudo compilar ni probar automáticamente**; el primer `mvn clean compile` debe hacerse en el equipo local antes de desplegar (detalle en `docs/despliegue-s4.md`).


## 11. Semana 5 — Actividad Sumativa 2: "Implementando comunicación Rest y GraphQL en el desarrollo"

Completa lo que quedó repartido en la Semana 4: ahora **ambas** funciones (`function-usuarios` y `function-roles`) exponen REST y GraphQL. Se agregó `UsuariosGraphQL` (`POST /api/graphql/usuarios`) a `function-usuarios`, con el mismo patrón que `RolesGraphQL`. Ver:

- `docs/arquitectura.md` → sección "Semana 5 — Actividad Sumativa 2" (diseño y ejemplos de queries/mutations).
- `docs/despliegue-s5.md` → cómo compilar y desplegar el `function-usuarios` actualizado (incluye la corrección de un bug en su `pom.xml` que apuntaba al recurso Azure equivocado — mismo tipo de bug que se corrigió en `function-roles` durante la S4).
- `docs/postman-s5.md` + `docs/postman_collection_s5.json` → pruebas REST y GraphQL de ambas entidades, listas para importar en Postman.

> **Nota:** igual que en la S4, este código se escribió y documentó en un entorno sin Maven ni JDK 17, por lo que **no se pudo compilar ni probar automáticamente**; el primer `mvn clean compile` debe hacerse en el equipo local antes de desplegar (detalle en `docs/despliegue-s5.md`).
