# Despliegue en Oracle Cloud Always Free

> **Cambio de base de datos:** el backend ahora usa Azure SQL (`parkspotter.database.windows.net`) y `docker-compose.yml` ya no levanta un contenedor de PostgreSQL. Las partes de esta guía sobre `POSTGRES_*`, `pg_dump` y respaldos de PostgreSQL ya no aplican: en `.env` basta con `DB_PASSWORD` (ver [`.env.example`](../.env.example)). La guía de despliegue se actualizará con el nuevo plan.

El backend corre en una máquina virtual gratuita de Oracle con Docker: tres contenedores (API, PostgreSQL y Caddy para HTTPS) definidos en [`docker-compose.yml`](../docker-compose.yml). La máquina no se apaga por inactividad como otros planes gratis, y el HTTPS se renueva solo.

```
Internet ──443/80──▶ Caddy (HTTPS) ──▶ API Spring Boot :8080 ──▶ PostgreSQL :5432
                     └────────────── red interna de Docker ─────────────────┘
```

Los pasos marcados con **[manual]** necesitan que una persona entre a una consola web o tome una decisión (crear cuentas, poner la tarjeta, copiar claves). El resto son comandos para pegar en la terminal de la VM.

> Tiempo estimado: 45 a 60 minutos la primera vez.

---

## 1. Crear la cuenta de Oracle Cloud **[manual]**

1. Entrar a <https://www.oracle.com/cloud/free/> y elegir **Start for free**.
2. Llenar los datos. Oracle pide una **tarjeta de crédito o débito** solo para verificar identidad: hace un cobro temporal pequeño que devuelve. Los recursos *Always Free* no generan cobros.
3. **Región de origen (home region):** elegir una cercana, por ejemplo *Brazil East (São Paulo)* o *US East (Ashburn)*. No se puede cambiar después y las máquinas gratuitas solo se crean en esa región.
4. Esperar el correo de bienvenida (puede tardar unos minutos) y entrar a la consola: <https://cloud.oracle.com>.

## 2. Crear la máquina virtual **[manual]**

1. Menú ☰ → **Compute** → **Instances** → **Create instance**.
2. **Name:** `parkspotter`.
3. **Image and shape** → **Edit**:
   - **Image:** *Canonical Ubuntu 24.04*.
   - **Shape:** *Change shape* → **Ampere** → `VM.Standard.A1.Flex` con **2 OCPU y 12 GB** de memoria. Es gratis hasta 4 OCPU y 24 GB en total por cuenta, así queda espacio para otra máquina.
4. **Networking:** dejar *Create new virtual cloud network* y *Create new public subnet*. Verificar que esté marcado **Assign a public IPv4 address**.
5. **Add SSH keys:** elegir **Generate a key pair for me** y descargar la **clave privada** (`ssh-key-….key`). Guardarla bien: sin ella no se puede entrar a la máquina.
6. **Boot volume:** dejar el tamaño por defecto (unos 47 GB; el plan gratis permite hasta 200 GB en total).
7. **Create**. En uno o dos minutos el estado pasa a *Running*. Copiar la **Public IP address** (ejemplo: `129.146.10.20`).

> **Si aparece "Out of capacity for shape VM.Standard.A1.Flex":** Oracle no tiene máquinas Ampere libres en ese momento. Reintentar más tarde (de madrugada suele haber), probar otro *Availability domain* en el paso 4, o bajar a 1 OCPU y 6 GB. Convertir la cuenta a *Pay As You Go* da prioridad y los recursos Always Free siguen sin costo, pero exige vigilar no crear recursos de pago.

## 3. Abrir los puertos 80 y 443 en Oracle **[manual]**

Oracle bloquea todo el tráfico de entrada salvo SSH (22).

1. En la página de la instancia, en **Primary VNIC**, abrir el enlace de la **Subnet**.
2. **Security Lists** → *Default Security List for …* → **Add Ingress Rules**.
3. Agregar una regla con: **Source CIDR** `0.0.0.0/0`, **IP Protocol** `TCP`, **Destination Port Range** `80,443`.
4. **Add Ingress Rules**.

## 4. Entrar por SSH

Desde la computadora donde se descargó la clave (Linux, macOS o PowerShell de Windows):

