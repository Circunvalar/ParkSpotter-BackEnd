# ParkSpotter — Backend

[![CI - Pruebas automatizadas](https://github.com/Circunvalar/ParkSpotter-BackEnd/actions/workflows/ci.yml/badge.svg)](https://github.com/Circunvalar/ParkSpotter-BackEnd/actions/workflows/ci.yml)

API REST de ParkSpotter (Universidad Central) para encontrar y gestionar parqueaderos: autenticación con JWT y roles (conductor, propietario y administrador), gestión de garajes y sus plazas, búsqueda con filtros, mapa y disponibilidad en tiempo real, reservas con pago en línea (Wompi), entrada y salida con código, calificaciones, notificaciones (bandeja, push y correo), verificación de garajes, panel de administración y reportes. Las mismas APIs las consumen los fronts web y Android.

**Stack:** Java 21 · Spring Boot 4 · Spring Security (JWT) · Spring Data JPA · Azure SQL (SQL Server) · JUnit 5 · JaCoCo · GitHub Actions.

## Ejecutar

```bash
./mvnw spring-boot:run
```
Por defecto escucha en `http://localhost:8080`. La base de datos es Azure SQL (`parkspotter.database.windows.net`, base `ParkSpotterDatabase`). Antes de arrancar hay que definir la variable de entorno `DB_PASSWORD` con la contraseña de la base; nunca se escribe en `application.properties`. Opcionalmente `DB_URL` y `DB_USERNAME` cambian el servidor y el usuario.

```bash
export DB_PASSWORD='la-contraseña'          # PowerShell: $env:DB_PASSWORD='la-contraseña'
./mvnw spring-boot:run
```
En IntelliJ: *Run → Edit Configurations → Environment variables* → `DB_PASSWORD=...`. Documentación interactiva en `http://localhost:8080/swagger-ui.html`.

Con Docker (la misma imagen que corre en Azure; la base sigue siendo Azure SQL):

```bash
cp .env.example .env   # completar los valores (DB_PASSWORD es obligatoria)
docker compose up -d --build   # API en http://localhost:8080
```

Todas las variables están explicadas en [`.env.example`](.env.example).

## Pruebas

```bash
./mvnw verify
```
Corre todas las pruebas y exige la cobertura mínima. También se ejecutan solas en cada push y pull request ([ver la automatización](docs/Automatizacion-de-Pruebas.md)).

## Documentación

- [Referencia completa de la API (todos los endpoints)](docs/API-Referencia.md)
- [Clientes listos para los fronts web (TypeScript) y Android (Kotlin)](clients/README.md)
- [Despliegue en Azure App Service (paso a paso, variables y costos)](docs/Despliegue-Azure.md)
- [Cómo se conectan el front web y la app móvil (URL, CORS, autenticación y requisitos)](docs/Conexion-Web-y-Movil.md)
- [API de búsqueda, mapa, plazas y tiempo real (Sprint 03)](docs/API-Sprint-03-Busqueda-Garajes.md)
- [Criterios de calidad: validaciones, errores, seguridad, rendimiento y cobertura](docs/Criterios-de-Calidad.md)
- [Automatización de pruebas (CI)](docs/Automatizacion-de-Pruebas.md)

## Flujo de trabajo

GitFlow: `master` (producción), `develop` (integración) y una rama por sprint (`Sprint-NN-Descripcion`) que se integra a `develop` con un pull request.
