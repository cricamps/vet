# Despliegue — Semana 8 (Sumativa 3): Usuarios y Roles con Azure Event Grid

> **Estado real (30-09-2026):** pasos 1 a 5 y 7 ya ejecutados. Topic `usuarios-roles-events-37854`; suscripciones `sub-auditoria`, `sub-usuarios`, `sub-roles` en *Succeeded*; `func-usuarios` y `func-roles` redesplegadas; `func-eventos-usuarios-roles-dsy2207` creada con sus 4 funciones; BFF `bff-service:s8` corriendo en EC2 (`http://100.52.252.86:8080`). **Falta solo el bloque de App Settings con secretos** (ver "Paso 3b/4b — secretos") y luego las pruebas.

Orden: **0) rama Git → 1) tablas Oracle → 2) Event Grid Topic → 3) funciones generadoras (usuarios y roles) → 4) funciones consumidoras → 5) suscripciones → 6) pruebas directas → 7) BFF en EC2 → 8) Postman → 9) Pull Request**.

Comandos para **PowerShell** en Windows, desde `C:\vet`, con la cuenta `cri.camps@duocuc.cl` (suscripción *Azure for Students*).

```powershell
az login
az account show --query name -o tsv      # "Azure for Students"

$RG   = "rg-dsy2207-usuarios-roles"
$LOC  = "brazilsouth"
$FUSR = "func-usuarios-dsy2207-18514"           # generadora (ya existe desde la S3)
$FROL = "func-roles-dsy2207-14376"              # generadora (ya existe desde la S3)
$CONS = "func-eventos-usuarios-roles-dsy2207"   # consumidora (nueva) = <functionAppName> del pom
```

---

## Paso 0 — Rama de trabajo

La S8 se hace en una rama nueva **a partir de `feature/s7-event-grid`**, para que la imagen del BFF conserve también los endpoints de la Formativa 5 (el BFF de EC2 es uno solo).

```powershell
cd C:\vet
git checkout feature/s7-event-grid
git pull
git checkout -b feature/s8-eventos-usuarios-roles
```

## Paso 1 — Tablas en Oracle (`usuariosroles`)

*OCI → Base de datos de IA autónoma → **usuariosroles** → Acciones de base de datos → SQL* → pegar `db/script_eventos_s8.sql` → **Ejecutar script (F5)**.

Crea `UR_AUDITORIA_EVENTOS`, `UR_EVENTOS_PROCESADOS` y `UR_NOTIFICACIONES`, y asegura que exista el rol `CONSULTA` (rol por defecto). **No modifica** `USUARIOS` ni `ROLES`.

Verificación (al final del script): aparecen las 3 tablas `UR_*` y la lista de roles (1 ADMINISTRADOR, 4 OPERADOR, 5 CONSULTA, 10 SOPORTE).

## Paso 2 — Event Grid Topic

Portal: *Event Grid Topics → Create* → nombre `usuarios-roles-events-NNNNN`, región Brazil South, **Event Grid Schema**.

```powershell
az provider show --namespace Microsoft.EventGrid --query registrationState -o tsv   # "Registered" (ya se hizo en la S7)

$TOPIC = "usuarios-roles-events-" + (Get-Random -Minimum 10000 -Maximum 99999)
az eventgrid topic create -g $RG -n $TOPIC -l $LOC --input-schema eventgridschema

$EG_ENDPOINT = az eventgrid topic show -g $RG -n $TOPIC --query endpoint -o tsv
$EG_KEY      = az eventgrid topic key list -g $RG -n $TOPIC --query key1 -o tsv
$TOPIC_ID    = az eventgrid topic show -g $RG -n $TOPIC --query id -o tsv
$TOPIC; $EG_ENDPOINT      # anotar el nombre del topic
```

## Paso 3 — Funciones GENERADORAS (`function-usuarios` y `function-roles`)

```powershell
cd C:\vet\function-usuarios
mvn clean package            # compila y corre UsuarioServiceTest
mvn azure-functions:deploy   # redeploy sobre func-usuarios-dsy2207-18514 (no crea recurso nuevo)

cd C:\vet\function-roles
mvn clean package            # compila y corre RolServiceTest
mvn azure-functions:deploy   # redeploy sobre func-roles-dsy2207-14376
```

App Settings del topic (no van en el código ni en GitHub):

