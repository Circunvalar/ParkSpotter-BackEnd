# ParkSpotter — Backend

[![CI - Pruebas automatizadas](https://github.com/Circunvalar/ParkSpotter-BackEnd/actions/workflows/ci.yml/badge.svg)](https://github.com/Circunvalar/ParkSpotter-BackEnd/actions/workflows/ci.yml)

API REST de ParkSpotter (Universidad Central) para encontrar y gestionar parqueaderos: autenticación con JWT, gestión de garajes y sus plazas, búsqueda con filtros, mapa y disponibilidad en tiempo real. Las mismas APIs las consumen los fronts web y Android.

**Stack:** Java 21 · Spring Boot 4 · Spring Security (JWT) · Spring Data JPA · PostgreSQL · JUnit 5 · JaCoCo · GitHub Actions.

## Ejecutar

```bash
./mvnw spring-boot:run
```
Por defecto escucha en `http://localhost:8080`. La base de datos se configura con `spring.datasource.*`; se puede sobrescribir con las variables `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`.

## Pruebas

```bash
./mvnw verify
```
Corre todas las pruebas y exige la cobertura mínima. También se ejecutan solas en cada push y pull request ([ver la automatización](docs/Automatizacion-de-Pruebas.md)).

## Documentación

- [API de búsqueda, mapa, plazas y tiempo real (Sprint 03)](docs/API-Sprint-03-Busqueda-Garajes.md)
- [Criterios de calidad: validaciones, errores, seguridad, rendimiento y cobertura](docs/Criterios-de-Calidad.md)
- [Automatización de pruebas (CI)](docs/Automatizacion-de-Pruebas.md)

## Flujo de trabajo

GitFlow: `master` (producción), `develop` (integración) y una rama por sprint (`Sprint-NN-Descripcion`) que se integra a `develop` con un pull request.
