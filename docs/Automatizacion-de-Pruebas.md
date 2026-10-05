# Automatización de pruebas (CI) — Backend ParkSpotter

Las pruebas se ejecutan solas con **GitHub Actions**, sin servidores propios ni licencias. Es gratis: en repositorios **públicos** los minutos son ilimitados, y si el repositorio se vuelve privado el plan Free incluye 2.000 minutos al mes (cada ejecución tarda unos 5 minutos).

Configuración: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml).

## Cuándo se ejecuta

- En cada **push** a `master`, `develop` o cualquier rama `Sprint-*`.
- En cada **pull request** hacia `master` o `develop`.
- A mano, desde la pestaña **Actions → CI - Pruebas automatizadas → Run workflow**.

Si se hace un push nuevo a la misma rama mientras corre, la ejecución anterior se cancela.

## Qué ejecuta

```
push / pull request
        │
        ▼
┌─────────────────────────────── job: pruebas (≈ 3 min) ───────────────────────────────┐
│ Java 21 + caché de Maven                                                              │
│ ./mvnw verify  →  326 pruebas (unitarias, H2, API, servidor HTTP real, rendimiento)   │
│               →  JaCoCo exige líneas ≥ 90 %, ramas ≥ 85 %, métodos ≥ 95 %             │
│ Resumen en la página de la ejecución + reportes descargables                          │
└───────────────────────────────────────────────────────────────────────────────────────┘
        │ (solo si todo pasó)
        ▼
┌──────────────────────── job: e2e (≈ 2 min) ────────────────────────┐
│ PostgreSQL 18 en un contenedor temporal                            │
│ Levanta la API real (el mismo jar del job anterior)                │
│ scripts/smoke-test.sh → 44 verificaciones de punta a punta         │
│ Si falla, guarda el log de la API                                  │
└────────────────────────────────────────────────────────────────────┘
```

Ningún job se conecta a la base de datos de producción: el job `e2e` usa su propia base efímera y un secreto JWT aleatorio por ejecución.

## Cómo ver los resultados

- **En el pull request:** aparecen los checks "Pruebas y cobertura" y "Punta a punta con PostgreSQL" en verde o rojo.
- **Pestaña Actions** del repositorio, abriendo la ejecución:
  - Arriba está el **resumen**: pruebas que pasaron o fallaron (con el mensaje de cada falla) y la cobertura por paquete.
  - Abajo, en **Artifacts**:
    - `reportes-pruebas`: reporte HTML de JaCoCo (`jacoco/index.html`) y reportes de Surefire.
    - `log-api-e2e`: log de la API, solo si falló la prueba de punta a punta.
- **Insignia** en el [README](../README.md): verde o roja según la última ejecución en `master`.

## Recomendado: exigir la CI antes de mergear

Lo debe hacer un administrador del repositorio, una sola vez:

1. **Settings → Branches → Add branch ruleset** (o *Add classic branch protection rule*).
2. Patrón de rama: `develop`. Repetir para `master`.
3. Activar **Require a pull request before merging**.
4. Activar **Require status checks to pass** y agregar:
   - `Pruebas y cobertura`
   - `Punta a punta con PostgreSQL`
5. Guardar.

Desde ese momento, GitHub no deja mergear un pull request con pruebas en rojo.

## Ejecutar lo mismo en tu PC

```bash
./mvnw verify
python scripts/ci-summary.py
```
El primer comando corre las pruebas y la cobertura (igual que el job `pruebas`); el segundo muestra el mismo resumen que la CI.

Para la prueba de punta a punta (igual que el job `e2e`) se necesita un PostgreSQL de pruebas. **Nunca uses el de producción**:

```bash
./mvnw -DskipTests package
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/parkspotter_test \
SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=tu_clave_local \
./scripts/e2e.sh
```

Contra una API que ya está corriendo (local o desplegada):

```bash
BASE_URL=https://tu-api.onrender.com ./scripts/smoke-test.sh
```

En Windows estos scripts funcionan en **Git Bash**.

## Si la CI falla

| Falla en | Qué revisar |
|---|---|
| "Compilar, probar y verificar cobertura" con pruebas en rojo | El resumen de la ejecución lista cada prueba fallida con su mensaje. Reproducir con `./mvnw verify`. |
| Mismo paso, pero dice `coverage checks have not been met` | Se agregó código sin pruebas. Ver qué falta en `jacoco/index.html` (artefacto `reportes-pruebas`). |
| "Levantar la API y ejecutar el smoke test" | La salida indica qué verificación falló. Descargar `log-api-e2e` para ver el error de la API. |

## Herramientas gratuitas opcionales para más adelante

| Herramienta | Para qué | Costo |
|---|---|---|
| **Dependabot** (de GitHub) | Abre PRs para actualizar dependencias con vulnerabilidades; la CI las prueba solas | Gratis |
| **SonarQube Cloud** | Análisis estático: bugs, código duplicado, vulnerabilidades, deuda técnica | Gratis para repos públicos |
| **Codecov** | Historial de cobertura y comentario automático en cada PR | Gratis para repos públicos |
| **Postman + Newman** | Colección de la API para los fronts, ejecutable desde la CI | Gratis |

Se pueden agregar después sin cambiar lo que ya existe. Los tres primeros requieren que un administrador del repositorio los active o cree una cuenta.
