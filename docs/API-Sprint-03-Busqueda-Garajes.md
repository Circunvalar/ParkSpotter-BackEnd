# Sprint 03 — Búsqueda, mapa y disponibilidad en tiempo real

Guía de la API para los fronts **web** y **Android**. Todas las rutas son `GET` públicas (no requieren token) salvo donde se indique.
Base: `/api/v1/garages`.

## Modelo: plazas (`ParkingSpot`)

Desde este sprint cada garaje tiene **plazas físicas**. Al crear un garaje se generan `totalSpots` plazas libres (`P-001`, `P-002`...), tipo `CAR`, piso 1.
Los contadores del garaje (`availableSpots`, `occupiedSpots`, `reservedSpots`, `outOfServiceSpots`) **se calculan desde las plazas**.

| Enum | Valores |
|---|---|
| `SpotStatus` | `AVAILABLE`, `OCCUPIED`, `RESERVED`, `OUT_OF_SERVICE` |
| `VehicleType` | `CAR`, `MOTORCYCLE`, `BICYCLE`, `TRUCK`, `ELECTRIC`, `DISABLED` |
| `GarageAvailability` (color del marcador) | `AVAILABLE`, `FEW_SPOTS` (≤ 20% libres), `FULL`, `CLOSED` (garaje inactivo/suspendido) |

