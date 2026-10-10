# Despliegue — Evaluación Final Transversal (Semana 9)

Parte del estado de la S8 (todo desplegado y verificado el 30-09-2026). Para la EFT cambian solo dos cosas:

1. **Requerimiento 2:** al eliminar un rol, sus usuarios quedan **sin rol** (`idRol = null`) y reciben la notificación `ROL_QUITADO`. Antes se reasignaban a `CONSULTA`. Cambian `function-roles` (el evento ya no lleva `rolReemplazo`) y `function-eventos-usuarios-roles` (`ProcesarEventoRol`).
2. **Integración GraphQL en el BFF:** se agregan `POST /api/bff/graphql/usuarios` y `POST /api/bff/graphql/roles` (`GraphQlBffController` + `GraphQlFunctionClient`).

3. **Oracle:** el CHECK `CK_UR_NOTIF_TIPO` de `UR_NOTIFICACIONES` debe aceptar `ROL_QUITADO` → `db/script_eft_migracion.sql` (sin esto `ProcesarEventoRol` falla y Event Grid reintenta).
4. **Corrección en `function-usuarios`:** `UsuarioDao.map` devolvía `idRol = 0` en vez de `null` para usuarios sin rol (`wasNull()` se consultaba después de leer otra columna).

No cambian el topic de Event Grid, las suscripciones ni las App Settings.

> **Estado real (10-10-2026):** pasos 1 y 2 y la migración Oracle ya ejecutados. Funciones redesplegadas desde Azure Cloud Shell; BFF `bff-service:eft` en EC2 `http://34.204.17.155:8080` (la IP cambia al reiniciar el lab). Prueba de punta a punta OK: usuario sin rol → CONSULTA; rol eliminado → `idRol = null` + `ROL_QUITADO` en ~5 s. Falta: Git/PR (paso 4), zip y videos.

Orden: **0) Git → 1) funciones → 2) BFF en EC2 → 3) Postman → 4) Pull Request → 5) zip**. Comandos en **PowerShell**, desde `C:\vet`.

```powershell
$RG   = "rg-dsy2207-usuarios-roles"
$FUSR = "func-usuarios-dsy2207-18514"
$FROL = "func-roles-dsy2207-14376"
$CONS = "func-eventos-usuarios-roles-dsy2207"
```

## Paso 0 — Git

Primero borrar en el Explorador los archivos `C:\vet\.git\index.lock.stale-claude*` si siguen ahí. Luego:

```powershell
cd C:\vet
git status                      # rama feature/s8-eventos-usuarios-roles con los cambios S8 + EFT sin commitear
git checkout -b feature/eft-s9  # la rama nueva se lleva los cambios del working tree
```

## Paso 1 — Funciones (compilar, probar y redesplegar)

```powershell
az login

cd C:\vet\function-roles
mvn clean package               # RolServiceTest: el evento RolEliminado ya no trae rolReemplazo
mvn azure-functions:deploy

cd C:\vet\function-eventos-usuarios-roles
mvn clean package
mvn azure-functions:deploy

cd C:\vet\function-usuarios
mvn clean package               # corrección idRol null
mvn azure-functions:deploy
```

> `azure-functions:deploy` conserva las App Settings (`ORACLE_DB_*`, `EVENTGRID_*`, `ROL_POR_DEFECTO`).

Prueba rápida, sin BFF:

```powershell
$U = "https://$FUSR.azurewebsites.net/api"; $R = "https://$FROL.azurewebsites.net/api"
$u = Invoke-RestMethod -Method Post "$U/usuarios" -ContentType "application/json" -Body (@{nombreUsuario="Prueba EFT"; pais="Chile"} | ConvertTo-Json)
Start-Sleep 5; Invoke-RestMethod "$U/usuarios/$($u.idUsuario)"            # idRol = 5 (requerimiento 1)
$r = Invoke-RestMethod -Method Post "$R/roles" -ContentType "application/json" -Body (@{nombreRol="TEMP EFT"} | ConvertTo-Json)
Invoke-RestMethod -Method Put "$U/usuarios/$($u.idUsuario)" -ContentType "application/json" -Body (@{nombreUsuario="Prueba EFT"; pais="Chile"; idRol=$r.idRol} | ConvertTo-Json)
Invoke-WebRequest -Method Delete "$R/roles/$($r.idRol)"                    # 204
Start-Sleep 5; Invoke-RestMethod "$U/usuarios/$($u.idUsuario)"            # idRol vacío (requerimiento 2)
Invoke-RestMethod "https://$CONS.azurewebsites.net/api/consultas/notificaciones?idUsuario=$($u.idUsuario)"   # ... ROL_QUITADO
Invoke-WebRequest -Method Delete "$U/usuarios/$($u.idUsuario)"
```

