# Criterios de calidad — Backend ParkSpotter

Criterios que debe cumplir todo cambio del backend (Sprint 01 a 03 en adelante), cómo se verifican y la evidencia de la última verificación.
Referencias: **ISO/IEC 25010** (calidad del producto) e **ISO/IEC 42010** (arquitectura).

## 1. Definición de terminado (DoD)

Un cambio está terminado cuando:

1. La **CI de GitHub Actions está en verde** (ver [Automatizacion-de-Pruebas.md](Automatizacion-de-Pruebas.md)): equivale a que `./mvnw verify` termine en **BUILD SUCCESS** y a que el smoke test pase contra PostgreSQL.
2. Todas las pruebas pasan y la cobertura cumple los mínimos que exige JaCoCo: **líneas ≥ 90 %, ramas ≥ 85 %, métodos ≥ 95 %**. El build falla si baja.
3. Todo método nuevo tiene al menos una prueba, y todo campo de entrada nuevo tiene validación (sección 3) con una prueba que la cubre.
4. Los errores siguen el contrato de la sección 4 (JSON `ApiError`, código HTTP correcto, mensaje en español).
5. Las consultas nuevas filtran en la base de datos (no en memoria) y tienen índice si filtran por columnas nuevas.
6. La documentación de la API para los fronts está actualizada (`docs/`).

## 2. Cómo verificar

La CI lo hace sola en cada push y pull request. Para verificarlo en tu PC:

```bash
./mvnw verify
```
Corre las pruebas (unitarias, integración con H2, API con MockMvc, servidor HTTP real y rendimiento) y el control de cobertura. Reporte de cobertura: `target/site/jacoco/index.html`. Resumen en consola: `python scripts/ci-summary.py`.

```bash
BASE_URL=http://localhost:8080 ./scripts/smoke-test.sh
```
Prueba de punta a punta contra la app corriendo (local, de un compañero o Render). Solo requiere bash y curl (Git Bash sirve).

## 3. Validación de entradas

Reglas aplicadas con Bean Validation en los DTOs; los mensajes salen siempre en español (`spring.web.locale=es`). Los textos se guardan sin espacios al inicio ni al final.

### Autenticación

| Campo | Regla |
|---|---|
| `email` (registro y login) | Obligatorio, máximo 254, formato `nombre@dominio.ext` (se aceptan espacios alrededor y se recortan; se guarda en minúsculas) |
| `password` (registro) / `newPassword` | Obligatoria, 12 a 72 caracteres, máximo 72 bytes UTF-8 (límite de BCrypt), al menos una letra y un número, sin espacios al inicio o al final |
| `password` (login) / `currentPassword` | Obligatoria, máximo 72 (en login no se exige la política, solo se compara) |
| `newPassword` vs `currentPassword` | Deben ser distintas |
| `refreshToken` | Obligatorio, máximo 200, solo `A-Z a-z 0-9 _ -` |
| Header `X-Client-Id` | 1 a 64 caracteres, solo `A-Z a-z 0-9 . _ -` (por defecto `browser`) |

### Garajes (`POST` y `PUT /api/v1/garages`)

En `PUT` todos los campos son opcionales, pero si llegan cumplen la misma regla y no pueden ir vacíos.

| Campo | Regla |
|---|---|
| `name` | Obligatorio, 3 a 150 |
| `description` | Opcional, máximo 1000 (en `PUT`, `""` la borra) |
| `phone` | Opcional, 7 a 20 caracteres: dígitos, espacios, `-`, `( )` y `+` inicial |
| `addressLine` | Obligatoria, 5 a 255 |
| `city`, `state`, `country` | Obligatorios, 2 a 100, solo letras (con tildes y ñ), espacios, `.`, `'`, `-` |
| `postalCode` | Opcional, 3 a 10 letras, números, espacios o guiones |
| `latitude` / `longitude` | Obligatorias, -90..90 / -180..180, y no ambas 0 (punto por defecto de los mapas) |
| `totalSpots` | Obligatorio, 1 a 5000 |
| `pricePerHour` | Obligatorio, 0 a 1.000.000, máximo 2 decimales |
| `open24Hours` | Obligatorio (`true`/`false`) |
| `openingTime`, `closingTime` | Obligatorios y distintos si `open24Hours=false` (se permite horario nocturno, ej. 18:00–06:00) |
| `availableSpots` (solo `PUT`) | Rechazado: se calcula a partir del estado de las plazas |

### Plazas (`/api/v1/garages/{id}/spots`)

