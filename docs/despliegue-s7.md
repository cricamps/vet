# Despliegue — Semana 7 (Formativa 5): capa de eventos con Azure Event Grid

Orden recomendado por la guía: **1) Event Grid Topic → 2) función generadora → 3) función consumidora → 4) suscripción**. Antes, la base de datos; al final, el BFF.

Todos los comandos son para **PowerShell** en Windows, desde `C:\vet`, con la cuenta `cri.camps@duocuc.cl` (suscripción *Azure for Students*). Cada paso indica también su equivalente en el portal (igual que en el material descargable).

```powershell
az login
az account show --query name -o tsv      # debe decir "Azure for Students"

$RG  = "rg-dsy2207-usuarios-roles"
$LOC = "brazilsouth"
$PROD = "func-eventos-productora-dsy2207"    # = <functionAppName> del pom de la productora
$CONS = "func-eventos-consumidora-dsy2207"   # = <functionAppName> del pom de la consumidora
```

> Si Maven indica que el nombre de una Function App ya existe en Azure, cambiar el sufijo en `<functionAppName>` del `pom.xml` y en la variable correspondiente.

---

## Paso 0 — Tablas en Oracle

Ejecutar `db/script_eventos_s7.sql` en la base **VeterinariaCloud** (`vetcloud`, esquema ADMIN) desde *OCI → Base de datos de IA autónoma → VeterinariaCloud → Acciones de base de datos → SQL*, con **Ejecutar script (F5)**. Crea `EVENTOS_LOG`, `EVENTOS_PROCESADOS` y `NOTIFICACIONES`, y agrega un veterinario (EMPLEADOS) y dos medicamentos (INVENTARIO) para la demo. **No modifica** las tablas existentes del sistema veterinario.

> Si Database Actions responde *403 IP Address Rejected*, agregar la IP propia en *Red → Lista de control de acceso → Editar → Agregar mi dirección IP*.

Verificar: `SELECT ID_EMPLEADO, NOMBRE FROM EMPLEADOS;` (al menos un veterinario) y `SELECT ID_ITEM, NOMBRE, STOCK_ACTUAL FROM INVENTARIO;`. Anotar el `ID_MASCOTA`, `ID_EMPLEADO` e `ID_ITEM` que se usarán en las pruebas (en la demo: mascota 1 *Firulais*, veterinario 1 *Camila Rojas*, ítems 1, 21 y 22).

## Paso 1 — Event Grid Topic

Portal: *Subscriptions → Azure for Students → Resource providers → `Microsoft.EventGrid` → Register* y luego *Event Grid Topics → Create* (Event Grid Schema, Public access).

```powershell
az provider register --namespace Microsoft.EventGrid
az provider show --namespace Microsoft.EventGrid --query registrationState -o tsv   # esperar "Registered"

$TOPIC = "vetcare-events-" + (Get-Random -Minimum 10000 -Maximum 99999)
az eventgrid topic create -g $RG -n $TOPIC -l $LOC --input-schema eventgridschema

$EG_ENDPOINT = az eventgrid topic show -g $RG -n $TOPIC --query endpoint -o tsv
$EG_KEY      = az eventgrid topic key list -g $RG -n $TOPIC --query key1 -o tsv
$TOPIC_ID    = az eventgrid topic show -g $RG -n $TOPIC --query id -o tsv
$TOPIC; $EG_ENDPOINT      # anotar el nombre del topic (lo usaremos para el video)
```

## Paso 2 — Función generadora de eventos (`function-eventos-productora`)

```powershell
cd C:\vet\function-eventos-productora
mvn clean package          # compila y corre los 7 tests unitarios de ConstructorEventosTest
mvn azure-functions:deploy # crea la Function App si no existe y publica 4 funciones
```

Configurar el endpoint y la key del topic como **App Settings** (no van en el código ni en GitHub):

```powershell
az functionapp config appsettings set -g $RG -n $PROD --settings "EVENTGRID_TOPIC_ENDPOINT=$EG_ENDPOINT" "EVENTGRID_TOPIC_KEY=$EG_KEY"

# Function key (las funciones generadoras usan authLevel = FUNCTION)
$PROD_KEY = az functionapp keys list -g $RG -n $PROD --query functionKeys.default -o tsv
$PROD_URL = "https://$PROD.azurewebsites.net/api"
```

Portal equivalente: *Function App → Settings → Environment variables → + Add* (`EVENTGRID_TOPIC_ENDPOINT`, `EVENTGRID_TOPIC_KEY`) y *App keys → default*.

