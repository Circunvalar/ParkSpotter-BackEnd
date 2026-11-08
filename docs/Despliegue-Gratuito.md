# Despliegue gratuito: Render + Neon

Sin Azure, el backend se despliega **gratis y sin tarjeta** con dos servicios:

- **Render** (plan *Free*): corre el contenedor Docker de este repositorio y da el HTTPS y la URL `https://<nombre>.onrender.com`.
- **Neon** (plan *Free*): la base de datos PostgreSQL. Reemplaza a Azure SQL.

```
Web (Vercel/Netlify) ──┐                         ┌────────────────────────────┐
                       ├─ HTTPS ─▶ Render (TLS) ─▶│ Contenedor Spring Boot     │── TLS ──▶ Neon (PostgreSQL)
App Android ───────────┘                         │ (Dockerfile del repo)      │
                                                 └────────────────────────────┘
```

Cómo se conectan el front web y la app: [Conexion-Web-y-Movil.md](Conexion-Web-y-Movil.md).

> Tiempo estimado: 20 a 30 minutos la primera vez. Todo se hace desde el navegador; no hace falta instalar nada.

---

## 0. Por qué Render + Neon (y no otros)

| Opción | ¿Gratis de verdad? | Para este backend |
|---|---|---|
| **Render Free + Neon Free** — recomendado | Sí, sin tarjeta | Despliega el `Dockerfile` directo desde GitHub, HTTPS automático, se configura con el [`render.yaml`](../render.yaml) del repositorio. Se duerme tras 15 min sin tráfico (ver abajo). |
| Google Cloud Run (capa gratuita) | Sí, pero **exige activar facturación con tarjeta** | Arranca más rápido y escala a cero. La CPU se congela entre peticiones, así que las tareas programadas (vencer reservas sin pago, recordatorios) no corren solas sin pagar *min-instances*. Buena opción si tienen tarjeta. |
| Koyeb (instancia gratuita) | Sí | Muy parecido a Render (contenedor pequeño que se duerme). Alternativa si Render falla. |
| Fly.io | **No**: ya no tiene plan gratis para cuentas nuevas (pago por uso con prueba) | Funciona, pero cuesta unos pocos dólares al mes. |
| Oracle Cloud *Always Free* | Sí, pero pide tarjeta y la aprobación de la cuenta suele fallar | Una VM ARM potente, pero hay que administrar el servidor (Docker, HTTPS, actualizaciones). |
| **Vercel** | — | **No sirve para este backend.** Vercel es para fronts y funciones *serverless* de corta duración; no corre un contenedor Spring Boot que esté siempre escuchando, ni el tiempo real (SSE) ni las tareas programadas. **Sí es ideal para el front web** (React/Next/Vite), que se conecta a la API en Render. |

**Base de datos.** Azure SQL ya no está, así que la base pasa a **PostgreSQL en Neon**: plan gratis sin vencimiento, 1 GB por proyecto, se suspende tras 5 min sin uso y despierta sola en la siguiente consulta. No se usa la base gratuita de Render porque **se borra a los 30 días**. Supabase también sirve (500 MB, pero pausa el proyecto tras una semana sin uso). El backend crea las tablas solo al arrancar (`ddl-auto=update`): no hay que correr scripts. Los datos de Azure SQL no se migran; la base nueva empieza vacía.

### Límites del plan gratuito (y qué hacer)

| Límite | Efecto | Qué hacer |
|---|---|---|
| Render duerme el servicio tras **15 min sin peticiones**; despertar tarda ~1 min o más (Spring Boot con CPU limitada) | La primera petición después de un rato tarda; mientras duerme no corren las tareas programadas (se ponen al día al despertar) | Monitor gratis que llame a `/api/v1/health` cada 10 min (paso 7). Las 750 h/mes gratis alcanzan para tenerlo despierto todo el mes (un solo servicio). En el front, mostrar "Conectando con el servidor…" si la primera llamada tarda. |
| **512 MB de RAM**, CPU compartida | Arranque lento | Ya configurado: `JAVA_OPTS` con Serial GC y heap al 65 %. Probado: la app usa ~340 MB. |
| Render **bloquea el correo SMTP** (puertos 25, 465 y 587) en el plan gratis | Gmail por el 587 no funciona | Usar Brevo (gratis, 300 correos/día) por el puerto **2525** (paso 8). Sin correo, la app funciona igual y los correos quedan en el log. |
| Ancho de banda de salida del plan gratuito (pocos GB al mes); sin método de pago, al pasarse Render suspende los servicios gratis hasta el mes siguiente | Las fotos de garajes son lo más pesado | Fotos pequeñas (el backend acepta hasta 5 MB; recomendar < 500 KB en los fronts). Ya se envían con caché de 30 días. |
| Neon: 1 GB de almacenamiento | Las fotos se guardan en la base | Suficiente para el proyecto (cientos de fotos comprimidas). |
| Un solo contenedor | — | Es lo correcto: el tiempo real (SSE), las tareas y los límites de login viven en memoria y necesitan **una sola instancia**. |

