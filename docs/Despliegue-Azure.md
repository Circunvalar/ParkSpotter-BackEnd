# Despliegue en Azure App Service

El backend corre en **Azure App Service para contenedores (Linux)** con la imagen Docker del repositorio, y se conecta a la base **Azure SQL** que ya existe (`parkspotter.database.windows.net`, base `ParkSpotterDatabase`). Azure da el HTTPS, el dominio `*.azurewebsites.net` y el certificado; no hace falta Caddy ni una máquina virtual.

```
Web (navegador) ──┐                                   ┌──────────────────────────────┐
                  ├─ HTTPS 443 ─▶ App Service (TLS) ─▶│ Contenedor Spring Boot :8080 │──1433──▶ Azure SQL
App Android ──────┘                                   └──────────────────────────────┘
                                   imagen: ghcr.io/circunvalar/parkspotter-backend
```

Cómo se conectan el front web y la app móvil, y qué necesitan, está en [Conexion-Web-y-Movil.md](Conexion-Web-y-Movil.md).

Los pasos marcados con **[manual]** se hacen en una consola web o requieren una decisión. El resto son comandos de `az` (Azure CLI) para pegar en una terminal.

> Tiempo estimado: 30 a 45 minutos la primera vez.

---

## 0. Plan y costo

| Plan | Precio aprox. (Linux) | ¿Sirve? |
|---|---|---|
| **F1 (Free)** | US$0 | **No para producción.** 60 min de CPU al día, sin *Always On*: la app se duerme sin tráfico, tarda en despertar y las tareas automáticas (vencer reservas sin pago, recordatorios) dejan de correr. Tampoco permite certificado en dominio propio. |
| **B1 (Basic)** — recomendado | **≈ US$13 al mes** (≈ US$0,018 por hora) | 1 núcleo, 1,75 GB de RAM, *Always On*, dominio propio con certificado gratis. Alcanza para el proyecto. |
| B2 / P0v3 | ≈ US$26 / ≈ US$60 al mes | Solo si B1 se queda corto de memoria o CPU. |

