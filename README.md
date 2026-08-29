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
│   ├── arquitectura.md             # Diagrama + explicación de la arquitectura
│   └── guion-video.md              # Guion sugerido para el video de Teams
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
| BFF (Spring Boot, Docker) | AWS EC2 (`t3.micro`, instancia `i-03a5f366b29b6d0c5`), imagen publicada en Amazon ECR | `http://98.88.35.185:8080` |
| Función Usuarios | Azure Functions (Java) | `https://func-usuarios-dsy2207-18514.azurewebsites.net/api` |
| Función Roles | Azure Functions (Java) | `https://func-roles-dsy2207-14376.azurewebsites.net/api` |

Prueba rápida de que todo está vivo y conectado (BFF en AWS llamando a las funciones en Azure):

```bash
curl http://98.88.35.185:8080/api/bff/estado
# {"servicio":"bff-service","estado":"UP",...}

curl http://98.88.35.185:8080/api/bff/roles
# 500 esperado: la función de Azure responde, pero la base Oracle todavía
# tiene credenciales de ejemplo (ORACLE_DB_URL=CAMBIAR). Ver sección 5.
```

El `500` en `/roles` y `/usuarios` **no es un error del despliegue**: confirma que el BFF en AWS efectivamente llegó hasta la función en Azure (si no hubiera conectividad real, sería un timeout o un `502`, no un error de aplicación con JSON bien formado). Falta apuntar `ORACLE_DB_URL/USER/PASSWORD` (en ambas Function Apps de Azure) a una base Oracle real para que el CRUD funcione de punta a punta — recomendado usar el Oracle XE del `docker-compose.yml` expuesto públicamente, u Oracle Autonomous DB en OCI, y luego correr:

```bash
az functionapp config appsettings set -g rg-dsy2207-usuarios-roles -n func-usuarios-dsy2207-18514 --settings ORACLE_DB_URL="jdbc:oracle:thin:@//<host>:1521/<service>" ORACLE_DB_USER="..." ORACLE_DB_PASSWORD="..."
az functionapp config appsettings set -g rg-dsy2207-usuarios-roles -n func-roles-dsy2207-14376 --settings ORACLE_DB_URL="jdbc:oracle:thin:@//<host>:1521/<service>" ORACLE_DB_USER="..." ORACLE_DB_PASSWORD="..."
```

> **Nota:** la IP pública de la instancia EC2 (`98.88.35.185`) es dinámica y cambiará si la instancia se detiene y reinicia. Antes de grabar el video, verificar la IP actual con `aws ec2 describe-instances --instance-ids i-03a5f366b29b6d0c5 --query 'Reservations[0].Instances[0].PublicIpAddress'`.

## 8. Buenas prácticas aplicadas (guía Semana 3)

- **Enfoque en funciones**: cada función es pequeña, stateless y con una sola responsabilidad (usuarios o roles).
- **Manejo de errores**: todas las funciones capturan `SQLException` y devuelven códigos HTTP apropiados (404, 500) con un cuerpo JSON de error.
- **Seguridad**: las credenciales de Oracle nunca están hardcodeadas, se inyectan por variables de entorno.
- **Versionado y despliegue**: separación clara por commits/ramas en Git, Docker para builds reproducibles.
- **Pruebas**: recomendado probar cada endpoint con Postman antes de grabar el video (colección sugerida: crear rol → crear usuario con ese rol → listar → actualizar → eliminar).