Los límites de estos planes cambian; revisar <https://render.com/docs/free> y <https://neon.com/docs/introduction/plans> antes de presentar.

---

## 1. Crear la base en Neon **[manual]**

1. Entrar a <https://neon.com> → **Sign up** con la cuenta de GitHub.
2. **Create project**: nombre `parkspotter`, Postgres 16 o 17, región **AWS US East (N. Virginia)** (la más cercana a la región de Render que usamos).
3. En **Connect** (o *Connection details*), elegir la rama `main`, la base `neondb` y desmarcar **Connection pooling** (conexión directa). Copiar:
   - **Host**: algo como `ep-xxxx-123456.us-east-1.aws.neon.tech`
   - **User**: algo como `neondb_owner`
   - **Password**
4. Armar la URL JDBC (formato que entiende Java):

   ```
   jdbc:postgresql://<host>/neondb?sslmode=require
   ```

   Ojo: Neon muestra la URL como `postgresql://usuario:clave@host/neondb`. Para Java se escribe como arriba, con `jdbc:` delante y **sin** usuario ni clave en la URL (van en variables aparte).

## 2. Crear el servicio en Render **[manual]**

1. Entrar a <https://render.com> → **Get Started** con la cuenta de GitHub y autorizar el acceso al repositorio `ParkSpotter-BackEnd`.
2. **New → Blueprint** → elegir el repositorio y la rama `master`. Render lee el [`render.yaml`](../render.yaml) y propone el servicio `parkspotter-api` (Docker, plan Free, región Virginia, health check `/api/v1/health`).
3. Render pide los valores marcados como secretos. Completar:

| Variable | Valor |
|---|---|
| `DB_URL` | La URL JDBC del paso 1, ej. `jdbc:postgresql://ep-xxxx.us-east-1.aws.neon.tech/neondb?sslmode=require` |
| `DB_USERNAME` | El usuario de Neon (ej. `neondb_owner`) |
| `DB_PASSWORD` | La contraseña de Neon |
| `ADMIN_EMAIL` | Correo de la cuenta que será administradora |
| `APP_CORS_ALLOWED_ORIGINS` | URL del front web, separadas por coma, sin `/` final. Se aceptan comodines: `https://parkspotter-web.vercel.app,https://*.vercel.app,http://localhost:3000` |
| `PAYMENTS_REDIRECT_PREFIXES` | `https://parkspotter-web.vercel.app/,parkspotter://` |
| `PAYMENTS_DEFAULT_REDIRECT` | `https://parkspotter-web.vercel.app/pagos/resultado` |
| `RESET_PASSWORD_URL` | `https://parkspotter-web.vercel.app/restablecer-contrasena` |

   `JWT_SECRET` lo genera Render solo (valor aleatorio), y `APP_BOOTSTRAP_ROLES`, `PAYMENTS_PROVIDER=sandbox`, `DB_POOL_SIZE` y `JAVA_OPTS` ya vienen en el blueprint. Si todavía no tienen la URL del front, pongan `http://localhost:3000` y cámbienla después en **Environment**.

4. **Apply**. El primer despliegue compila la imagen (5 a 10 minutos). Al terminar, la URL queda arriba a la izquierda: `https://parkspotter-api.onrender.com` (o con un sufijo si el nombre ya existe). Esa es la **URL base** para la web y la app.

> Sin blueprint: **New → Web Service** → repositorio → *Language: Docker* → *Instance type: Free* → región *Virginia* → *Health Check Path* `/api/v1/health` → agregar las variables de la tabla más `JWT_SECRET` (generar con `openssl rand -hex 48`) y `APP_BOOTSTRAP_ROLES=true`.

## 3. Verificar

- `https://<URL>/api/v1/health` → `{"status":"UP","database":"UP",…}`. Si dice `DOWN`, revisar `DB_URL`, usuario y contraseña.
- `https://<URL>/swagger-ui.html` → documentación interactiva.
- Logs: en Render, el servicio → **Logs**. Esperar `Started BackendParkSpotterApplication`.