```powershell
az functionapp config appsettings set -g $RG -n $FUSR --settings "EVENTGRID_TOPIC_ENDPOINT=$EG_ENDPOINT" "EVENTGRID_TOPIC_KEY=$EG_KEY"
az functionapp config appsettings set -g $RG -n $FROL --settings "EVENTGRID_TOPIC_ENDPOINT=$EG_ENDPOINT" "EVENTGRID_TOPIC_KEY=$EG_KEY" "ROL_POR_DEFECTO=CONSULTA"
```

> El `azure-functions:deploy` conserva las App Settings existentes (`ORACLE_DB_*`), solo agrega/actualiza las que se indican.

## Paso 4 — Funciones CONSUMIDORAS (`function-eventos-usuarios-roles`)

```powershell
cd C:\vet\function-eventos-usuarios-roles
mvn clean package            # compila y corre EventoDominioTest
mvn azure-functions:deploy   # crea func-eventos-usuarios-roles-dsy2207 y publica 4 funciones
```

La consumidora usa la **misma base** que `func-usuarios`; se copian sus datos de conexión:

```powershell
$ORA_URL  = az functionapp config appsettings list -g $RG -n $FUSR --query "[?name=='ORACLE_DB_URL'].value" -o tsv
$ORA_USER = az functionapp config appsettings list -g $RG -n $FUSR --query "[?name=='ORACLE_DB_USER'].value" -o tsv
$ORA_PASS = az functionapp config appsettings list -g $RG -n $FUSR --query "[?name=='ORACLE_DB_PASSWORD'].value" -o tsv

az functionapp config appsettings set -g $RG -n $CONS --settings "ORACLE_DB_URL=$ORA_URL" "ORACLE_DB_USER=$ORA_USER" "ORACLE_DB_PASSWORD=$ORA_PASS" "ROL_POR_DEFECTO=CONSULTA"

az functionapp function list -g $RG -n $CONS --query "[].name" -o tsv
# AuditarEventoUsuariosRoles, ProcesarEventoUsuario, ProcesarEventoRol, ConsultarEventosUsuariosRoles
```

Prueba: `Invoke-RestMethod "https://$CONS.azurewebsites.net/api/consultas/usuarios" | ConvertTo-Json -Depth 5` → usuarios con su `NOMBRE_ROL`.

## Paso 3b/4b — secretos (pegar en Azure Cloud Shell, bash)

```bash
RG=rg-dsy2207-usuarios-roles; T=usuarios-roles-events-37854
EP=$(az eventgrid topic show -g $RG -n $T --query endpoint -o tsv)
K=$(az eventgrid topic key list -g $RG -n $T --query key1 -o tsv)
for A in func-usuarios-dsy2207-18514 func-roles-dsy2207-14376; do
  az functionapp config appsettings set -g $RG -n $A --settings "EVENTGRID_TOPIC_ENDPOINT=$EP" "EVENTGRID_TOPIC_KEY=$K" -o none
done
g(){ az functionapp config appsettings list -g $RG -n func-usuarios-dsy2207-18514 --query "[?name=='$1'].value" -o tsv; }
az functionapp config appsettings set -g $RG -n func-eventos-usuarios-roles-dsy2207 \
  --settings "ORACLE_DB_URL=$(g ORACLE_DB_URL)" "ORACLE_DB_USER=$(g ORACLE_DB_USER)" "ORACLE_DB_PASSWORD=$(g ORACLE_DB_PASSWORD)" -o none
echo LISTO
```

## Paso 5 — Suscripciones

Portal: *Topic → + Event Subscription* → **Event Grid Schema** → Endpoint Type **Azure Function** → Function App `func-eventos-usuarios-roles-dsy2207` → función. Para `sub-usuarios` y `sub-roles`, en *Filter to Event Types* dejar solo los tipos indicados.

```powershell
$CONS_ID = az functionapp show -g $RG -n $CONS --query id -o tsv

# 1) Auditoria: TODOS los eventos
az eventgrid event-subscription create --name sub-auditoria `
  --source-resource-id $TOPIC_ID `
  --endpoint-type azurefunction --endpoint "$CONS_ID/functions/AuditarEventoUsuariosRoles" `
  --max-delivery-attempts 10 --event-ttl 1440

# 2) Usuarios: filtro por eventType
az eventgrid event-subscription create --name sub-usuarios `
  --source-resource-id $TOPIC_ID `
  --endpoint-type azurefunction --endpoint "$CONS_ID/functions/ProcesarEventoUsuario" `
  --included-event-types UsuariosRoles.UsuarioCreado UsuariosRoles.UsuarioModificado UsuariosRoles.UsuarioEliminado `
  --max-delivery-attempts 10 --event-ttl 1440