Los precios cambian según la región y la moneda: confirmarlos en la [calculadora de precios](https://azure.microsoft.com/pricing/calculator/) eligiendo *App Service → Linux → Basic B1*. La base Azure SQL se cobra aparte (ya está creada). Guardar la imagen en GitHub Container Registry (GHCR) es gratis; Azure Container Registry Basic costaría ≈ US$5 más al mes.

Con **Azure for Students** (correo de la universidad, sin tarjeta) hay US$100 de crédito al año, suficiente para B1 durante varios meses.

**Una sola instancia.** El tiempo real (SSE), las tareas programadas y los límites de intentos de login viven en la memoria de la app. Con 2 o más instancias los avisos en vivo solo llegarían a parte de los clientes y las tareas correrían dos veces. Mantener la app en 1 instancia y, si hace falta más potencia, subir de plan (escalar verticalmente).

## 1. Requisitos **[manual]**

1. Cuenta de Azure con una suscripción activa (<https://portal.azure.com>).
2. Acceso al servidor SQL `parkspotter` (para abrir el firewall) y la contraseña de la base.
3. Una terminal con Azure CLI 2.60 o más reciente. Lo más fácil es **Azure Cloud Shell**: en el portal, el ícono `>_` de la barra superior, elegir **Bash**. Ya trae `az` y la sesión iniciada. En una computadora propia: instalar Azure CLI y ejecutar `az login`.

Los comandos usan variables de Bash. Definirlas una vez por sesión de terminal (cambiar `APP` por un nombre único en todo Azure: solo minúsculas, números y guiones):

```bash
RG=rg-parkspotter                  # grupo de recursos
LOCATION=eastus                    # misma región que el servidor SQL (ver abajo)
PLAN=plan-parkspotter
APP=parkspotter-api                # la URL será https://<APP>.azurewebsites.net (o una variante, ver paso 5)
IMAGE=ghcr.io/circunvalar/parkspotter-backend:latest
SQL_SERVER=parkspotter
```

Para saber la región del servidor SQL (conviene que la app esté en la misma, así hay menos latencia y no se paga tráfico entre regiones):

```bash
az sql server list --query "[?name=='$SQL_SERVER'].{grupo:resourceGroup, region:location}" -o table
SQL_RG=<grupo que aparece arriba>
```

## 2. Publicar la imagen Docker

App Service descarga la imagen de un registro. La opción recomendada es GitHub Container Registry, que ya queda automatizada con el workflow [`deploy-azure.yml`](../.github/workflows/deploy-azure.yml).

### Opción A: GHCR con GitHub Actions (recomendada)

1. Integrar a `master` el código que se quiere desplegar. Cuando la CI pasa en `master`, el workflow **Despliegue - Azure App Service** construye la imagen y la publica como `ghcr.io/circunvalar/parkspotter-backend:latest` y `:sha-<commit>`. También se puede lanzar a mano: GitHub → **Actions** → *Despliegue - Azure App Service* → **Run workflow**.
2. **[manual]** La primera vez, hacer pública la imagen para que Azure la descargue sin credenciales (la imagen no tiene secretos: todos van en las variables de la app): GitHub → perfil u organización → **Packages** → `parkspotter-backend` → **Package settings** → **Change visibility** → *Public*.

   Si prefieren dejarla privada, crear un token clásico de GitHub con permiso `read:packages` y usarlo en el paso 4 como contraseña del registro.

### Opción B: construirla y subirla a mano

Desde la carpeta del repositorio, en una computadora con Docker (App Service ejecuta imágenes `linux/amd64`):

```bash
echo <token-github-con-write:packages> | docker login ghcr.io -u <usuario-github> --password-stdin
docker build --platform linux/amd64 -t ghcr.io/circunvalar/parkspotter-backend:latest .
docker push ghcr.io/circunvalar/parkspotter-backend:latest
```

### Opción C: Azure Container Registry (si prefieren no usar GitHub)

```bash
ACR=parkspotteracr                 # nombre único, solo letras y números
az acr create -g $RG -n $ACR --sku Basic
az acr build -r $ACR -t parkspotter-backend:latest .     # compila en Azure, no necesita Docker local
IMAGE=$ACR.azurecr.io/parkspotter-backend:latest
```

En el paso 4, crear la app con `--container-image-name $IMAGE` sin usuario ni contraseña de registro y darle acceso con identidad administrada:

```bash
az webapp identity assign -g $RG -n $APP
az role assignment create --assignee "$(az webapp identity show -g $RG -n $APP --query principalId -o tsv)" \
  --role AcrPull --scope "$(az acr show -n $ACR --query id -o tsv)"
az webapp config set -g $RG -n $APP --generic-configurations '{"acrUseManagedIdentityCreds": true}'
```

## 3. Grupo de recursos y plan

```bash
az group create -n $RG -l $LOCATION
az appservice plan create -g $RG -n $PLAN --is-linux --sku B1 --number-of-workers 1
```

## 4. Crear la Web App para contenedores

Imagen pública en GHCR:

```bash
az webapp create -g $RG -p $PLAN -n $APP \
  --container-image-name $IMAGE \
  --container-registry-url https://ghcr.io
```

Imagen privada en GHCR: agregar `--container-registry-user <usuario-github> --container-registry-password <token-read-packages>`.

Configuración general (puerto, Always On, HTTPS, health check, una instancia):

```bash
az webapp config set -g $RG -n $APP \
  --always-on true \
  --http20-enabled true \
  --min-tls-version 1.2 \
  --ftps-state Disabled \
  --generic-configurations '{"healthCheckPath": "/v3/api-docs"}'
az webapp update -g $RG -n $APP --https-only true --client-affinity-enabled false
```

## 5. Variables de entorno (Application settings)

Los secretos se guardan en **Application settings** de la app, nunca en el código ni en el repositorio. Azure los cifra y los entrega al contenedor como variables de entorno.

Primero obtener la URL pública real (en apps nuevas Azure puede agregar un sufijo, por ejemplo `parkspotter-api-abc123.eastus-01.azurewebsites.net`):

```bash
API_HOST=$(az webapp show -g $RG -n $APP --query defaultHostName -o tsv)
echo "https://$API_HOST"
```

Generar el secreto de los tokens:

```bash
JWT_SECRET=$(openssl rand -hex 48)
```

Escribir la contraseña de la base sin que quede en el historial de la terminal:

```bash
read -rs -p "Contraseña de Azure SQL: " DB_PASSWORD; echo
```

Cargar las variables (reemplazar `https://parkspotter-web.ejemplo.com` por la URL real del front web):

```bash
WEB=https://parkspotter-web.ejemplo.com

az webapp config appsettings set -g $RG -n $APP --output none --settings \
  WEBSITES_PORT=8080 \
  WEBSITES_CONTAINER_START_TIME_LIMIT=600 \
  DB_PASSWORD="$DB_PASSWORD" \
  JWT_SECRET="$JWT_SECRET" \
  APP_BOOTSTRAP_ROLES=true \
  ADMIN_EMAIL=admin@ejemplo.com \
  APP_CORS_ALLOWED_ORIGINS="$WEB,http://localhost:3000" \
  PAYMENTS_PROVIDER=sandbox \
  PAYMENTS_REDIRECT_PREFIXES="$WEB/,parkspotter://" \
  PAYMENTS_DEFAULT_REDIRECT="$WEB/pagos/resultado" \
  RESET_PASSWORD_URL="$WEB/restablecer-contrasena"

unset DB_PASSWORD JWT_SECRET
```

`--output none` evita que la respuesta imprima los valores en pantalla. Cada cambio de variables reinicia la app.

| Variable | Obligatoria | Para qué |
|---|---|---|
| `WEBSITES_PORT` | Sí | Puerto en el que escucha el contenedor: `8080`. |
| `WEBSITES_CONTAINER_START_TIME_LIMIT` | Recomendada | Segundos que Azure espera a que arranque. Spring Boot con Azure SQL tarda 1 o 2 min en B1; `600` evita reinicios en falso. |
| `DB_PASSWORD` | Sí | Contraseña del usuario de Azure SQL. Sin ella la app no arranca. |
| `DB_URL` | No | URL JDBC si cambia el servidor o la base. Por defecto `jdbc:sqlserver://parkspotter.database.windows.net:1433;database=ParkSpotterDatabase;encrypt=true;…` |
| `DB_USERNAME` | No | Usuario de la base. Por defecto `ParkSpotterDatabaseManager@parkspotter`. |
| `JWT_SECRET` | Sí | Firma de los tokens. Mínimo 64 caracteres aleatorios. Si cambia, todas las sesiones se cierran. |
| `APP_BOOTSTRAP_ROLES` | Sí | `true`: crea los roles al arrancar. |
| `ADMIN_EMAIL` | Recomendada | Cuenta que recibe `ROLE_ADMIN` al arrancar (ver paso 9). |
| `APP_CORS_ALLOWED_ORIGINS` | Sí, si hay front web | Orígenes del front web separados por coma, sin `/` final. |
| `PAYMENTS_PROVIDER` | No | `sandbox` (simulado, por defecto) o `wompi`. |
| `PAYMENTS_REDIRECT_PREFIXES` | Sí, si hay pagos | Prefijos permitidos para volver después de pagar: URL del front con `/` final y `parkspotter://` para la app. |
| `PAYMENTS_DEFAULT_REDIRECT` | No | Página del front que muestra el resultado del pago. |
| `RESET_PASSWORD_URL` | Sí, si hay correo | Página del front para restablecer la contraseña. |
| `WOMPI_*`, `MAIL_*`, `FCM_*` | No | Pagos reales, correo y push (paso 10). |

También se pueden editar en el portal: la app → **Settings** → **Environment variables** → **App settings**. Para guardar los secretos en Azure Key Vault, el valor de la variable puede ser `@Microsoft.KeyVault(SecretUri=…)`; no es necesario para empezar.

## 6. Abrir el firewall de Azure SQL

Azure SQL rechaza las conexiones que no estén permitidas en su firewall. Hay dos formas; elegir una.

**A. Permitir los servicios de Azure (la más simple).** Deja entrar a cualquier recurso que corra en Azure; la base sigue protegida por usuario y contraseña.

```bash
az sql server firewall-rule create -g $SQL_RG -s $SQL_SERVER -n AllowAzureServices \
  --start-ip-address 0.0.0.0 --end-ip-address 0.0.0.0
```

Equivale, en el portal, a servidor SQL → **Networking** → *Allow Azure services and resources to access this server*.

**B. Solo las IP de salida de la app (más restrictiva).**

```bash
for ip in $(az webapp show -g $RG -n $APP --query possibleOutboundIpAddresses -o tsv | tr ',' ' '); do
  az sql server firewall-rule create -g $SQL_RG -s $SQL_SERVER -n "app-${ip//./-}" \
    --start-ip-address $ip --end-ip-address $ip --output none
done
```

Si después se cambia el plan de precio, las IP de salida pueden cambiar: repetir el comando.

Para conectarse desde la computadora de un desarrollador (por ejemplo, para correr la API en local contra Azure SQL), agregar su IP pública: servidor SQL → **Networking** → **Add your client IPv4 address**.

## 7. Arrancar y verificar

```bash
az webapp restart -g $RG -n $APP
az webapp log config -g $RG -n $APP --docker-container-logging filesystem
az webapp log tail -g $RG -n $APP          # Ctrl+C para salir; esperar "Started BackendParkSpotterApplication"
```

Probar desde el navegador:

- `https://<API_HOST>/swagger-ui.html` abre la documentación interactiva.
- `https://<API_HOST>/api/v1/garages/search` responde JSON.

Prueba de humo completa (crea datos de prueba con nombres únicos; necesita `PAYMENTS_PROVIDER=sandbox`). `ORIGIN` debe ser uno de los orígenes de `APP_CORS_ALLOWED_ORIGINS`:

```bash
BASE_URL=https://$API_HOST ORIGIN=http://localhost:3000 MAX_SECONDS=5 ./scripts/smoke-test.sh
```

## 8. Dominio propio (opcional) **[manual]**

Con la URL `https://<API_HOST>` ya funciona todo. Para usar, por ejemplo, `api.parkspotter.co`:

1. En el proveedor del dominio, crear:
   - un registro **CNAME** `api` → `<API_HOST>`;
   - un registro **TXT** `asuid.api` con el valor de `az webapp show -g $RG -n $APP --query customDomainVerificationId -o tsv`.
2. Agregar el dominio y el certificado gratuito administrado por Azure:

   ```bash
   az webapp config hostname add -g $RG --webapp-name $APP --hostname api.parkspotter.co
   THUMB=$(az webapp config ssl create -g $RG -n $APP --hostname api.parkspotter.co --query thumbprint -o tsv)
   az webapp config ssl bind -g $RG -n $APP --certificate-thumbprint $THUMB --ssl-type SNI
   ```

   El certificado se renueva solo. Después, actualizar la URL base en el front web, en la app y en el webhook de Wompi.

## 9. Crear la cuenta administradora

1. **[manual]** Registrarse en la app o en Swagger (`POST /api/v1/auth/register`) con el correo puesto en `ADMIN_EMAIL`.
2. Reiniciar la app para que le asigne el rol: `az webapp restart -g $RG -n $APP`.
3. Al iniciar sesión de nuevo, `roles` incluye `ROLE_ADMIN`. Desde ahí se dan más roles con `PUT /api/v1/admin/users/{id}/roles`.

## 10. Servicios opcionales

Se cargan igual que en el paso 5, con `az webapp config appsettings set … --settings CLAVE=valor` o desde el portal.

### Pagos con Wompi (sandbox gratis) **[manual]**

1. Crear la cuenta en <https://comercios.wompi.co> y entrar al ambiente de **pruebas**.
2. En **Desarrolladores**, copiar la llave pública (`pub_test_…`), el secreto de **integridad** y el secreto de **eventos**.
3. Configurar la **URL de eventos**: `https://<API_HOST>/api/v1/payments/webhooks/wompi`.
4. Variables: `PAYMENTS_PROVIDER=wompi`, `WOMPI_PUBLIC_KEY`, `WOMPI_INTEGRITY_SECRET`, `WOMPI_EVENTS_SECRET`.

Para cobrar de verdad se cambian por las llaves de producción (`pub_prod_…`) cuando Wompi apruebe el comercio.

### Correo (recuperación de contraseña y avisos) **[manual]**

Con Gmail: activar la verificación en dos pasos y crear una **contraseña de aplicación** en <https://myaccount.google.com/apppasswords>. Variables: `MAIL_ENABLED=true`, `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_USERNAME`, `MAIL_PASSWORD` (la contraseña de aplicación), `MAIL_FROM="ParkSpotter <cuenta@gmail.com>"`.

App Service bloquea la salida por el puerto 25, pero el 587 funciona. Sin correo configurado, los mensajes solo se escriben en el log.

### Notificaciones push con Firebase **[manual]**

1. En <https://console.firebase.google.com>, crear el proyecto (el mismo de la app Android).
2. **Configuración del proyecto** → **Cuentas de servicio** → **Generar nueva clave privada**.
3. Del JSON descargado, copiar `project_id`, `client_email` y `private_key` a `FCM_PROJECT_ID`, `FCM_CLIENT_EMAIL` y `FCM_PRIVATE_KEY`, y poner `FCM_ENABLED=true`. `FCM_PRIVATE_KEY` va en una sola línea, tal como aparece en el JSON (con los `\n`). Es más cómodo pegarla en el portal que en la terminal.

## 11. Despliegue automático desde GitHub (opcional)

El workflow [`deploy-azure.yml`](../.github/workflows/deploy-azure.yml) siempre publica la imagen. Si además encuentra la configuración de Azure, apunta la app a la imagen nueva (`:sha-<commit>`), la reinicia y espera a que responda. Para activarlo:

1. Crear una identidad para GitHub con permiso solo sobre el grupo de recursos:

   ```bash
   az ad sp create-for-rbac --name parkspotter-github-deploy --role contributor \
     --scopes $(az group show -n $RG --query id -o tsv) --json-auth
   ```

2. **[manual]** En GitHub → repositorio → **Settings** → **Secrets and variables** → **Actions**:
   - **Secrets** → `AZURE_CREDENTIALS`: pegar el JSON completo que imprimió el comando anterior.
   - **Variables** → `AZURE_WEBAPP_NAME`: el valor de `$APP`; `AZURE_RESOURCE_GROUP`: el valor de `$RG`.

Sin esa variable, el workflow solo publica la imagen y la app se actualiza a mano:

```bash
az webapp config container set -g $RG -n $APP --container-image-name ghcr.io/circunvalar/parkspotter-backend:latest
az webapp restart -g $RG -n $APP
```

Si la cuenta de Azure no deja crear la identidad (pasa en algunas cuentas de estudiante administradas por la universidad), usar la actualización manual de arriba.

## 12. Comandos útiles

| Para | Comando |
|---|---|
| Ver logs en vivo | `az webapp log tail -g $RG -n $APP` |
| Reiniciar | `az webapp restart -g $RG -n $APP` |
| Ver variables (oculta los valores en el portal) | `az webapp config appsettings list -g $RG -n $APP --query "[].name"` |
| Cambiar una variable | `az webapp config appsettings set -g $RG -n $APP --output none --settings CLAVE=valor` |
| Ver la imagen actual | `az webapp config container show -g $RG -n $APP` |
| Volver a una versión anterior | `az webapp config container set -g $RG -n $APP --container-image-name ghcr.io/circunvalar/parkspotter-backend:sha-<commit>` |
| Apagar (deja de responder; el plan B1 se sigue cobrando) | `az webapp stop -g $RG -n $APP` |
| Borrar todo lo creado aquí (no toca Azure SQL) | `az group delete -n $RG` |

## 13. Problemas comunes

| Síntoma | Causa probable | Solución |
|---|---|---|
| *Application Error* o la app reinicia en bucle | Falta `DB_PASSWORD` o `WEBSITES_PORT`, o la contraseña no coincide | `az webapp log tail`. Revisar el paso 5. |
| En el log: `Cannot open server 'parkspotter' requested by the login. Client with IP address '…' is not allowed` | Firewall de Azure SQL | Paso 6. |
| *Container didn't respond to HTTP pings on port 8080* | Arranque lento o puerto mal configurado | `WEBSITES_PORT=8080` y `WEBSITES_CONTAINER_START_TIME_LIMIT=600`. |
| `ImagePullFailure` o *manifest unknown* | La imagen es privada o el nombre está mal | Hacerla pública (paso 2) o configurar usuario y token del registro. |
| El front web recibe error de CORS | Falta su origen | Agregarlo a `APP_CORS_ALLOWED_ORIGINS`, sin `/` final. |
| `redirectUrl no permitida` al pagar | Falta el prefijo | Agregar la URL del front a `PAYMENTS_REDIRECT_PREFIXES`. |
| Las reservas sin pago no vencen o la primera petición tarda mucho | La app se durmió | Plan B1 o superior con `--always-on true` (paso 4). |
| Los avisos en vivo llegan a unos clientes sí y a otros no | Más de una instancia | Volver a 1: `az appservice plan update -g $RG -n $PLAN --number-of-workers 1`. |

## Seguridad

- Los secretos (`DB_PASSWORD`, `JWT_SECRET`, `WOMPI_*`, `MAIL_PASSWORD`, `FCM_PRIVATE_KEY`) solo viven en las Application settings de Azure y en los secretos de GitHub. Nunca en `application.properties`, en el `Dockerfile` ni en commits.
- `--https-only true` redirige todo el tráfico HTTP a HTTPS, con TLS 1.2 como mínimo.
- El contenedor corre con un usuario sin privilegios y solo expone el puerto 8080 a la red interna de App Service.

## Resumen de pasos manuales

| Paso | Qué se necesita |
|---|---|
| 1. Cuenta y suscripción de Azure | Correo (Azure for Students) o tarjeta |
| 2. Hacer pública la imagen en GHCR | Cuenta de GitHub con acceso al repositorio |
| 5. Variables | Contraseña de Azure SQL, URL del front web, correo del administrador |
| 6. Firewall de Azure SQL | Permiso sobre el servidor `parkspotter` |
| 8. Dominio propio | Solo si lo quieren; acceso al DNS del dominio |
| 9. Cuenta admin | Registrarse con `ADMIN_EMAIL` |
| 10. Wompi, Gmail, Firebase | Opcional |
| 11. Despliegue automático | Opcional; secreto y variables en GitHub |