Prueba directa (sin BFF):

```powershell
$body = @{ idMascota=1; idVeterinario=1; fechaHora="2026-10-05T10:30"; motivo="Vacuna antirrabica" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$PROD_URL/eventos/turnos" -Headers @{ "x-functions-key"=$PROD_KEY; "X-Usuario-Id"="1" } -ContentType "application/json" -Body $body | ConvertTo-Json -Depth 5
# -> mensaje "Evento creado correctamente", idEvento, eventType VetCare.TurnoAgendado, idRecurso TUR-XXXXXXXX
```

(En este punto el topic recibe el evento pero todavía no hay suscriptores: en *Topic → Overview* se ve "Published Events" = 1 y "Unmatched Events" = 1.)

## Paso 3 — Función consumidora de eventos (`function-eventos-consumidora`)

```powershell
cd C:\vet\function-eventos-consumidora
mvn clean package
mvn azure-functions:deploy # publica RegistrarEventoLog, ProcesarEventoClinico y 4 funciones Consultar*
```

Configurar la conexión a la base **VeterinariaCloud** (portal: *Function App `func-eventos-consumidora-dsy2207` → Settings → Environment variables*), con los mismos datos de conexión que usa el proyecto veterinario (`veterinaria-cloud-func-cc`):

| App Setting | Valor |
|---|---|
| `ORACLE_DB_URL` | URL JDBC de `vetcloud` (TLS, p.ej. `jdbc:oracle:thin:@(description=...(host=adb.sa-santiago-1.oraclecloud.com)...(service_name=..._vetcloud_low.adb.oraclecloud.com)...)`) |
| `ORACLE_DB_USER` | `ADMIN` (o el usuario de la app) |
| `ORACLE_DB_PASSWORD` | password de ese usuario |

> La función consumidora sale a internet con las mismas IPs que `func-usuarios` (mismo plan en Brazil South), por lo que pasa la lista de acceso de la base si esa ya lo hace.

Prueba: `Invoke-RestMethod "https://$CONS.azurewebsites.net/api/consultas/inventario"` → los ítems de `INVENTARIO` de VeterinariaCloud.

## Paso 4 — Suscripciones a los eventos

Portal (como en el material): *Event Grid Topic → Event Subscriptions → + Event Subscription* → Event Schema **Event Grid Schema** → Endpoint Type **Azure Function** → *Configure an endpoint* → Function App `func-eventos-consumidora-dsy2207` → Function `RegistrarEventoLog` (y una segunda suscripción para `ProcesarEventoClinico`, con *Filter to Event Types*).

Con Azure CLI:

```powershell
$CONS_ID = az functionapp show -g $RG -n $CONS --query id -o tsv

# 1) Auditoria: recibe TODOS los eventos
az eventgrid event-subscription create --name sub-auditoria `
  --source-resource-id $TOPIC_ID `
  --endpoint-type azurefunction --endpoint "$CONS_ID/functions/RegistrarEventoLog" `
  --max-delivery-attempts 10 --event-ttl 1440

# 2) Clinica: solo los 4 tipos de negocio (filtro por eventType)
az eventgrid event-subscription create --name sub-clinica `
  --source-resource-id $TOPIC_ID `
  --endpoint-type azurefunction --endpoint "$CONS_ID/functions/ProcesarEventoClinico" `
  --included-event-types VetCare.TurnoAgendado VetCare.TurnoCancelado VetCare.MedicacionDispensada VetCare.ResultadoLabListo `
  --max-delivery-attempts 10 --event-ttl 1440