# 3) Roles: filtro por eventType
az eventgrid event-subscription create --name sub-roles `
  --source-resource-id $TOPIC_ID `
  --endpoint-type azurefunction --endpoint "$CONS_ID/functions/ProcesarEventoRol" `
  --included-event-types UsuariosRoles.RolModificado UsuariosRoles.RolEliminado `
  --max-delivery-attempts 10 --event-ttl 1440

az eventgrid event-subscription list --source-resource-id $TOPIC_ID -o table
```

## Paso 6 — Pruebas directas (sin BFF)

```powershell
$U = "https://$FUSR.azurewebsites.net/api"
$R = "https://$FROL.azurewebsites.net/api"
$C = "https://$CONS.azurewebsites.net/api"

# a) Usuario SIN rol -> la consumidora le asigna CONSULTA
$r = Invoke-WebRequest -Method Post -Uri "$U/usuarios" -ContentType "application/json" `
     -Body (@{ nombreUsuario="Ana Perez"; profesionUsuario="Disenadora"; pais="Chile" } | ConvertTo-Json)
$r.Headers["X-Evento-Tipo"]; $r.Headers["X-Evento-Publicado"]; $r.Content
$idAna = ($r.Content | ConvertFrom-Json).idUsuario
Start-Sleep 5
Invoke-RestMethod "$U/usuarios/$idAna"                                  # idRol = 5 (CONSULTA)
Invoke-RestMethod "$C/consultas/notificaciones?idUsuario=$idAna" | ConvertTo-Json -Depth 5   # ROL_ASIGNADO + BIENVENIDA

# b) Cambio de rol -> notificacion CAMBIO_ROL
Invoke-RestMethod -Method Put -Uri "$U/usuarios/$idAna" -ContentType "application/json" `
  -Body (@{ nombreUsuario="Ana Perez"; profesionUsuario="Disenadora"; pais="Chile"; idRol=4 } | ConvertTo-Json)

# c) Rol en uso eliminado -> reasignacion a CONSULTA
$rol = Invoke-RestMethod -Method Post -Uri "$R/roles" -ContentType "application/json" -Body (@{ nombreRol="AUDITOR" } | ConvertTo-Json)
Invoke-RestMethod -Method Put -Uri "$U/usuarios/$idAna" -ContentType "application/json" `
  -Body (@{ nombreUsuario="Ana Perez"; profesionUsuario="Disenadora"; pais="Chile"; idRol=$rol.idRol } | ConvertTo-Json)
Invoke-WebRequest -Method Delete -Uri "$R/roles/$($rol.idRol)"         # 204 (antes fallaba por la FK)
Start-Sleep 5
Invoke-RestMethod "$U/usuarios/$idAna"                                  # idRol = 5 otra vez
Invoke-RestMethod "$C/consultas/procesados" | ConvertTo-Json -Depth 5
Invoke-RestMethod "$C/consultas/auditoria"  | ConvertTo-Json -Depth 5

# d) Regla de negocio: el rol por defecto no se elimina
Invoke-WebRequest -Method Delete -Uri "$R/roles/5"                      # 409 Conflict
```

> `Invoke-WebRequest` lanza excepción con 4xx; para ver el 409: `try { ... } catch { $_.Exception.Response.StatusCode; $_.ErrorDetails.Message }`.

Evidencias en el portal: *Topic → Overview* (Published / Delivered events, 0 *Unmatched*), *Event Subscription → Overview*, *Function App consumidora → función → Monitor / Invocations* (logs `[auditoria] Evento recibido…`, `[ProcesarEventoUsuario] UsuariosRoles.UsuarioCreado -> PROCESADO…`).

## Paso 7 — BFF en AWS EC2 (vía SSM, igual que en la S7)

1. `git push -u origin feature/s8-eventos-usuarios-roles` (paso 9) **antes** de reconstruir, porque la imagen se construye desde la rama.
2. *AWS Academy → Start Lab → AWS Console → CloudShell*. Revisar primero el script de la S7 y crear el de la S8 a partir de él:

```bash
INSTANCE=i-03a5f366b29b6d0c5

aws ssm send-command --instance-ids $INSTANCE --document-name AWS-RunShellScript \
  --parameters 'commands=["cat /root/deploy-s7.sh"]' --query Command.CommandId --output text
# ver la salida:
aws ssm get-command-invocation --instance-id $INSTANCE --command-id <CommandId> --query StandardOutputContent --output text
```