Prueba de humo completa (crea datos de prueba con nombres únicos; necesita `PAYMENTS_PROVIDER=sandbox`; `ORIGIN` debe estar en `APP_CORS_ALLOWED_ORIGINS`):

```bash
BASE_URL=https://parkspotter-api.onrender.com ORIGIN=http://localhost:3000 MAX_SECONDS=5 ./scripts/smoke-test.sh
```

## 4. Crear la cuenta administradora

1. Registrarse en la app o en Swagger (`POST /api/v1/auth/register`) con el correo puesto en `ADMIN_EMAIL`.
2. Reiniciar el servicio para que le asigne el rol: Render → servicio → **Manual Deploy → Restart service**.
3. Al iniciar sesión de nuevo, `roles` incluye `ROLE_ADMIN`.

## 5. Desplegar el front web (Vercel) **[manual]**

1. En <https://vercel.com>, importar el repositorio del front.
2. En **Settings → Environment Variables**: `VITE_API_URL=https://parkspotter-api.onrender.com` (en Next.js `NEXT_PUBLIC_API_URL`).
3. Desplegar y copiar la URL que da Vercel (ej. `https://parkspotter-web.vercel.app`) a `APP_CORS_ALLOWED_ORIGINS`, `PAYMENTS_REDIRECT_PREFIXES`, `PAYMENTS_DEFAULT_REDIRECT` y `RESET_PASSWORD_URL` en Render (paso 2). Render se reinicia solo al guardar.