## Paso 2 — BFF en AWS EC2

1. `git push -u origin feature/eft-s9` (la imagen se construye desde la rama).
2. *AWS Academy → Start Lab → AWS Console → CloudShell*:

```bash
INSTANCE=i-03a5f366b29b6d0c5
aws ssm send-command --instance-ids $INSTANCE --document-name AWS-RunShellScript --parameters 'commands=[
 "sed -e \"s#feature/s8-eventos-usuarios-roles#feature/eft-s9#g\" -e \"s#bff-service:s8#bff-service:eft#g\" /root/deploy-s8.sh > /root/deploy-eft.sh",
 "chmod +x /root/deploy-eft.sh",
 "grep -n \"eft\" /root/deploy-eft.sh",
 "nohup /root/deploy-eft.sh > /tmp/deploy-eft.log 2>&1 &"
]' --query Command.CommandId --output text

# 3-5 minutos después:
aws ssm send-command --instance-ids $INSTANCE --document-name AWS-RunShellScript \
  --parameters 'commands=["tail -20 /tmp/deploy-eft.log","docker ps","curl -s localhost:8080/api/bff/estado"]' \
  --query Command.CommandId --output text
aws ssm get-command-invocation --instance-id $INSTANCE --command-id <CommandId> --query StandardOutputContent --output text

# IP pública actual (cambia cada vez que se reinicia el lab):
aws ec2 describe-instances --instance-ids $INSTANCE --query "Reservations[0].Instances[0].PublicIpAddress" --output text
```

`GET http://<ip>:8080/api/bff/estado` debe responder `"... (REST + GraphQL) ... DSY2207 EFT"`. Si sigue diciendo S8, el contenedor no se reconstruyó: revisar `/tmp/deploy-eft.log`. Para volver atrás, la imagen `bff-service:s8` sigue disponible.

## Paso 3 — Postman

Importar `docs/postman_collection_eft.json`, poner la IP en la variable `bffUrl` y ejecutar las carpetas 0 a 3 con *Run collection*. Todas las pruebas deben pasar en verde (sirve como evidencia en el video).

## Paso 4 — Git y Pull Request (criterio 1 de la pauta: participación equitativa)

Cristóbal (código):

```powershell
git add function-roles function-eventos-usuarios-roles bff-service function-usuarios db README.md docs/despliegue-eft.md docs/arquitectura-eft.* docs/despliegue-eft.md docs/README-s3-original.md
git add docs/arquitectura-eda-s8.* docs/despliegue-s8.md docs/postman-s8.md docs/postman_collection_s8.json db/script_eventos_s8.sql
git commit -m "EFT: rol eliminado se quita a sus usuarios (ROL_QUITADO) e integracion GraphQL en el BFF"
git push
```

Cynthia (en su equipo, después de `git pull` de la rama):

```powershell
git add docs/postman_collection_eft.json
git commit -m "EFT: coleccion Postman con flujo REST y GraphQL via BFF"
git push
```

Abrir el PR `feature/eft-s9 → main`. Lo revisa y hace el merge el integrante que no lo abrió. Si el PR de la S8 sigue pendiente, este lo reemplaza, porque la rama incluye todo lo de la S8.

## Paso 5 — Zip de entrega

Desde `C:\vet`, en PowerShell (excluye `target/`, `local.settings.json`, `.git` y lo que no es del caso):

```powershell
$dst = "C:\vet\entregas-preparadas\DSY2207_EFT_codigo_fuente.zip"
$tmp = "$env:TEMP\eft-zip"; Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
robocopy C:\vet $tmp /E /XD target .git entregas-preparadas "Claude outputs" git function-eventos-productora function-eventos-consumidora bin obj /XF git local.settings.json *.env "guion-video*.md" *.docx | Out-Null
Compress-Archive -Path "$tmp\*" -DestinationPath $dst -Force
```

En el AVA, subir el zip junto con el **link del repositorio** y el **link del video de Kaltura**.

## Solución de problemas

| Síntoma | Causa / solución |
|---|---|
| El usuario sigue quedando con rol 5 tras eliminar un rol | `func-eventos-usuarios-roles-dsy2207` no se redesplegó (paso 1). |
| `/api/bff/graphql/...` responde 404 | El BFF de EC2 sigue con la imagen S8 (paso 2). |
| GraphQL responde `errors` "rol por defecto" | Correcto si se intenta `eliminarRol(id: 5)`: es el rol protegido. |
| Postman no llega al BFF | Cambió la IP de la EC2 o el Security Group no permite el puerto 8080. |