| Campo | Regla |
|---|---|
| `code` | Opcional (se genera `P-001`…), 1 a 20, solo letras, números y guiones, único por garaje |
| `floor` | Opcional, -10 a 200 |
| `vehicleType` | `CAR`, `MOTORCYCLE`, `BICYCLE`, `TRUCK`, `ELECTRIC`, `DISABLED` |
| `status` (PATCH) | `AVAILABLE`, `OCCUPIED`, `RESERVED`, `OUT_OF_SERVICE` |

Reglas de negocio: no se elimina una plaza ocupada o reservada ni la última del garaje; al reducir `totalSpots` solo se quitan plazas libres o fuera de servicio.

### Búsqueda y mapa

| Parámetro | Regla |
|---|---|
| `q`, `city` | Máximo 100 |
| `minPrice`, `maxPrice` | 0 a 1.000.000, 2 decimales, `minPrice ≤ maxPrice` |
| `minAvailableSpots` | 1 a 5000 |
| `lat`, `lng` | Rangos geográficos; deben ir juntos |
| `radiusKm` | Mayor que 0 y hasta 50; requiere `lat`/`lng` |
| `sort=DISTANCE` | Requiere `lat`/`lng` |
| `page` / `size` | 0 a 10000 / 1 a 100 |
| `minLat`, `minLng`, `maxLat`, `maxLng` (mapa) | Obligatorios, rangos geográficos, `min ≤ max` |
| `limit` (mapa) | 1 a 500 |
| `/nearby?radiusKm` | Mayor que 0 y hasta 50 |

## 4. Contrato de errores

Toda respuesta de error es JSON, incluso si el cliente pidió otro formato (por ejemplo SSE):

```json
{ "timestamp": "…", "status": 400, "error": "Bad Request", "message": "Datos inválidos",
  "path": "/api/v1/garages", "details": { "name": "no debe estar vacío", "totalSpots": "debe ser mayor que o igual a 1" } }
```

| Código | Cuándo |
|---|---|
| 400 | Validación (con `details` por campo), JSON mal formado o con tipos incorrectos, parámetro faltante o inválido, regla de negocio |
| 401 | Sin token, token inválido o vencido, credenciales inválidas, refresh token revocado |
| 403 | El usuario no es dueño ni admin del recurso; origen CORS no permitido |
| 404 | Garaje, plaza o ruta inexistente |
| 405 / 415 | Método HTTP o `Content-Type` no soportado |
| 409 | Correo ya registrado; conflicto por cambios simultáneos |
| 429 | Demasiados intentos de login (bloqueo temporal) |
| 503 | Se alcanzó el máximo de conexiones en tiempo real (`app.garages.stream-max-subscribers`) |
| 500 | Error inesperado: mensaje genérico, sin detalles internos (el detalle queda en el log) |

## 5. Seguridad (ISO/IEC 25010 — Security)

- JWT de acceso de 15 min y refresh token opaco de 7 días, guardado como hash SHA-256 y rotado en cada uso.
- Contraseñas con BCrypt (costo 12) y política de la sección 3.
- Mismo mensaje y tiempo de respuesta para "correo inexistente", "contraseña incorrecta" y "usuario deshabilitado" (no se revela qué correos existen).
- Bloqueo de login tras 5 intentos fallidos en 10 min (429 durante 15 min).
- `logout` revoca el refresh token aunque el access token ya haya vencido.
- Token inválido en endpoint público: se atiende como anónimo; en endpoint protegido: 401.
- Endpoints públicos sin datos personales: el correo del dueño solo se devuelve al dueño (Ley 1581 de 2012).
- CORS solo para los orígenes de `app.cors.allowed-origins`.
- Headers de seguridad: HSTS, `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Cache-Control: no-store`.

## 6. Rendimiento y confiabilidad (ISO/IEC 25010 — Performance Efficiency, Reliability)

- Búsqueda, mapa y detalle responden en **< 3 s** con 1.000 garajes (prueba automática `GarageSearchPerformanceTest`).
- Filtros resueltos en SQL con índices (`status+city`, `status+latitude+longitude`, `status+price`, `status+available_spots`, plazas por `garage_id+status` y `garage_id+vehicle_type+status`), paginación en BD y límites de tamaño.
- `spring.jpa.open-in-view=false`: los streams SSE no retienen conexiones de la base de datos (prueba de regresión `manyOpenStreams_DoNotExhaustDatabaseConnections`).
- Cambios de estado de plazas con bloqueo de fila del garaje: contadores consistentes con operadores simultáneos.
- Eventos en tiempo real enviados solo después del commit.

## 7. Evidencia de la última verificación (Sprint 03)

**Pruebas automáticas:** 326 pruebas en 27 clases, 0 fallas.