```bash
chmod 400 ~/Descargas/ssh-key-*.key          # en Windows no hace falta
ssh -i ~/Descargas/ssh-key-*.key ubuntu@129.146.10.20
```

El usuario de las imágenes Ubuntu de Oracle es `ubuntu`. Todos los comandos siguientes se ejecutan dentro de la VM.

## 5. Preparar la VM

### 5.1 Firewall interno de Ubuntu

Las imágenes de Oracle traen reglas `iptables` que rechazan todo salvo SSH. Abrir 80 y 443 y guardar las reglas:

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

### 5.2 Instalar Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit
```

Volver a entrar por SSH (para que tome el grupo `docker`) y comprobar:

```bash
docker run --rm hello-world
docker compose version
```

### 5.3 Zona horaria y actualizaciones automáticas (opcional)

```bash
sudo timedatectl set-timezone America/Bogota
sudo apt-get install -y unattended-upgrades
```

## 6. Elegir el dominio para HTTPS

Caddy pide el certificado a Let's Encrypt, que necesita un nombre de dominio (no sirve la IP sola). Opciones gratis:

| Opción | Dominio de ejemplo | Qué hacer |
|---|---|---|
| **sslip.io** (la más rápida) | `129-146-10-20.sslip.io` | Nada: es la IP con guiones + `.sslip.io`. |
| **DuckDNS** | `parkspotter.duckdns.org` | **[manual]** Entrar a <https://www.duckdns.org> con GitHub o Google, crear el subdominio y poner la IP pública. |
| Dominio propio | `api.parkspotter.co` | **[manual]** En el proveedor del dominio, crear un registro `A` que apunte a la IP pública. |

Para empezar, sslip.io basta. Si después cambian a un dominio propio, solo se cambia `DOMAIN` en `.env` y se reinicia Caddy.

## 7. Descargar el código y configurar

```bash
git clone https://github.com/Circunvalar/ParkSpotter-BackEnd.git
cd ParkSpotter-BackEnd
git checkout develop        # o la rama que se vaya a desplegar
cp .env.example .env
```

> Si el repositorio es privado, `git clone` pide usuario y contraseña: usar el usuario de GitHub y un **token** (GitHub → Settings → Developer settings → Personal access tokens → *Fine-grained*, con permiso de lectura de *Contents* sobre este repositorio). **[manual]**

Generar los secretos:

```bash
echo "POSTGRES_PASSWORD=$(openssl rand -hex 24)"
echo "JWT_SECRET=$(openssl rand -hex 48)"
```

Editar `.env` con `nano .env` y completar como mínimo:

| Variable | Valor |
|---|---|
| `DOMAIN` | El dominio del paso 6, ej. `129-146-10-20.sslip.io` |
| `POSTGRES_PASSWORD` | El valor generado arriba |
| `JWT_SECRET` | El valor generado arriba |
| `ADMIN_EMAIL` | Correo de la persona administradora (ver paso 9) |
| `APP_CORS_ALLOWED_ORIGINS` | URL del front web, ej. `https://parkspotter-web.vercel.app,http://localhost:3000` |
| `PAYMENTS_REDIRECT_PREFIXES` | URL del front web con `/` final, más `parkspotter://` para la app |
| `PAYMENTS_DEFAULT_REDIRECT` | Página del front que muestra el resultado del pago |
| `RESET_PASSWORD_URL` | Página del front para restablecer la contraseña |

Guardar con `Ctrl+O`, `Enter`, y salir con `Ctrl+X`. Pagos, correo y push son opcionales y se configuran en el paso 10.

## 8. Levantar los contenedores

```bash
docker compose up -d --build
```

La primera vez tarda unos minutos (descarga las imágenes y compila el jar). Ver el estado y los logs:

```bash
docker compose ps                  # api y db deben quedar "healthy"
docker compose logs -f api         # Ctrl+C para salir
docker compose logs caddy | grep -i certificate
```

Probar desde cualquier navegador:

- `https://<DOMAIN>/swagger-ui.html` debe abrir la documentación interactiva.
- `https://<DOMAIN>/api/v1/garages/search` debe responder JSON.

También se puede correr la prueba de humo completa desde la VM:

```bash
BASE_URL=https://<DOMAIN> ./scripts/smoke-test.sh
```

## 9. Crear la cuenta administradora