3. Generar `deploy-s8.sh` (cambia rama y tag de la imagen y agrega la URL de la consumidora) y ejecutarlo:

```bash
aws ssm send-command --instance-ids $INSTANCE --document-name AWS-RunShellScript --parameters 'commands=[
 "sed -e \"s#feature/s7-event-grid#feature/s8-eventos-usuarios-roles#g\" -e \"s#bff-service:s7#bff-service:s8#g\" -e \"s#-e FUNCION_EVENTOS_CONSUMIDORA_URL=#-e FUNCION_EVENTOS_UR_URL=https://func-eventos-usuarios-roles-dsy2207.azurewebsites.net/api -e FUNCION_EVENTOS_CONSUMIDORA_URL=#\" /root/deploy-s7.sh > /root/deploy-s8.sh",
 "chmod +x /root/deploy-s8.sh",
 "grep -n \"s8\\|FUNCION_EVENTOS_UR_URL\" /root/deploy-s8.sh",
 "nohup /root/deploy-s8.sh > /tmp/deploy-s8.log 2>&1 &"
]' --query Command.CommandId --output text

# 3-5 minutos despues:
aws ssm send-command --instance-ids $INSTANCE --document-name AWS-RunShellScript \
  --parameters 'commands=["tail -20 /tmp/deploy-s8.log","docker ps","curl -s localhost:8080/api/bff/estado"]' \
  --query Command.CommandId --output text
```

> Si el `grep` no muestra `FUNCION_EVENTOS_UR_URL`, el `docker run` de la S7 está escrito distinto: editar `/root/deploy-s8.sh` para agregar `-e FUNCION_EVENTOS_UR_URL=https://func-eventos-usuarios-roles-dsy2207.azurewebsites.net/api` junto a las demás variables.

4. IP pública actual de la instancia (cambia al reiniciar el lab): `aws ec2 describe-instances --instance-ids $INSTANCE --query "Reservations[0].Instances[0].PublicIpAddress" --output text`. Probar `GET http://<ip>:8080/api/bff/estado` → descripción "... eventos Azure Event Grid - DSY2207 S8".

## Paso 8 — Postman

Importar `docs/postman_collection_s8.json` y seguir `docs/postman-s8.md`.

## Paso 9 — Git y Pull Request (criterio 9 de la pauta)

```powershell
cd C:\vet
git status        # confirmar que NO aparecen local.settings.json ni docs/guion-video-s8.md
git add function-usuarios function-roles function-eventos-usuarios-roles bff-service db/script_eventos_s8.sql docs/arquitectura-eda-s8.* docs/despliegue-s8.md docs/postman-s8.md docs/postman_collection_s8.json README.md
git commit -m "S8S3: eventos de Usuarios y Roles con Azure Event Grid (generadoras, consumidoras, BFF)"
git push -u origin feature/s8-eventos-usuarios-roles
```

Abrir el Pull Request `feature/s8-eventos-usuarios-roles → main`. Para evidenciar **participación equitativa**, Cynthia hace al menos un commit propio en la rama (p.ej. las pruebas de la colección Postman o las evidencias) y es quien revisa y hace el merge del PR.

## Solución de problemas

| Síntoma | Causa / solución |
|---|---|
| CRUD responde bien pero `X-Evento-Publicado: false` | Faltan o están mal `EVENTGRID_TOPIC_ENDPOINT` / `EVENTGRID_TOPIC_KEY` en esa Function App (paso 3). El endpoint termina en `/api/events`. |
| El topic muestra *Unmatched events* | Faltan suscripciones (paso 5) o el filtro no coincide con el `eventType` (`UsuariosRoles.…`). |
| La consumidora no registra nada | *Function → Monitor*: si hay error de Oracle, revisar `ORACLE_DB_*` (paso 4). Event Grid reintenta hasta 10 veces. |
| `/consultas/...` responde `ORA-00942` | No se ejecutó `db/script_eventos_s8.sql` en `usuariosroles` (paso 1). |
| Evento `RECHAZADO` en `/consultas/procesados` | Error de negocio: usuario ya eliminado o no existe el rol `CONSULTA`. No se reintenta. |
| `DELETE /roles/5` responde 409 | Correcto: `CONSULTA` es el rol por defecto. |
| La suscripción no se crea (validación del endpoint) | La Function App consumidora debe estar desplegada y encendida antes del paso 5. |
| BFF `/api/bff/usuarios-roles/eventos/...` responde 500/404 | El contenedor no tiene `FUNCION_EVENTOS_UR_URL` (paso 7). |