| Cobertura | Resultado | Mínimo exigido |
|---|---|---|
| Métodos | **100 %** (290/290) | 95 % |
| Líneas | **99,8 %** (968/970) | 90 % |
| Ramas | **97,6 %** (369/378) | 85 % |
| Clases | **100 %** (68/68) | — |

Las 2 líneas sin cubrir son el `catch` de "SHA-256 no disponible" en `RefreshTokenService`: es inalcanzable, porque la especificación de Java obliga a que SHA-256 exista. Las ramas parciales son combinaciones de chequeos de nulos que ya validan otras capas.

**Tipos de prueba:**

| Tipo | Qué cubre | Clases |
|---|---|---|
| Unitarias (sin Spring) | Servicios, reglas de negocio, utilidades, configuración, manejador de errores | `*UnitTest`, `RateLimitServiceTest`, `RefreshTokenServiceTest`, `GeoUtilsTest`, `GarageAvailabilityRulesTest`, `ConfigTest`, `GlobalExceptionHandlerTest` |
| Validación campo por campo | Reglas de la sección 3 | `*ValidationTest`, `PasswordPolicyValidatorTest` |
| Integración (Spring + H2) | Consultas SQL, transacciones, filtros de búsqueda | `GarageServiceTest`, `GarageSearchServiceTest`, `AuthServiceTest` |
| API (MockMvc) | Endpoints, seguridad, permisos, contrato de errores, CORS | `*ControllerTest`, `GarageManagementApiTest`, `AuthFlowTest`, `ApiQualityCriteriaTest` |
| Servidor HTTP real | Desconexión y vencimiento de streams SSE | `GarageAvailabilityStreamIntegrationTest` |
| Rendimiento | Respuesta < 3 s con 1.000 garajes | `GarageSearchPerformanceTest` |
| Punta a punta (PostgreSQL) | Flujo completo contra la API desplegada | `scripts/smoke-test.sh` (44 verificaciones) |

**PostgreSQL 18.6 real**, clúster temporal y aislado:

1. Se levantó la versión del Sprint 02 y se crearon garajes con sus contadores, incluido uno con 12 plazas y 5 libres.
2. Se levantó la versión del Sprint 03 sobre esa misma base:
   - Las columnas nuevas se agregaron sin error.
   - Se crearon los 8 índices.
   - El garaje antiguo recibió exactamente 12 plazas: 7 ocupadas y 5 libres.
   - El dueño antiguo pudo operar sus plazas.
3. El smoke test dio **44/44 OK**, con tiempos de 0.03 s a 0.33 s.
4. Prueba de carga SSE con 22 streams abiertos (el pool es de 20):
   - **Antes de la corrección:** la API respondía 500 a los 30 s.
   - **Después:** 200 en 0.02 s.

**Defectos encontrados y corregidos en esta verificación:**

| Defecto | Impacto | Corrección |
|---|---|---|
| Cualquier `IllegalArgumentException` respondía 401 "Authentication failed" | Correo duplicado y errores internos se veían como "no autenticado" | Excepciones con su código (409, 401, 400, 429) |
| Streams SSE retenían conexiones de BD (open-in-view) | Con ~20 usuarios en el mapa la API dejaba de responder | `open-in-view=false` + prueba de regresión |
| `logout` exigía access token | Con el access token vencido, el refresh token quedaba vivo 7 días | `logout` público (basta el refresh token) |
| CORS no permitía `X-Client-Id` | El front web no podía enviar ese header en el login | Header permitido |
| `X-Client-Id` sin límite (columna de 64) | Error de BD al guardar la auditoría | Validación 1–64 caracteres |
| Endpoints públicos devolvían el correo del dueño | Exposición de datos personales | Correo solo para el dueño o admin |
| Usuario deshabilitado podía hacer login | Acceso indebido | Rechazo con mensaje genérico |
| `open24Hours` ausente daba error de parseo (Jackson 3) | Mensaje confuso para los fronts | Validación `@NotNull` con detalle del campo |
| `/nearby` sin límite de radio; `page` muy alta desbordaba | Consultas costosas / 500 | Límites y aritmética segura |
| Errores de Spring (404 de ruta, 405, 415, JSON inválido) sin formato `ApiError` | Fronts no podían mostrar el error | Manejador global completo |
| Stream SSE sin máximo de conexiones | Agotamiento del servidor | Tope configurable (503) |
| Spring creaba un usuario en memoria y mostraba su contraseña en el log | Ruido y mala práctica de seguridad | Bean `UserDetailsService` explícito |
| Test heredado levantaba el servidor en el puerto 8080 | Fallaba si la app ya estaba corriendo | Puerto aleatorio |
