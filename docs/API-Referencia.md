# Referencia de la API de ParkSpotter (Sprints 01 a 06)

Guía para quien implemente los fronts **web** y **Android**. Cada endpoint indica método, ruta, autenticación, cuerpo, respuesta y errores.

- **Base:** `https://<tu-servidor>` (local: `http://localhost:8080`; emulador Android: `http://10.0.2.2:8080`).
- **Swagger UI** (probar en el navegador): `/swagger-ui.html`. Contrato OpenAPI en JSON: `/v3/api-docs`.
- **Clientes listos para copiar:** [clients/web](../clients/web/README.md) (TypeScript) y [clients/android](../clients/android/README.md) (Kotlin + Retrofit).
- La búsqueda, el mapa y el tiempo real están explicados con más detalle en [API-Sprint-03-Busqueda-Garajes.md](API-Sprint-03-Busqueda-Garajes.md).

## Índice

1. [Convenciones](#1-convenciones)
2. [Roles y permisos](#2-roles-y-permisos)
3. [Autenticación](#3-autenticación)
4. [Perfil y cuenta](#4-perfil-y-cuenta)
5. [Garajes y plazas (propietario)](#5-garajes-y-plazas-propietario)
6. [Fotos y verificación de garajes](#6-fotos-y-verificación-de-garajes)
7. [Búsqueda, mapa y tiempo real (público)](#7-búsqueda-mapa-y-tiempo-real-público)
8. [Reservas](#8-reservas)
9. [Pagos y comprobante](#9-pagos-y-comprobante)
10. [Reseñas](#10-reseñas)
11. [Notificaciones](#11-notificaciones)
12. [Reportes](#12-reportes)
13. [Administración](#13-administración)
14. [Flujos completos](#14-flujos-completos)
15. [Enums](#15-enums)

---

## 1. Convenciones

| Tema | Regla |
|---|---|
| Formato | JSON (`Content-Type: application/json`), salvo la subida de fotos (`multipart/form-data`). |
| Autenticación | Header `Authorization: Bearer <accessToken>`. El access token dura 15 min; se renueva con el refresh token. |
| Fechas con hora | ISO-8601 en UTC: `"2026-05-10T14:00:00Z"`. Para mostrarlas, convertir a la hora local (Bogotá, UTC-5). |
| Fechas sin hora | `"2026-05-10"`. Se interpretan en hora de Bogotá. |
| Horas de apertura | `"07:00"` o `"07:00:00"`. |
| Dinero | Pesos colombianos (`COP`), número con hasta 2 decimales. |
| Ids | UUID en texto. |
| Paginación | Parámetros `page` (desde 0) y `size`. Respuesta `Page`: `{ content: [], page, size, totalElements, totalPages, first, last }`. |

### Errores

Todos los errores tienen la misma forma:

```json
{
  "timestamp": "2026-05-10T14:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "La validación falló",
  "path": "/api/v1/reservations",
  "details": { "vehiclePlate": "debe ser una placa válida" }
}
```

`message` está en español y se puede mostrar al usuario. `details` trae el error de cada campo en las validaciones.

| Código | Cuándo |
|---|---|
| 400 | Datos inválidos o regla de negocio incumplida (ver `message`). |
| 401 | Sin token, token vencido o credenciales incorrectas. Con token vencido: renovar y repetir. |
| 403 | Autenticado pero sin permiso (rol que no corresponde, recurso de otro usuario). |
| 404 | El recurso no existe. |
| 409 | Conflicto con el estado actual (ya existe, ya está pagada, sin plazas, modificado al mismo tiempo por otra persona). |
| 413 | Archivo de más de 5 MB. |
| 429 | Demasiados intentos (login o recuperación de contraseña). Esperar unos minutos. |

---

## 2. Roles y permisos

Una cuenta tiene siempre `ROLE_USER` y además uno o varios de estos roles:

| Rol | Quién | Puede |
|---|---|---|
| `ROLE_DRIVER` | Conductor | Reservar, pagar, cancelar sus reservas, calificar. |
| `ROLE_OWNER` | Propietario | Crear y administrar garajes, ver reservas de sus garajes, registrar entradas y salidas, ver su reporte. |
| `ROLE_ADMIN` | Administrador | Todo lo anterior, verificar garajes, gestionar usuarios y ver el reporte general. |

- Al registrarse se elige `DRIVER` (por defecto) u `OWNER`. Después se puede activar el otro con `POST /api/v1/users/me/roles`.
- Los roles llegan en `roles` del login y de `/users/me`. Úsenlos para mostrar u ocultar menús. El backend vuelve a comprobarlos en cada llamada.
- El primer administrador se configura con la variable `ADMIN_EMAIL` (ver la guía de despliegue).

---

## 3. Autenticación

Base: `/api/v1/auth`. Todos son públicos salvo `/me` y `/password` (PATCH).

### POST `/register`

Crea la cuenta y devuelve la sesión (201).

```json
{ "email": "ana@correo.com", "password": "ClaveSegura123", "role": "DRIVER", "fullName": "Ana Pérez", "phone": "3001234567" }
```

- `role`: `DRIVER` (por defecto) u `OWNER`. `fullName` y `phone` son opcionales.
- Contraseña: 12 a 72 caracteres, con al menos una letra y un número.

**Respuesta `TokenPair`:**

```json
{
  "userId": "…", "email": "ana@correo.com",
  "accessToken": "eyJ…", "refreshToken": "q3Jk…", "tokenType": "Bearer", "expiresInSeconds": 900,
  "roles": ["ROLE_DRIVER", "ROLE_USER"]
}
```

Errores: 400 validación, 409 correo ya registrado.

### POST `/login`

`{ "email": "…", "password": "…" }` → 200 `TokenPair`. Errores: 401 credenciales incorrectas o cuenta deshabilitada (mismo mensaje, para no revelar el estado de la cuenta), 429 demasiados intentos.

### POST `/refresh`

`{ "refreshToken": "…" }` → 200 `TokenPair` nuevo. El refresh token anterior deja de servir (rotación): guarden siempre el último. 401 si venció o ya se usó → enviar al login.

### POST `/logout`

`{ "refreshToken": "…" }` → 204. No necesita access token.

### GET `/me`

Usuario actual (`User`, ver 4). Igual que `GET /api/v1/users/me`.

### PATCH `/password`

Con sesión. `{ "currentPassword": "…", "newPassword": "…" }` → 204. 400 si la actual no coincide.

### POST `/password/forgot`

`{ "email": "…" }` → **202 siempre**, exista o no el correo (así no se revela quién tiene cuenta). Si la cuenta existe, llega un correo con un enlace `RESET_PASSWORD_URL?token=…` y el mismo token para pegarlo en la app. Vence en 30 min. 429 tras 5 solicitudes seguidas del mismo correo.

### POST `/password/reset`

`{ "token": "…", "newPassword": "…" }` → 204. El token es de un solo uso y cierra todas las sesiones abiertas. 400 si el token no es válido, ya se usó o venció.

---

## 4. Perfil y cuenta

Base: `/api/v1/users/me`. Requiere sesión.

**`User`:** `{ id, email, fullName, phone, roles: [], enabled, createdAt }`

| Método y ruta | Cuerpo | Respuesta | Errores |
|---|---|---|---|
| `GET /api/v1/users/me` | — | 200 `User` | 401 |
| `PATCH /api/v1/users/me` | `{ "fullName": "Ana María", "phone": "" }` | 200 `User` | 400 formato |
| `POST /api/v1/users/me/roles` | `{ "role": "OWNER" }` | 200 `User` con el rol nuevo | 400 |
| `DELETE /api/v1/users/me` | `{ "password": "…" }` | 204 | 400 contraseña, 409 reservas activas |
| `POST /api/v1/users/me/delete` | `{ "password": "…" }` | 204 | igual (para clientes que no envían cuerpo en DELETE) |

- En `PATCH`, un campo `null` o ausente no cambia; un texto vacío `""` borra el dato.
- Eliminar la cuenta: no se permite con reservas activas (como conductor o en sus garajes). Se anonimizan los datos, se desactivan sus garajes y se cierran las sesiones. El correo queda libre para registrarse otra vez.

---

## 5. Garajes y plazas (propietario)

Base: `/api/v1/garages`.

**`Garage`:** `{ id, name, description, phone, ownerId, ownerEmail, addressLine, city, state, country, postalCode, latitude, longitude, totalSpots, availableSpots, occupiedSpots, reservedSpots, outOfServiceSpots, pricePerHour, open24Hours, openingTime, closingTime, status, availability, verificationStatus, verified, averageRating, ratingCount, createdAt, updatedAt }`

`ownerEmail` solo llega al dueño o a un administrador; en las consultas públicas es `null`.

| Método y ruta | Auth | Descripción | Respuesta | Errores |
|---|---|---|---|---|
| `POST /api/v1/garages` | OWNER o ADMIN | Crear garaje (genera `totalSpots` plazas `P-001`…) | 201 `Garage` | 400, 403 si es solo conductor |
| `GET /api/v1/garages?city=` | Público | Garajes activos | 200 `Garage[]` | — |
| `GET /api/v1/garages/nearby?lat=&lng=&radiusKm=5` | Público | Cercanos (radio hasta 50 km) | 200 `Garage[]` | 400 |
| `GET /api/v1/garages/mine` | Sesión | Mis garajes | 200 `Garage[]` | — |
| `GET /api/v1/garages/{id}` | Público | Un garaje | 200 `Garage` | 404 |
| `PUT /api/v1/garages/{id}` | Dueño o ADMIN | Editar (campos opcionales; `totalSpots` agrega o quita plazas libres) | 200 `Garage` | 400, 403, 404 |
| `PATCH /api/v1/garages/{id}/status?status=INACTIVE` | Dueño o ADMIN | Activar, desactivar o suspender | 200 `Garage` | 403, 404 |
| `DELETE /api/v1/garages/{id}` | Dueño o ADMIN | Borrar | 204 | 403, 404, **409 si tiene reservas activas** |

**Cuerpo de crear (`GarageRequest`):**

```json
{
  "name": "Garaje Centro", "description": "Cubierto", "phone": "3001234567",
  "addressLine": "Cra 7 # 12-30", "city": "Bogotá", "state": "Cundinamarca", "country": "Colombia", "postalCode": "110111",
  "latitude": 4.711, "longitude": -74.072, "totalSpots": 20, "pricePerHour": 4000,
  "open24Hours": false, "openingTime": "06:00", "closingTime": "22:00"
}
```

Con `open24Hours: true`, `openingTime` y `closingTime` van en `null`. Se aceptan horarios nocturnos (`22:00` a `06:00`).

### Plazas: `/api/v1/garages/{garageId}/spots`

**`ParkingSpot`:** `{ id, garageId, code, floor, vehicleType, status, updatedAt }`

| Método y ruta | Auth | Cuerpo o params | Respuesta | Errores |
|---|---|---|---|---|
| `GET /spots?status=&vehicleType=` | Público | filtros opcionales | 200 `ParkingSpot[]` | 404 |
| `POST /spots` | Dueño o ADMIN | `{ "code": "M-01", "floor": 1, "vehicleType": "MOTORCYCLE" }` | 201 `ParkingSpot` | 400 (incluye código repetido), 403, 404 |
| `PUT /spots/{spotId}` | Dueño o ADMIN | igual que POST | 200 | 400, 403, 404 |
| `PATCH /spots/{spotId}/status?status=OUT_OF_SERVICE` | Dueño o ADMIN | — | 200 | 400, 403, 404 |
| `DELETE /spots/{spotId}` | Dueño o ADMIN | — | 204 | **400 si está ocupada, reservada o con reservas activas**, 403, 404 |

---

## 6. Fotos y verificación de garajes

| Método y ruta | Auth | Descripción | Respuesta | Errores |
|---|---|---|---|---|
| `GET /api/v1/garages/{id}/photos` | Público | Lista de fotos | 200 `GaragePhoto[]` | 404 |
| `GET /api/v1/garages/{id}/photos/{photoId}` | Público | Bytes de la imagen (cache 30 días) | 200 `image/*` | 404 |
| `POST /api/v1/garages/{id}/photos` | Dueño o ADMIN | `multipart/form-data`, campo `file` | 201 `GaragePhoto` | 400 formato o límite, 403, 404, 413 |
| `DELETE /api/v1/garages/{id}/photos/{photoId}` | Dueño o ADMIN | Borrar | 204 | 403, 404 |
| `POST /api/v1/garages/{id}/verification-request` | Dueño o ADMIN | Pedir la insignia de verificado | 200 `Garage` (`verificationStatus: PENDING`) | 400 sin fotos, 403, 409 ya pendiente o verificado |

**`GaragePhoto`:** `{ id, garageId, contentType, sizeBytes, url, createdAt }`. `url` es relativa (`/api/v1/garages/…/photos/…`): concatenarla con la base para usarla en `<img>` o en Coil o Glide.

Reglas: JPG, PNG o WEBP (se revisa el contenido real del archivo), máximo 5 MB, máximo 8 fotos por garaje.

Flujo de verificación: `UNVERIFIED` → (dueño pide) `PENDING` → (admin decide) `VERIFIED` o `REJECTED`. Tras un rechazo el dueño puede volver a pedirla. El dueño recibe una notificación y un correo con la decisión.

---

## 7. Búsqueda, mapa y tiempo real (público)

Todos son `GET` públicos. Detalle completo en [API-Sprint-03-Busqueda-Garajes.md](API-Sprint-03-Busqueda-Garajes.md); aquí solo lo nuevo de los Sprints 04 a 06.

| Ruta | Respuesta |
|---|---|
| `/api/v1/garages/search` | `Page<GarageSummary>` |
| `/api/v1/garages/map?minLat=&minLng=&maxLat=&maxLng=` | `{ markers: GarageMarker[], count, truncated }` |
| `/api/v1/garages/{id}/detail?includeSpots=false` | `GarageDetail` |
| `/api/v1/garages/{id}/availability` | `Availability` |
| `/api/v1/garages/{id}/availability/stream` | SSE de un garaje (evento `availability`) |
| `/api/v1/garages/availability/stream` | SSE de todos los garajes (para el mapa) |

**Filtros nuevos en `/search` y `/map`:**

| Param | Descripción |
|---|---|
| `minRating` | 1 a 5: solo garajes con calificación promedio mayor o igual. |
| `verifiedOnly` | `true`: solo garajes verificados. |
| `sort=RATING` | Mejor calificados primero. |

**Campos nuevos:** `GarageSummary`, `GarageMarker` y `GarageDetail` traen `verified`, `averageRating` y `ratingCount` (el marcador no trae `ratingCount`). `GarageDetail` trae además `verificationStatus` y `photos`.

---

## 8. Reservas

### Reglas

| Regla | Valor (configurable) |
|---|---|
| Duración | Mínimo 30 min, máximo 24 h. |
| Anticipación | Hasta 30 días. Puede empezar "ahora" (hasta 5 min en el pasado). |
| Horario | El garaje debe estar activo y abierto durante toda la franja. |
| Precio | Se cobra en bloques de 30 min: `ceil(minutos / 30) × precioHora / 2`. Ej.: 70 min a 4.000/h = 3 bloques = 6.000. |
| Plaza | Se asigna una libre del tipo de vehículo (o la que se pida con `spotId`). Dos reservas no se cruzan en la misma plaza. |
| Solapamiento | Un conductor no puede tener dos reservas activas que se crucen. |
| Pago | La reserva nace `PENDING_PAYMENT` y hay 15 min para pagar (`paymentDeadline`). Si no, pasa a `EXPIRED` y libera la plaza. |
| Plaza apartada | 30 min antes del inicio, la plaza de una reserva confirmada aparece como `RESERVED` en la disponibilidad. |
| Entrada | Desde 30 min antes del inicio hasta el fin. Solo reservas pagadas. |
| Salida | Si se pasa del fin, se calcula `overtimeMinutes` y `overtimeAmount` (mismo precio por bloques, se cobra en el garaje). |
| No presentado | Si una reserva confirmada termina sin entrada, pasa a `NO_SHOW`. |
| Cancelar | Solo `PENDING_PAYMENT` o `CONFIRMED`. Si ya estaba pagada, el pago queda `REFUND_PENDING` (el reembolso se hace manualmente desde el panel de Wompi). |

### Estados

```
PENDING_PAYMENT ──pago aprobado──▶ CONFIRMED ──entrada──▶ CHECKED_IN ──salida──▶ COMPLETED
      │                               │
      ├─ vence sin pago ─▶ EXPIRED    ├─ termina sin entrada ─▶ NO_SHOW
      └─ cancelar ───────▶ CANCELLED ◀┘ cancelar
```

**`Reservation`:** `{ id, code, status, garageId, garageName, garageAddress, garageLatitude, garageLongitude, spotId, spotCode, spotFloor, driverId, driverName, vehicleType, vehiclePlate, startTime, endTime, durationMinutes, pricePerHour, totalAmount, overtimeMinutes, overtimeAmount, currency, paymentDeadline, paid, paidAt, checkInAt, checkOutAt, cancelledAt, cancellationReason, cancellable, createdAt }`

`code` (ej. `PS-7K3MQ2`) es lo que el conductor muestra al llegar (como texto o QR). `cancellable` indica si mostrar el botón de cancelar.

### GET `/api/v1/garages/{garageId}/reservation-quote` (público)

Params: `vehicleType`, `startTime`, `endTime`. Cotiza sin reservar.

```json
{
  "garageId": "…", "vehicleType": "CAR", "startTime": "2026-05-10T14:00:00Z", "endTime": "2026-05-10T15:10:00Z",
  "durationMinutes": 70, "pricePerHour": 4000, "totalAmount": 6000, "currency": "COP",
  "availableSpots": 3, "available": true, "unavailableReason": null
}
```

Si la franja no se puede reservar (fuera de horario, muy corta, en el pasado, sin plazas), responde 200 con `available: false` y el motivo en `unavailableReason`, listo para mostrar. 400 si faltan parámetros o tienen mal formato, 404 si el garaje no existe.

### POST `/api/v1/reservations` (DRIVER o ADMIN)

```json
{
  "garageId": "…", "vehicleType": "CAR", "vehiclePlate": "ABC123",
  "startTime": "2026-05-10T14:00:00Z", "endTime": "2026-05-10T16:00:00Z",
  "spotId": null, "searchStartedAt": "2026-05-10T13:52:10Z"
}
```

- `vehiclePlate`: se guarda en mayúsculas y sin espacios.
- `spotId` (opcional): una plaza concreta.
- `searchStartedAt` (opcional): cuándo empezó a buscar el usuario. Alimenta la métrica "tiempo de búsqueda a reserva" del reporte del administrador.

201 `Reservation` con `status: PENDING_PAYMENT`. Errores: 400 regla incumplida (ver `message`), 403 si no es conductor, 404 garaje, 409 sin plazas o reserva cruzada.

### Consultas

| Método y ruta | Auth | Params | Respuesta |
|---|---|---|---|
| `GET /api/v1/reservations/mine` | Sesión | `status`, `page`, `size` (20) | `Page<Reservation>` (historial del conductor, más recientes primero) |
| `GET /api/v1/reservations/owned` | OWNER o ADMIN | `date` (`2026-05-10`), `garageId`, `status`, `page`, `size` (50) | `Page<Reservation>` (panel del propietario, ordenado por inicio) |
| `GET /api/v1/reservations/{id}` | Conductor, dueño del garaje o ADMIN | — | `Reservation` (403 si no es suya, 404) |

### POST `/api/v1/reservations/{id}/cancel`

Conductor, dueño del garaje o ADMIN. Cuerpo opcional `{ "reason": "Cambio de planes" }` (máx. 300). 200 `Reservation` cancelada. 403, 404, 409 si el estado no permite cancelar.

### POST `/api/v1/reservations/check-in` y `/check-out` (OWNER o ADMIN)

`{ "code": "PS-7K3MQ2" }`. El código se acepta con o sin `PS-` y en minúsculas. Solo el dueño de ese garaje (o un admin).

- Entrada: 200 `Reservation` `CHECKED_IN`. 409 si no está pagada, si es muy temprano o si la franja terminó.
- Salida: 200 `Reservation` `COMPLETED` con `overtimeMinutes` y `overtimeAmount`. 409 si no tiene entrada.
- 403 si la reserva es de otro garaje, 404 si el código no existe.

---

## 9. Pagos y comprobante

La pasarela es **Wompi** (Colombia). En desarrollo se usa una pasarela **simulada** (`PAYMENTS_PROVIDER=sandbox`) que no cobra nada.

**`Payment`:** `{ id, reservationId, reference, amount, amountInCents, currency, status, provider, paymentMethod, checkoutUrl, redirectUrl, publicKey, integritySignature, createdAt, approvedAt }`

| Método y ruta | Auth | Descripción | Respuesta | Errores |
|---|---|---|---|---|
| `POST /api/v1/reservations/{id}/payments` | Conductor de la reserva | Crea el pago (o reutiliza el pendiente) | 201 `Payment` | 400 `redirectUrl` no permitida, 403, 404, 409 no pendiente o vencida |
| `GET /api/v1/reservations/{id}/payments` | Conductor, dueño o ADMIN | Pagos de la reserva | 200 `Payment[]` | 403, 404 |
| `GET /api/v1/payments/{paymentId}` | Conductor, dueño o ADMIN | Un pago | 200 `Payment` | 403, 404 |
| `POST /api/v1/payments/{paymentId}/sandbox-result` | Conductor | Solo pasarela simulada: `{ "status": "APPROVED" }` (o `DECLINED`, `ERROR`) | 200 `Payment` | 400 si el pago es de Wompi, 403 |
| `GET /api/v1/reservations/{id}/receipt` | Conductor, dueño o ADMIN | Comprobante | 200 `Receipt` | 403, 404, 409 sin pago aprobado |
| `POST /api/v1/payments/webhooks/wompi` | Público (firma) | Lo llama Wompi, no los fronts | 200 | 401 firma inválida |

**Cuerpo para crear el pago (opcional):** `{ "redirectUrl": "https://mi-web.com/pagos/resultado" }`. Debe empezar por uno de los prefijos de `PAYMENTS_REDIRECT_PREFIXES` (por defecto `http://localhost:3000/` y `parkspotter://`). En Android usen un deep link, ej. `parkspotter://pagos/resultado`.

**Cómo pagar desde el front:**

1. `POST /reservations/{id}/payments` → recibir `checkoutUrl`.
2. Web: `window.location.href = checkoutUrl`. Android: abrirlo con Custom Tabs.
3. Wompi cobra y vuelve a `redirectUrl?id=<transacción>`. Al mismo tiempo avisa al backend por el webhook, y el backend confirma la reserva.
4. Al volver, consultar `GET /reservations/{id}` cada 2 o 3 segundos (máximo un minuto) hasta ver `CONFIRMED`. Si llega `PAYMENT_DECLINED` en notificaciones, ofrecer reintentar (otro `POST /payments`).

Con la pasarela simulada, `checkoutUrl` es la misma `redirectUrl` con `?reference=…&sandbox=true`; el front llama a `/sandbox-result` para aprobar o rechazar.

`publicKey` e `integritySignature` sirven si prefieren el widget de Wompi embebido en lugar de redirigir.

**`Receipt`:** `{ receiptNumber, issuedAt, reservationId, reservationCode, reservationStatus, customerName, customerEmail, garageName, garageAddress, spotCode, vehicleType, vehiclePlate, startTime, endTime, durationMinutes, pricePerHour, amountPaid, currency, paymentProvider, paymentMethod, paymentReference, providerTransactionId, paidAt, paymentStatus }`. Pensado para mostrar o imprimir; el PDF se puede generar en el front (o con "Imprimir → Guardar como PDF").

**Estados del pago:** `PENDING` → `APPROVED`, `DECLINED`, `VOIDED` o `ERROR`. `REFUND_PENDING`: el pago se aprobó pero la reserva se canceló o venció antes; hay que devolver el dinero desde el panel de Wompi.

---

## 10. Reseñas

**`Review`:** `{ id, garageId, reservationId, rating, comment, authorName, createdAt }`. `authorName` es solo el primer nombre (o "Usuario").

| Método y ruta | Auth | Cuerpo | Respuesta | Errores |
|---|---|---|---|---|
| `POST /api/v1/reservations/{id}/review` | Conductor de la reserva | `{ "rating": 5, "comment": "Muy bien" }` | 201 `Review` | 400 (rating 1 a 5, comentario máx. 500), 403, 404, 409 si no está `COMPLETED` o ya se calificó |
| `GET /api/v1/garages/{id}/reviews?page=0&size=10` | Público | — | 200 `Page<Review>` | 404 |
| `DELETE /api/v1/reviews/{reviewId}` | Autor o ADMIN | — | 204 | 403, 404 |

El promedio del garaje (`averageRating`, `ratingCount`) se actualiza solo al crear o borrar.

---

## 11. Notificaciones

Base: `/api/v1/notifications`. Requiere sesión.

**`Notification`:** `{ id, type, title, body, referenceId, read, readAt, createdAt }`. `referenceId` es el id de la reserva (tipos `RESERVATION_*`, `CHECK_*`, `PAYMENT_*`) o del garaje (`NEW_REVIEW`, `GARAGE_*`): úsenlo para abrir la pantalla correspondiente al tocar la notificación.

| Método y ruta | Descripción | Respuesta |
|---|---|---|
| `GET /?unreadOnly=false&page=0&size=20` | Bandeja, más recientes primero | `Page<Notification>` |
| `GET /unread-count` | Para el globo del ícono | `{ "unread": 3 }` |
| `PATCH /{id}/read` | Marcar una como leída | `Notification` (404 si no es suya) |
| `POST /read-all` | Marcar todas | 204 |
| `POST /devices` | Registrar el token push: `{ "token": "…", "platform": "ANDROID" }` (`ANDROID`, `IOS`, `WEB`) | 204 |
| `DELETE /devices?token=…` | Quitar el token (al cerrar sesión) | 204 |

**Push:** Firebase Cloud Messaging. El push trae `notification.title`, `notification.body` y en `data` los campos `type` y `referenceId`. Si el backend no tiene credenciales de Firebase, las notificaciones igual quedan en la bandeja.

**Cuándo se generan:** al conductor: reserva creada, confirmada (pago aprobado), cancelada (por correo si la canceló el garaje), vencida, no presentada, recordatorio 30 min antes del inicio, entrada, salida y pago rechazado. Al dueño: nueva reserva pagada, reserva cancelada, nueva reseña y garaje verificado o rechazado (también por correo). `ACCOUNT` queda reservado para avisos de cuenta.

---

## 12. Reportes

Fechas `from` y `to` opcionales (`2026-05-01`). Sin fechas: últimos 30 días hasta hoy. Rango máximo 366 días. Fechas en hora de Bogotá; se cuentan las reservas cuya franja empieza en el rango.

### GET `/api/v1/reports/owner?from=&to=&garageId=` (OWNER o ADMIN)

```json
{
  "from": "2026-04-11", "to": "2026-05-10", "currency": "COP",
  "summary": {
    "totalReservations": 120, "paidReservations": 98, "completed": 90, "cancelled": 12, "expired": 8, "noShow": 2,
    "revenue": 412000.00, "overtimeRevenue": 18000.00, "occupancyRate": 0.3712, "averageRating": 4.6
  },
  "garages": [ { "garageId": "…", "name": "Garaje Centro", "totalSpots": 20, "reservations": 80, "completed": 60,
                 "cancelled": 8, "noShow": 1, "revenue": 300000.00, "occupancyRate": 0.41, "averageRating": 4.7, "ratingCount": 35 } ],
  "daily": [ { "date": "2026-04-11", "reservations": 4, "revenue": 16000.00 } ]
}
```

- `revenue`: reservas pagadas y no canceladas. `occupancyRate`: minutos reservados y pagados ÷ (plazas × minutos del rango), de 0 a 1.
- `daily` trae un punto por día del rango (sirve directo para una gráfica).
- 400 rango inválido, 403 si `garageId` no es suyo.

### GET `/api/v1/reports/admin?from=&to=` (ADMIN)

Igual que el del propietario pero de toda la plataforma, más:

- `users`: `{ total, drivers, owners, admins, disabled, newInRange }`
- `garages`: `{ total, active, verified, pendingVerification }`
- `averageSearchToReservationSeconds`: promedio del tiempo entre `searchStartedAt` y la reserva (null si no hay datos).
- `topGarages`: los 5 garajes con más ingresos.

---

## 13. Administración

Base: `/api/v1/admin`. Solo `ROLE_ADMIN` (403 para los demás).

| Método y ruta | Cuerpo o params | Respuesta | Errores |
|---|---|---|---|
| `GET /users` | `q` (correo o nombre), `role` (`DRIVER`, `OWNER`, `ADMIN`), `enabled`, `page`, `size` (20) | `Page<AdminUser>` | 400 rol desconocido |
| `PATCH /users/{id}/status` | `{ "enabled": false }` | `AdminUser` | 400 si es la propia cuenta, 404 |
| `PUT /users/{id}/roles` | `{ "roles": ["DRIVER", "OWNER"] }` (roles finales) | `AdminUser` | 400 si se quita su propio ADMIN, 404 |
| `GET /garages?verificationStatus=PENDING` | `page`, `size` | `Page<Garage>` (los más antiguos primero) | — |
| `POST /garages/{id}/verification` | `{ "approved": false, "notes": "Las fotos no muestran la entrada" }` | `Garage` | 400, 404 |

**`AdminUser`:** `{ id, email, fullName, phone, roles, enabled, createdAt }`. Deshabilitar una cuenta cierra sus sesiones; `ROLE_USER` se conserva siempre.

---

## 14. Flujos completos

### Conductor: reservar y pagar

1. Buscar: `GET /garages/search?lat=…&lng=…&onlyAvailable=true` (y abrir el SSE para actualizar en vivo).
2. Ver detalle: `GET /garages/{id}/detail` y reseñas `GET /garages/{id}/reviews`.
3. Cotizar al elegir horario: `GET /garages/{id}/reservation-quote?...`.
4. Reservar: `POST /reservations` → guardar `id`, `code` y `paymentDeadline` (mostrar cuenta regresiva).
5. Pagar: `POST /reservations/{id}/payments` → abrir `checkoutUrl` → al volver, consultar hasta `CONFIRMED`.
6. Llegar al garaje y mostrar `code` (texto o QR).
7. Después de la salida, calificar: `POST /reservations/{id}/review`. Comprobante: `GET /reservations/{id}/receipt`.

### Propietario: operar el garaje

1. Registrarse con `role: OWNER` (o activar el rol con `POST /users/me/roles`).
2. Crear garaje, subir fotos y pedir verificación.
3. Panel del día: `GET /reservations/owned?date=2026-05-10&garageId=…`.
4. Llega un carro: `POST /reservations/check-in` con el código. Sale: `POST /reservations/check-out`.
5. Reporte: `GET /reports/owner`.

### Sesión en los fronts

1. Login → guardar `accessToken`, `refreshToken` y `roles`.
2. Cada llamada con `Authorization: Bearer <accessToken>`.
3. Ante un 401: `POST /auth/refresh` una sola vez, guardar el par nuevo y repetir. Si falla, ir al login.
4. Al cerrar sesión: `DELETE /notifications/devices?token=…` y `POST /auth/logout`.

Los clientes de `clients/` ya hacen los pasos 2 y 3.

---

## 15. Enums

| Enum | Valores |
|---|---|
| `AccountType` | `DRIVER`, `OWNER` |
| Roles | `ROLE_USER`, `ROLE_DRIVER`, `ROLE_OWNER`, `ROLE_ADMIN` |
| `VehicleType` | `CAR`, `MOTORCYCLE`, `BICYCLE`, `TRUCK`, `ELECTRIC`, `DISABLED` |
| `GarageStatus` | `ACTIVE`, `INACTIVE`, `SUSPENDED` |
| `GarageAvailability` | `AVAILABLE`, `FEW_SPOTS`, `FULL`, `CLOSED` |
| `SpotStatus` | `AVAILABLE`, `OCCUPIED`, `RESERVED`, `OUT_OF_SERVICE` |
| `VerificationStatus` | `UNVERIFIED`, `PENDING`, `VERIFIED`, `REJECTED` |
| `GarageSortOption` | `DISTANCE`, `PRICE_ASC`, `PRICE_DESC`, `AVAILABILITY`, `NAME`, `NEWEST`, `RATING` |
| `ReservationStatus` | `PENDING_PAYMENT`, `CONFIRMED`, `CHECKED_IN`, `COMPLETED`, `CANCELLED`, `EXPIRED`, `NO_SHOW` |
| `PaymentStatus` | `PENDING`, `APPROVED`, `DECLINED`, `VOIDED`, `ERROR`, `REFUND_PENDING` |
| `DevicePlatform` | `ANDROID`, `IOS`, `WEB` |
| `NotificationType` | `RESERVATION_CREATED`, `RESERVATION_CONFIRMED`, `RESERVATION_CANCELLED`, `RESERVATION_EXPIRED`, `RESERVATION_NO_SHOW`, `RESERVATION_REMINDER`, `CHECK_IN`, `CHECK_OUT`, `PAYMENT_DECLINED`, `NEW_REVIEW`, `GARAGE_VERIFIED`, `GARAGE_REJECTED`, `ACCOUNT` |
