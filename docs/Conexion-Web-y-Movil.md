# Cómo conectar el front web y la app móvil al backend

El backend desplegado en Azure es el único servidor de ParkSpotter: el front web y la app Android hablan con él por HTTPS y él es el único que habla con la base Azure SQL. Ningún cliente se conecta directo a la base ni guarda sus credenciales.

```
Front web (navegador) ──┐
                        ├── HTTPS ──▶ https://<API_HOST>/api/v1/... ──▶ Azure SQL
App Android ────────────┘
```

Cómo crear el servidor: [Despliegue-Azure.md](Despliegue-Azure.md). Lista completa de endpoints: [API-Referencia.md](API-Referencia.md).

## 1. URL base

| Entorno | URL base | Dónde se usa |
|---|---|---|
| Producción (Azure) | `https://<API_HOST>`, por ejemplo `https://parkspotter-api.azurewebsites.net` o el dominio propio `https://api.parkspotter.co` | Web y Android publicados |
| Local | `http://localhost:8080` | Front web en la misma computadora |
| Emulador Android | `http://10.0.2.2:8080` | El emulador ve la computadora en `10.0.2.2` |
| Celular físico en la misma red | `http://<IP-de-la-computadora>:8080` | Solo desarrollo |

`<API_HOST>` es el valor que da `az webapp show -g rg-parkspotter -n <app> --query defaultHostName -o tsv` (paso 5 de la guía de despliegue). Todas las rutas de la API empiezan por `/api/v1`. La documentación interactiva está en `<URL base>/swagger-ui.html` y el contrato OpenAPI en `<URL base>/v3/api-docs`.

La URL base nunca se escribe en el código: va en la configuración de cada cliente (sección 3 y 4), así cambiar de servidor o de dominio no requiere tocar código.

## 2. Requisitos de los clientes

| Requisito | Web | Android |
|---|---|---|
| Protocolo | **HTTPS obligatorio** en producción. Un front servido por HTTPS no puede llamar a una API HTTP (contenido mixto). | **HTTPS obligatorio** en producción: desde Android 9 el tráfico HTTP está bloqueado por defecto. `usesCleartextTraffic` solo en el build de debug. |
| CORS | El origen del front (esquema + dominio + puerto, sin `/` final) debe estar en `APP_CORS_ALLOWED_ORIGINS` del backend. | No aplica (CORS es solo de navegadores). |
| Permisos | — | `<uses-permission android:name="android.permission.INTERNET" />` |
| Versión mínima | Navegadores con `fetch` y `EventSource` (todos los actuales). | Android 7 (API 24) con OkHttp 4.12; `minSdk` 26 o *desugaring* para `java.time`. |
| Configuración | `VITE_API_URL` o `NEXT_PUBLIC_API_URL` | `buildConfigField("String", "API_URL", …)` terminada en `/` |
| Volver del pago | URL del front en `PAYMENTS_REDIRECT_PREFIXES` (con `/` final) | Deep link `parkspotter://` (ya permitido) |

El backend acepta peticiones de cualquier red: no hay que registrar la IP de los usuarios. Lo único que se registra en el servidor son los **orígenes del front web** (CORS) y los **prefijos de retorno del pago**.

## 3. Front web

1. Copiar el cliente [`clients/web/parkspotter-api.ts`](../clients/web/parkspotter-api.ts) en el proyecto (instrucciones en [clients/web](../clients/web/README.md)).
2. Configurar la URL base por entorno:

   ```bash
   # .env.development
   VITE_API_URL=http://localhost:8080
   # .env.production
   VITE_API_URL=https://parkspotter-api.azurewebsites.net
   ```

   (En Next.js: `NEXT_PUBLIC_API_URL`.)

3. Pedir a quien administra el backend que agregue el dominio del front en Azure:

   ```bash
   az webapp config appsettings set -g rg-parkspotter -n <app> --output none --settings \
     APP_CORS_ALLOWED_ORIGINS="https://parkspotter-web.ejemplo.com,http://localhost:3000" \
     PAYMENTS_REDIRECT_PREFIXES="https://parkspotter-web.ejemplo.com/,parkspotter://" \
     PAYMENTS_DEFAULT_REDIRECT="https://parkspotter-web.ejemplo.com/pagos/resultado" \
     RESET_PASSWORD_URL="https://parkspotter-web.ejemplo.com/restablecer-contrasena"
   ```

   Si el front está en Vercel, Netlify o Azure Static Web Apps, el origen es la URL que da ese servicio (por ejemplo `https://parkspotter-web.vercel.app`). Cada dominio de vista previa distinto también debe agregarse si se quiere probar contra producción.

**CORS en detalle.** El backend responde a los orígenes de la lista con:

- Métodos: `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
- Headers permitidos: `Authorization, Content-Type, Accept, X-Request-Id, X-Client-Id, Last-Event-ID, Cache-Control`.
- Sin cookies (`credentials` no se usa): la sesión viaja en el header `Authorization`, así que `fetch` no necesita `credentials: 'include'`.

Si el navegador muestra *blocked by CORS policy*, el origen no está en la lista o tiene una `/` al final.

## 4. App Android

1. Copiar los archivos de [`clients/android`](../clients/android/README.md) (Retrofit + OkHttp).
2. Definir la URL base por tipo de build en `app/build.gradle.kts` (debe terminar en `/`):

   ```kotlin
   android {
       buildFeatures { buildConfig = true }
       buildTypes {
           debug {
               buildConfigField("String", "API_URL", "\"http://10.0.2.2:8080/\"")
               manifestPlaceholders["cleartext"] = "true"
           }
           release {
               buildConfigField("String", "API_URL", "\"https://parkspotter-api.azurewebsites.net/\"")
               manifestPlaceholders["cleartext"] = "false"
           }
       }
   }
   ```

   Y en `AndroidManifest.xml`: `<application android:usesCleartextTraffic="${cleartext}" …>`.

3. Crear el cliente con `ParkSpotterClient(baseUrl = BuildConfig.API_URL, …)`. El cliente ya agrega el token, lo renueva ante un 401 y traduce los errores.

## 5. Autenticación y headers

| Header | Cuándo | Valor |
|---|---|---|
| `Authorization` | Toda ruta que requiere sesión | `Bearer <accessToken>` |
| `Content-Type` | Peticiones con cuerpo | `application/json` (subida de fotos: `multipart/form-data`, lo pone el navegador u OkHttp) |
| `Accept` | Recomendado | `application/json` (`text/event-stream` en el tiempo real) |
| `X-Client-Id` | Opcional, en el login | Identificador del dispositivo o navegador (letras, números, `.`, `-`, `_`; por defecto `browser`). Queda en la auditoría de inicios de sesión. |
| `X-Request-Id` | Opcional | Identificador propio para rastrear una petición; CORS lo permite. |

Flujo de sesión:

1. `POST /api/v1/auth/login` con `{ "email", "password" }` → `{ accessToken, refreshToken, roles, … }`.
2. Enviar `Authorization: Bearer <accessToken>` en cada llamada. El access token dura **15 minutos**.
3. Ante un **401**, llamar `POST /api/v1/auth/refresh` con `{ "refreshToken" }` (dura **7 días**) y repetir la petición. Los clientes de `clients/` ya lo hacen.
4. Al cerrar sesión: `POST /api/v1/auth/logout` con el refresh token.

Dónde guardar los tokens: en web, `localStorage` (valor por defecto del cliente) o memoria; en Android, `EncryptedSharedPreferences` (ejemplo en [clients/android](../clients/android/README.md)).

## 6. Tiempo real (disponibilidad de plazas)

La disponibilidad en vivo usa **Server-Sent Events** sobre la misma URL HTTPS (`GET /api/v1/garages/availability/stream` para todos los garajes o `GET /api/v1/garages/{id}/availability/stream` para uno; ver [API-Referencia.md](API-Referencia.md)). No requiere puertos ni configuración extra en Azure: el servidor manda un latido cada 25 segundos para que la conexión no se corte, y si se corta el cliente se reconecta solo. Cerrar el stream al salir de la pantalla.

## 7. Requisitos del servidor (referencia)

| Tema | Valor |
|---|---|
| Hosting | Azure App Service para contenedores (Linux), plan B1 o superior, *Always On*, 1 instancia |
| Imagen | `ghcr.io/circunvalar/parkspotter-backend` (Docker, `linux/amd64`), construida con el [`Dockerfile`](../Dockerfile) |
| Java | 21 (Eclipse Temurin, incluido en la imagen) |
| Spring Boot | 4 |
| Puerto del contenedor | `8080` (`WEBSITES_PORT=8080`). Hacia Internet solo 443 (HTTPS); el 80 redirige a 443. |
| Base de datos | Azure SQL `parkspotter.database.windows.net:1433`, base `ParkSpotterDatabase`, con el firewall abierto para la app |
| Variables obligatorias | `DB_PASSWORD`, `JWT_SECRET`, `WEBSITES_PORT`, `APP_BOOTSTRAP_ROLES=true`, `APP_CORS_ALLOWED_ORIGINS` |
| Variables según funciones | `ADMIN_EMAIL`, `PAYMENTS_*`, `WOMPI_*`, `RESET_PASSWORD_URL`, `MAIL_*`, `FCM_*` (tabla completa en la guía de despliegue) |
| Zona horaria | `America/Bogota` (en la imagen) |

Para desarrollar en local: Java 21 y la variable `DB_PASSWORD` (ver el [README](../README.md)), o Docker con `docker compose up -d --build`, que deja la API en `http://localhost:8080`.

## 8. Comprobar la conexión

```bash
curl -i https://<API_HOST>/api/v1/garages/search                      # 200 con JSON
curl -i -X OPTIONS https://<API_HOST>/api/v1/auth/login \
  -H "Origin: https://parkspotter-web.ejemplo.com" \
  -H "Access-Control-Request-Method: POST"                          # debe traer Access-Control-Allow-Origin
```

O la prueba completa: `BASE_URL=https://<API_HOST> ORIGIN=https://parkspotter-web.ejemplo.com ./scripts/smoke-test.sh`.
