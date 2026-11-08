# ParkSpotter — Backend

[![CI - Pruebas automatizadas](https://github.com/Circunvalar/ParkSpotter-BackEnd/actions/workflows/ci.yml/badge.svg)](https://github.com/Circunvalar/ParkSpotter-BackEnd/actions/workflows/ci.yml)

API REST de ParkSpotter (Universidad Central) para encontrar y gestionar parqueaderos: autenticación con JWT y roles (conductor, propietario y administrador), gestión de garajes y sus plazas, búsqueda con filtros, mapa y disponibilidad en tiempo real, reservas con pago en línea (Wompi), entrada y salida con código, calificaciones (con seguridad percibida), notificaciones (bandeja, push y correo), verificación de garajes, soporte (preguntas frecuentes y casos), panel de administración y reportes para conductor, propietario y administrador. Las mismas APIs las consumen los fronts web y Android.

**Stack:** Java 21 · Spring Boot 4 · Spring Security (JWT) · Spring Data JPA · PostgreSQL (Neon) · JUnit 5 · JaCoCo · GitHub Actions · Render.

## Ejecutar

Lo más simple es Docker, que levanta PostgreSQL y la API juntos (no hace falta ninguna cuenta):

```bash
cp .env.example .env           # poner cualquier DB_PASSWORD
docker compose up -d --build   # API en http://localhost:8080
```

Con Java 21 y un PostgreSQL propio (o solo la base de Docker: `docker compose up -d db`):

```bash
cp .env.example .env           # DB_PASSWORD y, si no es la base local, DB_URL y DB_USERNAME
./mvnw spring-boot:run
```

Por defecto la app usa `jdbc:postgresql://localhost:5432/parkspotter` con el usuario `parkspotter`; `DB_PASSWORD` es obligatoria y nunca va en `application.properties`. También sirve definirla en la terminal (`export DB_PASSWORD=...`, en PowerShell `$env:DB_PASSWORD='...'`) o en IntelliJ (*Run → Edit Configurations → Environment variables*). Si `DB_URL` empieza por `jdbc:sqlserver://`, funciona igual con SQL Server.

Documentación interactiva en `http://localhost:8080/swagger-ui.html` y estado en `http://localhost:8080/api/v1/health`. Todas las variables están explicadas en [`.env.example`](.env.example).

## Desplegar gratis

Render (servidor, plan Free) + Neon (PostgreSQL, plan Free), sin tarjeta. El repositorio trae el [`render.yaml`](render.yaml) listo: paso a paso en [docs/Despliegue-Gratuito.md](docs/Despliegue-Gratuito.md). Vercel sirve para el front web, no para este backend (la guía explica por qué).

## Pruebas

```bash
./mvnw verify
```
Corre todas las pruebas y exige la cobertura mínima. También se ejecutan solas en cada push y pull request ([ver la automatización](docs/Automatizacion-de-Pruebas.md)).

## Documentación

- [Referencia completa de la API (todos los endpoints)](docs/API-Referencia.md)
- [Clientes listos para los fronts web (TypeScript) y Android (Kotlin)](clients/README.md)
- [Despliegue gratuito en Render + Neon (paso a paso, variables y límites)](docs/Despliegue-Gratuito.md)
- [Estado del proyecto frente a lo documentado (requisitos, sprints y endpoints)](docs/Estado-del-Proyecto.md)
- [Cómo se conectan el front web y la app móvil (URL, CORS, autenticación y requisitos)](docs/Conexion-Web-y-Movil.md)
- [API de búsqueda, mapa, plazas y tiempo real (Sprint 03)](docs/API-Sprint-03-Busqueda-Garajes.md)
- [Criterios de calidad: validaciones, errores, seguridad, rendimiento y cobertura](docs/Criterios-de-Calidad.md)
- [Automatización de pruebas (CI)](docs/Automatizacion-de-Pruebas.md)

## Flujo de trabajo

GitFlow: `master` (producción), `develop` (integración) y una rama por sprint (`Sprint-NN-Descripcion`) que se integra a `develop` con un pull request.