> **Cambios respecto al Sprint 01/02 que afectan a los fronts:**
> - `PUT /api/v1/garages/{id}` ya no acepta `availableSpots` (400): la disponibilidad se cambia con el estado de las plazas. `totalSpots` sí se puede cambiar: agrega plazas libres o elimina libres/fuera de servicio (nunca ocupadas ni reservadas).
> - `POST /api/v1/garages` exige `open24Hours` (`true`/`false`) y valida todos los campos (reglas en [Criterios-de-Calidad.md](Criterios-de-Calidad.md#3-validación-de-entradas)).
> - Las consultas públicas (`GET /garages`, `/garages/{id}`, `/nearby`) devuelven `ownerEmail: null`; el correo solo llega al dueño (`/mine`, crear, editar).
> - Registro con correo repetido: **409** (antes 401). Login bloqueado por intentos: **429** (antes 401).
> - Contraseñas nuevas: 12 a 72 caracteres con al menos una letra y un número.
> - `POST /auth/logout` ya no requiere access token: basta el refresh token.

## 1. Búsqueda y listado — `GET /search`

Query params (todos opcionales):

| Param | Tipo | Descripción |
|---|---|---|
| `q` | texto | Busca en nombre, dirección y ciudad (sin distinguir mayúsculas) |
| `city` | texto | Ciudad exacta |
| `minPrice`, `maxPrice` | número | Rango de precio por hora |
| `vehicleType` | enum | Garajes con plazas de ese tipo |
| `onlyAvailable` | bool | Solo con plazas libres (del `vehicleType` si se envía) |
| `minAvailableSpots` | int ≥ 1 | Mínimo de plazas libres |
| `open24Hours` | bool | Solo 24 horas (o solo con horario, si `false`) |
| `openNow` | bool | Solo abiertos ahora (hora de Bogotá; soporta horarios nocturnos) |
| `lat`, `lng` | número | Ubicación del usuario. Van juntos |
| `radiusKm` | 0–50 | Radio. Por defecto 5 km si llegan `lat`/`lng` |
| `sort` | enum | `DISTANCE` (por defecto con ubicación), `NAME` (por defecto sin ubicación), `PRICE_ASC`, `PRICE_DESC`, `AVAILABILITY`, `NEWEST` |
| `page` | int ≥ 0 | Por defecto 0 |
| `size` | 1–100 | Por defecto 20 |

Ejemplo: `/api/v1/garages/search?lat=4.711&lng=-74.072&radiusKm=3&onlyAvailable=true&openNow=true`

```json
{
  "content": [
    {
      "id": "…", "name": "Garaje Centro", "addressLine": "Cra 7 # 12-30", "city": "Bogotá",
      "latitude": 4.710989, "longitude": -74.072092, "pricePerHour": 3500,
      "totalSpots": 50, "availableSpots": 49, "occupiedSpots": 1, "reservedSpots": 0,
      "availability": "AVAILABLE", "open24Hours": true, "openingTime": null, "closingTime": null,
      "openNow": true, "distanceKm": 0.0
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "first": true, "last": true
}
```

`distanceKm` solo viene cuando se envían `lat` y `lng`.

## 2. Mapa — `GET /map`

Marcadores livianos del **área visible** (para lazy loading: llamar al mover/hacer zoom).
Obligatorios: `minLat`, `minLng`, `maxLat`, `maxLng`. Opcional `limit` (1–500, por defecto 200).
Acepta los mismos filtros de `/search` (`onlyAvailable`, `vehicleType`, `maxPrice`, `openNow`...).

```json
{
  "markers": [
    { "id": "…", "name": "Garaje Centro", "latitude": 4.71, "longitude": -74.07,
      "pricePerHour": 3500, "availableSpots": 49, "totalSpots": 50,
      "availability": "AVAILABLE", "openNow": true }
  ],
  "count": 1,
  "truncated": false
}
```

Si `truncated` es `true` hay más garajes en el área: sugerir acercar el zoom. Se priorizan los de más plazas libres.

## 3. Detalle — `GET /{id}/detail?includeSpots=true`

Datos completos, horario, disponibilidad total y por tipo de vehículo, pisos y (opcional) todas las plazas para dibujar el parqueadero.

```json
{
  "id": "…", "name": "Parqueadero Chapinero", "description": "…", "phone": "…",
  "addressLine": "…", "city": "Bogotá", "state": "…", "country": "…", "postalCode": "…",
  "latitude": 4.6486, "longitude": -74.0628, "pricePerHour": 5000,
  "open24Hours": false, "openingTime": "06:00:00", "closingTime": "22:00:00",
  "availability": { "garageId": "…", "status": "ACTIVE", "availability": "AVAILABLE", "openNow": true,
                    "totalSpots": 21, "availableSpots": 20, "occupiedSpots": 0, "reservedSpots": 1,
                    "outOfServiceSpots": 0, "timestamp": "…" },
  "availabilityByVehicleType": [
    { "vehicleType": "CAR", "totalSpots": 20, "availableSpots": 19, "occupiedSpots": 0, "reservedSpots": 1, "outOfServiceSpots": 0 },
    { "vehicleType": "MOTORCYCLE", "totalSpots": 1, "availableSpots": 1, "occupiedSpots": 0, "reservedSpots": 0, "outOfServiceSpots": 0 }
  ],
  "floors": [1, 2],
  "spots": [ { "id": "…", "garageId": "…", "code": "P-001", "floor": 1, "vehicleType": "CAR", "status": "RESERVED", "updatedAt": "…" } ],
  "createdAt": "…", "updatedAt": "…"
}
```

## 4. Estado en tiempo real

- `GET /{id}/availability` → foto actual (mismo objeto que `detail.availability`).
- `GET /{id}/availability/stream` → **Server-Sent Events** de un garaje. El primer evento es el estado actual; luego llega uno por cada cambio.
- `GET /availability/stream` → SSE con los cambios de **todos** los garajes (para refrescar marcadores del mapa).

Cada evento se llama `availability` y su `data` es el JSON de disponibilidad. Cada 25 s llega un comentario `ping` para mantener viva la conexión.

Web:
```js
const es = new EventSource(`${API}/api/v1/garages/${id}/availability/stream`);
es.addEventListener('availability', (e) => render(JSON.parse(e.data)));
```

Android (OkHttp): `okhttp-sse` → `EventSources.createFactory(client).newEventSource(request, listener)` y filtrar `type == "availability"`.

Si la conexión se cae, `EventSource` reconecta solo; en Android, reintentar y volver a pedir `/availability`.

## 5. Plazas — `/{garageId}/spots`

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| GET | `/{garageId}/spots?status=&vehicleType=` | Pública | Listado (ordenado por piso y código) |
| POST | `/{garageId}/spots` | Dueño/Admin | Body `{"code":"M-01","floor":2,"vehicleType":"MOTORCYCLE"}` (todo opcional). 201 |
| PUT | `/{garageId}/spots/{spotId}` | Dueño/Admin | Cambia código, piso o tipo |
| PATCH | `/{garageId}/spots/{spotId}/status?status=OCCUPIED` | Dueño/Admin | Cambia el estado en tiempo real |
| DELETE | `/{garageId}/spots/{spotId}` | Dueño/Admin | No permite borrar ocupadas/reservadas ni la última plaza. 204 |

## Errores

Siempre JSON (`ApiError`): `{ "timestamp", "status", "error", "message", "path", "details" }`. En errores de validación, `details` trae un mensaje en español por cada campo inválido, para mostrarlo junto al campo del formulario.

| Código | Cuándo |
|---|---|
| 400 | Datos o parámetros inválidos (`size=500`, `vehicleType=AVION`, `lat` sin `lng`, límites de mapa invertidos, código de plaza repetido, JSON mal formado) |
| 401 | Falta token, token vencido o credenciales inválidas |
| 403 | El usuario no es dueño ni admin del garaje |
| 404 | Garaje, plaza o ruta inexistente |
| 409 | Correo ya registrado o conflicto por cambios simultáneos: reintentar |
| 429 | Demasiados intentos de login |
| 503 | Demasiadas conexiones de tiempo real abiertas: reintentar en unos segundos |

Lista completa en [Criterios-de-Calidad.md](Criterios-de-Calidad.md#4-contrato-de-errores).

## Notas técnicas (ISO/IEC 25010 / 42010)

- **Rendimiento:** filtros resueltos en SQL con índices (`status+city`, `status+latitude+longitude`, `status+price`, `status+available_spots`, `parking_spots(garage_id, status)` y `(garage_id, vehicle_type, status)`); paginación en BD; búsqueda por cercanía con pre-filtro por rectángulo indexado y distancia exacta (Haversine) solo sobre los candidatos. Prueba automática: con 1.000 garajes cada consulta responde en < 3 s.
- **Consistencia:** los contadores se recalculan desde las plazas y los cambios de estado bloquean la fila del garaje (`SELECT … FOR UPDATE`), así dos operadores simultáneos no los descuadran. Los eventos en tiempo real se envían solo tras el commit.
- **Compatibilidad:** mismo contrato JSON para web y Android; SSE es HTTP estándar (sin WebSocket ni librerías propietarias).
- **Arquitectura:** nuevo módulo `garageSearch` (lectura/consulta) separado de `garageManagement` (escritura), comunicados por el evento `GarageAvailabilityChangedEvent`.
- **Migración:** al arrancar, los garajes creados en el Sprint 02 (sin plazas) reciben sus plazas automáticamente respetando sus contadores (`app.garages.backfill-spots`).