1. **[manual]** Registrarse en la app (o en Swagger con `POST /api/v1/auth/register`) con el correo puesto en `ADMIN_EMAIL`.
2. Reiniciar la API para que le asigne el rol:

   ```bash
   docker compose restart api
   ```

3. Al iniciar sesión de nuevo, `roles` incluye `ROLE_ADMIN`. Desde ahí se pueden dar más roles con `PUT /api/v1/admin/users/{id}/roles`.

## 10. Servicios opcionales

Después de cambiar `.env`, aplicar con `docker compose up -d` (recrea solo lo que cambió).

### Pagos con Wompi (sandbox gratis) **[manual]**

1. Crear cuenta en <https://comercios.wompi.co> y entrar al ambiente de **pruebas**.
2. En **Desarrolladores**, copiar la llave pública (`pub_test_…`), el secreto de **integridad** y el secreto de **eventos**.
3. En la misma sección, configurar la **URL de eventos**: `https://<DOMAIN>/api/v1/payments/webhooks/wompi`.
4. En `.env`:

   ```bash
   PAYMENTS_PROVIDER=wompi
   WOMPI_PUBLIC_KEY=pub_test_...
   WOMPI_INTEGRITY_SECRET=test_integrity_...
   WOMPI_EVENTS_SECRET=test_events_...
   ```

Las tarjetas de prueba de Wompi están en su documentación (por ejemplo `4242 4242 4242 4242` aprobada). Para cobrar de verdad se cambian por las llaves de producción (`pub_prod_…`) cuando Wompi apruebe el comercio. Mientras `PAYMENTS_PROVIDER=sandbox`, los pagos son simulados.

### Correo (recuperación de contraseña y avisos) **[manual]**

Con Gmail: activar la verificación en dos pasos de la cuenta y crear una **contraseña de aplicación** en <https://myaccount.google.com/apppasswords>. Luego:

```bash
MAIL_ENABLED=true
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=cuenta@gmail.com
MAIL_PASSWORD=la-contraseña-de-aplicación
MAIL_FROM=ParkSpotter <cuenta@gmail.com>
```

Sin esto, los correos solo se escriben en el log (`docker compose logs api | grep Correo`).

> Oracle bloquea por defecto la salida por el puerto 25, pero el 587 (el de Gmail) funciona.

### Notificaciones push con Firebase **[manual]**

1. En <https://console.firebase.google.com>, crear el proyecto (el mismo que use la app Android).
2. **Configuración del proyecto** → **Cuentas de servicio** → **Generar nueva clave privada**. Se descarga un JSON.
3. Del JSON, copiar `project_id`, `client_email` y `private_key` a `.env`:

   ```bash
   FCM_ENABLED=true
   FCM_PROJECT_ID=parkspotter-xxxx
   FCM_CLIENT_EMAIL=firebase-adminsdk-xxx@parkspotter-xxxx.iam.gserviceaccount.com
   FCM_PRIVATE_KEY=-----BEGIN PRIVATE KEY-----\nMIIE...\n-----END PRIVATE KEY-----\n
   ```

   `FCM_PRIVATE_KEY` va en una sola línea, tal como aparece en el JSON (con los `\n`).

## 11. Copias de seguridad de la base de datos

Crear la carpeta y probar una copia:

```bash
mkdir -p ~/backups
cd ~/ParkSpotter-BackEnd && docker compose exec -T db pg_dump -U parkspotter parkspotter | gzip > ~/backups/parkspotter-$(date +%F).sql.gz
ls -lh ~/backups
```

Programarla todos los días a las 3 a. m. y guardar las últimas 14 (`crontab -e` y pegar al final):

```cron
0 3 * * * cd /home/ubuntu/ParkSpotter-BackEnd && docker compose exec -T db pg_dump -U parkspotter parkspotter | gzip > /home/ubuntu/backups/parkspotter-$(date +\%F).sql.gz && find /home/ubuntu/backups -name '*.sql.gz' -mtime +14 -delete
```

Restaurar una copia (borra los datos actuales):

```bash
gunzip -c ~/backups/parkspotter-2026-05-10.sql.gz | docker compose exec -T db psql -U parkspotter -d parkspotter
```