Netlify o Cloudflare Pages funcionan igual. La app Android solo necesita la URL base (ver [Conexion-Web-y-Movil.md](Conexion-Web-y-Movil.md#4-app-android)).

## 6. Despliegues automáticos

Con el blueprint, Render despliega cada vez que entra un cambio a `master` **y la CI de GitHub terminó bien** (`autoDeployTrigger: checksPass`). Si una prueba falla, no se despliega.

El workflow [`deploy.yml`](../.github/workflows/deploy.yml) además publica la imagen en GitHub Container Registry (`ghcr.io/circunvalar/parkspotter-backend`), útil para Cloud Run o Koyeb. Si prefieren desplegar en Render solo desde GitHub Actions: en Render, **Settings → Deploy Hook**, copiar la URL a un secreto del repositorio llamado `RENDER_DEPLOY_HOOK_URL` y poner Auto-Deploy en *Off*.

## 7. Mantenerlo despierto (recomendado) **[manual]**

1. Crear una cuenta gratis en <https://uptimerobot.com> (o <https://cron-job.org>).
2. **New monitor** → HTTP(s) → URL `https://parkspotter-api.onrender.com/api/v1/health` → intervalo **10 minutos**.

Así el servicio no se duerme, las tareas programadas corren a tiempo y además reciben un correo si la API se cae. Con un solo servicio, las 750 horas gratis del mes alcanzan.

## 8. Servicios opcionales

Se agregan en Render → servicio → **Environment**.

### Pagos con Wompi (sandbox gratis) **[manual]**

1. Crear la cuenta en <https://comercios.wompi.co> y entrar al ambiente de **pruebas**.
2. En **Desarrolladores**, copiar la llave pública (`pub_test_…`), el secreto de **integridad** y el secreto de **eventos**.
3. **URL de eventos**: `https://<URL>/api/v1/payments/webhooks/wompi`.
4. Variables: `PAYMENTS_PROVIDER=wompi`, `WOMPI_PUBLIC_KEY`, `WOMPI_INTEGRITY_SECRET`, `WOMPI_EVENTS_SECRET`.

Mientras sea `sandbox`, los pagos se simulan con `POST /api/v1/payments/{id}/sandbox-result` (ideal para la demo).

### Correo (recuperación de contraseña y avisos) **[manual]**

Render Free bloquea los puertos 25, 465 y 587, así que **Gmail no funciona** desde el plan gratis. Usar **Brevo** (gratis, 300 correos al día) por el puerto 2525:

1. Cuenta en <https://www.brevo.com> → **SMTP & API** → generar una clave SMTP y verificar el remitente.
2. Variables: `MAIL_ENABLED=true`, `MAIL_HOST=smtp-relay.brevo.com`, `MAIL_PORT=2525`, `MAIL_USERNAME` (el login SMTP de Brevo), `MAIL_PASSWORD` (la clave SMTP), `MAIL_FROM="ParkSpotter <correo-verificado@dominio>"`.

Sin correo configurado, el resto funciona y el token de recuperación queda en el log.

### Notificaciones push con Firebase **[manual]**

1. En <https://console.firebase.google.com>, el proyecto de la app → **Configuración → Cuentas de servicio → Generar nueva clave privada**.
2. Del JSON copiar `project_id`, `client_email` y `private_key` a `FCM_PROJECT_ID`, `FCM_CLIENT_EMAIL` y `FCM_PRIVATE_KEY` (en una línea, con los `\n`), y `FCM_ENABLED=true`.

Sin Firebase, las notificaciones igual quedan en la bandeja de la app.

### Soporte

`SUPPORT_EMAIL`, `SUPPORT_PHONE` y `SUPPORT_HOURS`: los canales que muestra la sección de ayuda (`GET /api/v1/support/contact`).

## 9. Todas las variables

| Variable | ¿Obligatoria? | Para qué |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Sí | Base PostgreSQL (Neon). |
| `JWT_SECRET` | Sí | Firma de los tokens (mínimo 32 caracteres; Render la genera). |
| `APP_BOOTSTRAP_ROLES` | Sí (`true`) | Crea los roles al arrancar. |
| `APP_CORS_ALLOWED_ORIGINS` | Sí, si hay front web | Orígenes permitidos (acepta `https://*.vercel.app`). |
| `ADMIN_EMAIL` | Recomendada | Cuenta que recibe `ROLE_ADMIN` al arrancar. |
| `PAYMENTS_PROVIDER`, `PAYMENTS_REDIRECT_PREFIXES`, `PAYMENTS_DEFAULT_REDIRECT`, `WOMPI_*` | Según pagos | Ver paso 8. |
| `RESET_PASSWORD_URL`, `MAIL_*` | Según correo | Ver paso 8. |
| `FCM_*` | No | Push. |
| `SUPPORT_*` | No | Contacto de soporte. |
| `DB_POOL_SIZE` | No (5 en Render) | Conexiones a la base. |
| `JAVA_OPTS` | No (ya en el blueprint) | Memoria de la JVM para 512 MB. |
| `PORT` | No | Lo pone Render; la app lo respeta. |

## 10. Problemas comunes

| Síntoma | Causa probable | Solución |
|---|---|---|
| El despliegue falla en *health check* | La base no responde o las credenciales están mal | Logs del servicio; revisar `DB_URL` (con `jdbc:` y `?sslmode=require`), usuario y contraseña. |
| `FATAL: password authentication failed` | Contraseña o usuario de Neon | Copiarlos de nuevo desde **Connect** en Neon (se puede resetear la contraseña). |
| La primera petición tarda casi un minuto | El servicio estaba dormido | Monitor del paso 7. |
| `Out of memory` en los logs | Falta `JAVA_OPTS` | Verificar que esté la variable del blueprint. |
| El front recibe error de CORS | Falta su origen | Agregarlo a `APP_CORS_ALLOWED_ORIGINS`, sin `/` final. |
| `redirectUrl no permitida` al pagar | Falta el prefijo | Agregar la URL del front con `/` final a `PAYMENTS_REDIRECT_PREFIXES`. |
| No llegan correos | SMTP bloqueado en Render Free | Brevo por el puerto 2525 (paso 8). |

## Desarrollo local (sin ninguna cuenta)

```bash
cp .env.example .env          # poner cualquier DB_PASSWORD
docker compose up -d --build  # PostgreSQL + API en http://localhost:8080
```

O con Java 21 y un PostgreSQL propio: definir `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` y correr `./mvnw spring-boot:run`.

## Seguridad

- Los secretos (`DB_PASSWORD`, `JWT_SECRET`, `WOMPI_*`, `MAIL_PASSWORD`, `FCM_PRIVATE_KEY`) solo viven en el panel de Render. Nunca en `application.properties`, en el `Dockerfile`, en `render.yaml` ni en commits.
- Render redirige HTTP a HTTPS y gestiona el certificado; la conexión a Neon va cifrada (`sslmode=require`).
- El contenedor corre con un usuario sin privilegios.

## Resumen de pasos manuales

| Paso | Qué se necesita |
|---|---|
| 1. Neon | Cuenta de GitHub |
| 2. Render | Cuenta de GitHub con acceso al repositorio; datos de Neon; URL del front |
| 4. Cuenta admin | Registrarse con `ADMIN_EMAIL` y reiniciar |
| 5. Front en Vercel | Repositorio del front |
| 7. Monitor | Cuenta de UptimeRobot (opcional, recomendado) |
| 8. Wompi, Brevo, Firebase | Opcional |