az eventgrid event-subscription list --source-resource-id $TOPIC_ID -o table
```

Repetir la prueba del paso 2 y verificar (esperar unos segundos):

```powershell
Invoke-RestMethod "https://$CONS.azurewebsites.net/api/consultas/citas"          | ConvertTo-Json -Depth 5
Invoke-RestMethod "https://$CONS.azurewebsites.net/api/consultas/comunicaciones" | ConvertTo-Json -Depth 5
Invoke-RestMethod "https://$CONS.azurewebsites.net/api/consultas/procesados"     | ConvertTo-Json -Depth 5
Invoke-RestMethod "https://$CONS.azurewebsites.net/api/consultas/eventos"        | ConvertTo-Json -Depth 5
```

Evidencias en el portal (las mismas que muestra el material): *Topic → Overview* (Published / Delivered events), *Event Subscription → Overview* (Delivered / Matched), *Function App → función → Monitor / Invocations* con los logs "Evento recibido…".

## Paso 5 — BFF (AWS EC2) con el nuevo `EventoBffController`

1. Compilar: `cd C:\vet\bff-service; mvn clean package`.
2. Reconstruir y subir la imagen a ECR y reiniciar el contenedor en la EC2 **con el mismo procedimiento de la S3**, agregando estas variables de entorno al `docker run`:

```bash
docker run -d --name bff-service -p 8080:8080 \
  -e FUNCION_USUARIOS_URL=https://func-usuarios-dsy2207-18514.azurewebsites.net/api \
  -e FUNCION_ROLES_URL=https://func-roles-dsy2207-14376.azurewebsites.net/api \
  -e FUNCION_EVENTOS_PRODUCTORA_URL=https://func-eventos-productora-dsy2207.azurewebsites.net/api \
  -e FUNCION_EVENTOS_PRODUCTORA_KEY=<valor de $PROD_KEY> \
  -e FUNCION_EVENTOS_CONSUMIDORA_URL=https://func-eventos-consumidora-dsy2207.azurewebsites.net/api \
  <uri-imagen-ecr>/bff-service:s7
```

3. Verificar la IP pública actual de la instancia (cambia al reiniciar el lab) y probar `GET http://<ip>:8080/api/bff/estado`.

## Paso 6 — Preparar el contraejemplo de rol (403)

Hoy no existe un usuario con rol `CONSULTA` (idRol 5). Crear uno para el video:

```powershell
$BFF = "http://<ip-ec2>:8080"
Invoke-RestMethod -Method Post -Uri "$BFF/api/bff/usuarios" -ContentType "application/json" `
  -Body (@{ nombreUsuario="Usuario Demo Consulta"; profesionUsuario="Recepcion"; pais="Chile"; idRol=5 } | ConvertTo-Json)
# anotar el idUsuario devuelto -> usarlo como X-Usuario-Id en la prueba 403
```

## Paso 7 — Git (criterio 5)

```powershell
cd C:\vet
git pull
git checkout -b feature/s7-event-grid
git add function-eventos-productora function-eventos-consumidora bff-service db/script_eventos_s7.sql docs/arquitectura-eda-s7.* docs/despliegue-s7.md docs/postman-s7.md docs/postman_collection_s7.json README.md .gitignore
git status        # confirmar que NO aparecen local.settings.json ni docs/guion-video-s7.md
git commit -m "S7F5: capa de eventos con Azure Event Grid (productora, consumidora, BFF)"
git push -u origin feature/s7-event-grid
```

Abrir un Pull Request hacia `main` y que **Cynthia** lo revise y haga el merge (evidencia de trabajo colaborativo). Idealmente Cynthia sube también su propio commit (p.ej. evidencias o ajustes de la colección Postman).

## Solución de problemas

| Síntoma | Causa probable / solución |
|---|---|
| Productora responde 500 "Faltan las App Settings EVENTGRID_TOPIC_ENDPOINT…" | Falta el paso 2 (appsettings set). Después de cambiar settings la app se reinicia sola. |
| Productora responde 401 | Falta el header `x-functions-key` (o la key es de otra Function App). |
| Productora responde 502 "Error al publicar evento en Event Grid" | Endpoint o key del topic incorrectos; revisar que el endpoint termine en `/api/events`. |
| El topic muestra "Unmatched events" | No hay suscripciones creadas o el filtro de `sub-clinica` no coincide con el `eventType`. |
| La consumidora no registra nada | Revisar *Function → Monitor*; si hay error de Oracle, faltan o están mal las App Settings `ORACLE_*` (paso 3). Event Grid reintentará la entrega. |
| `/api/consultas/...` responde `ORA-00942` | La consumidora apunta a otra base: revisar que `ORACLE_DB_URL` sea la de **VeterinariaCloud** y que se haya corrido el paso 0. |
| Un evento aparece como `RECHAZADO` en `/consultas/procesados` | Error de negocio (mascota/veterinario/ítem inexistente, stock insuficiente, cita ya cancelada). No se reintenta. |
| La creación de la suscripción falla por validación del endpoint | La Function App consumidora debe estar desplegada y encendida antes del paso 4. |
| BFF responde 403 | Correcto si el usuario tiene rol `CONSULTA`; si no, revisar `EVENTOS_ROLES_AUTORIZADOS`. |