Las copias quedan en la misma VM. Para no perderlas si la VM se borra, descargarlas de vez en cuando con `scp -i <clave> ubuntu@<IP>:~/backups/* .`.

### Pasar los datos de la base actual de Render (opcional)

Si quieren conservar los usuarios y garajes que ya están en la base de Render:

```bash
docker run --rm postgres:18 pg_dump "postgresql://USUARIO:CLAVE@HOST_RENDER:5432/BASE?sslmode=require" | gzip > ~/backups/render.sql.gz
gunzip -c ~/backups/render.sql.gz | docker compose exec -T db psql -U parkspotter -d parkspotter
docker compose restart api
```

Hacerlo con la API recién levantada (base vacía). Al reiniciar, las cuentas antiguas reciben el rol de conductor o propietario automáticamente.

## 12. Actualizar a una versión nueva

```bash
cd ~/ParkSpotter-BackEnd
git pull
docker compose up -d --build
docker image prune -f
```

La API queda caída unos 30 segundos mientras arranca la versión nueva. El esquema de la base se actualiza solo (`ddl-auto=update`).

## 13. Comandos útiles

| Para | Comando |
|---|---|
| Ver estado | `docker compose ps` |
| Logs de la API | `docker compose logs -f --tail 200 api` |
| Reiniciar la API | `docker compose restart api` |
| Apagar todo | `docker compose down` (los datos se conservan) |
| Consola de la base | `docker compose exec db psql -U parkspotter -d parkspotter` |
| Uso de memoria y CPU | `docker stats --no-stream` |

## 14. Problemas comunes

| Síntoma | Causa probable | Solución |
|---|---|---|
| El navegador no abre `https://<DOMAIN>` (tiempo de espera) | Puertos cerrados | Revisar el paso 3 (Security List) y el 5.1 (iptables). |
| `docker compose logs caddy` dice que no pudo obtener el certificado | El dominio no apunta a la IP o el puerto 80 está cerrado | Verificar con `ping <DOMAIN>` que resuelva a la IP pública; revisar el paso 3. |
| `api` reinicia en bucle | Falta una variable o la clave de la base no coincide | `docker compose logs api`. Si se cambió `POSTGRES_PASSWORD` después del primer arranque, la base conserva la clave vieja: volver a ponerla. |
| El front web recibe error de CORS | Falta su dominio | Agregarlo a `APP_CORS_ALLOWED_ORIGINS` y `docker compose up -d`. |
| `redirectUrl no permitida` al pagar | Falta el prefijo | Agregar la URL del front a `PAYMENTS_REDIRECT_PREFIXES`. |
| Oracle avisa que la instancia está inactiva | Política de recuperación de instancias Always Free con uso muy bajo durante 7 días | Con tráfico real no suele pasar. Convertir la cuenta a *Pay As You Go* lo evita y lo gratuito sigue gratis. |

---

## Resumen de pasos manuales

| Paso | Quién lo hace | Qué se necesita |
|---|---|---|
| 1. Cuenta de Oracle | Responsable del proyecto | Correo, teléfono y tarjeta para verificar |
| 2. Crear la VM | Responsable del proyecto | Guardar la clave SSH y la IP pública |
| 3. Abrir puertos | Responsable del proyecto | Consola de Oracle |
| 6. Dominio | Solo si no usan sslip.io | Cuenta de DuckDNS o dominio propio |
| 7. Token de GitHub | Solo si el repositorio es privado | Cuenta de GitHub con acceso al repo |
| 9. Cuenta admin | Quien vaya a administrar | Registrarse con `ADMIN_EMAIL` |
| 10. Wompi, Gmail, Firebase | Opcional | Crear las cuentas y copiar las claves a `.env` |

Todo lo demás (pasos 4, 5, 7, 8, 11 y 12) son comandos que puede ejecutar cualquier persona con la clave SSH.

## Seguridad

- `.env` tiene los secretos: nunca subirlo al repositorio (ya está en `.gitignore`).
- La API y la base de datos no publican puertos: solo Caddy (80 y 443) es accesible desde Internet.
- `src/main/resources/application.properties` todavía trae los datos de conexión de la base de Render en texto plano. En Docker no se usan (las variables `SPRING_DATASOURCE_*` del compose los reemplazan), pero conviene **cambiar esa contraseña en Render** y quitarla del repositorio.
