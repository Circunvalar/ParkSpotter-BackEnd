# Estado del proyecto frente a lo documentado

Compara lo que piden el documento de Práctica IV (requisitos RF y RNF), el Product Goal, el plan de sprints (`ParkSpotter_Sprint.xlsx`), la Definition of Done y el Risk Register con lo que hace este backend. Sirve como lista de verificación para la entrega.

Leyenda: ✅ cumplido en el backend · 🟡 cumplido con una aclaración · 🔵 depende del hosting o de los fronts.

## Requisitos funcionales

| Requisito | Estado | Dónde |
|---|---|---|
| RF-01 Registro y autenticación (conductor y propietario) | ✅ | `/api/v1/auth/*`: registro con rol, login JWT + refresh token rotativo, logout, recuperación y cambio de contraseña, bloqueo por intentos. |
| RF-02 Gestión de perfiles (crear, consultar, editar, eliminar) | ✅ | `/api/v1/users/me` (GET, PATCH, DELETE), activar el otro rol con `POST /users/me/roles`. |
| RF-03 Mapa con garajes en tiempo real | ✅ | `/garages/map`, `/garages/{id}/availability`, streams SSE `/garages/availability/stream`. El mapa (Google Maps/Mapbox) lo dibujan los fronts. |
| RF-04 Búsqueda con filtros de precio, distancia, horario y calificación | ✅ | `/garages/search`: `minPrice`, `maxPrice`, `lat`/`lng`/`radiusKm`, `openNow`, `open24Hours`, `minRating`, `verifiedOnly`, tipo de vehículo y orden. Disponibilidad para una franja concreta: `/garages/{id}/reservation-quote`. |
| RF-05 Reservas anticipadas con bloqueo del espacio | ✅ | `POST /reservations`: valida horario y solapamientos, asigna plaza con bloqueo y control de concurrencia. |
| RF-06 Estado automático (disponible / ocupado / reservado) | ✅ | Tareas programadas: vencer sin pago, apartar plaza 30 min antes, no presentado; entrada y salida con código. |
| RF-07 Pagos electrónicos con comprobante | ✅ | Wompi (pasarela colombiana; el documento menciona PayU/Stripe como ejemplos) con webhook firmado e idempotente, pasarela simulada para demos, `GET /reservations/{id}/receipt`. |
| RF-08 Notificaciones push y correo (confirmación, vencimientos, disponibilidad) | ✅ | Bandeja `/notifications`, push con Firebase, correo SMTP, recordatorio 30 min antes; cambios de disponibilidad en vivo por SSE. |
| RF-09 Calificaciones y comentarios visibles | ✅ | `/reservations/{id}/review`, `/garages/{id}/reviews`, promedio en búsqueda y detalle. **Nuevo:** calificación opcional de seguridad percibida (`securityRating`, `averageSecurityRating`), como recomienda el Product Goal. |
| RF-10 Reportes para usuarios y administradores | ✅ | `/reports/owner`, `/reports/admin` y **nuevo** `/reports/driver` (el documento pide reportes "para usuarios", no solo para propietarios). |

## Requisitos no funcionales

| Requisito | Estado | Comentario |
|---|---|---|
| RNF-01 Ley 1581 de 2012 | 🟡 | Datos mínimos, eliminación de cuenta con anonimización, correos no expuestos en reseñas. La política de privacidad y el aviso de tratamiento de datos se muestran en los fronts. |
| RNF-02 TLS y cifrado | 🟡 🔵 | HTTPS obligatorio (lo da Render; HSTS activo) y conexión cifrada a la base (`sslmode=require`). Las contraseñas se guardan con **BCrypt** (hash, que es lo correcto para contraseñas; AES-256 es reversible y no se recomienda para esto). No se guardan datos de tarjeta. |
| RNF-03 PCI DSS | ✅ | La tarjeta la procesa Wompi; el backend nunca la ve. Webhook con firma e idempotencia. |
| RNF-04 Disponibilidad 24/7 (≤ 0,5 % caída) | 🔵 | El plan gratuito se duerme sin tráfico. Con el monitor de [Despliegue-Gratuito.md](Despliegue-Gratuito.md#7-mantenerlo-despierto-recomendado) se mantiene activo, pero el 99,5 % solo se garantiza con un plan pago. |
| RNF-05 10 000 usuarios concurrentes | 🔵 | El código está preparado (paginación, índices, pool de conexiones), pero la cifra depende del plan de hosting; el gratuito no la alcanza. |
| RNF-06 Respuesta < 3 s en el 95 % | ✅ | Pruebas de rendimiento automáticas (`GarageSearchPerformanceTest`) y el smoke test mide cada operación: en PostgreSQL real búsqueda ≈ 0,07 s y crear reserva ≈ 0,06 s. |
| RNF-07 Reserva en máximo 4 pasos | 🔵 | La API lo permite (buscar → detalle/cotizar → reservar → pagar); el flujo de pantallas es de los fronts. |
| RNF-10 Soporte con FAQ, contacto y tickets | ✅ | **Nuevo:** `/api/v1/support/faqs`, `/support/contact`, casos con conversación y bandeja de administración. |

## Plan de sprints (entregables del backend)

| Sprint | Entregables | Estado |
|---|---|---|
| 1 Autenticación y roles | Registro, login/logout, recuperación, perfil, roles, JWT + refresh, BCrypt, Bean Validation, `/api/v1/` | ✅ |
| 2 Gestión de garajes | CRUD, disponibilidad y precios, validaciones en servicio, auditoría `created_at`/`updated_at` | ✅ |
| 3 Búsqueda y tiempo real | Filtros, paginación, índices, estado en vivo | ✅ |
| 4 Reservas | Crear/cancelar, historial, cambio automático de estado, transacciones y concurrencia | ✅ |
| 5 Pagos y notificaciones | Pasarela, webhook, idempotencia, notificaciones, comprobante | ✅ |
| 6 Calidad y cierre | Calificaciones, reportes, ajustes finales, cobertura ≥ 80 %, revisión de seguridad | ✅ Cobertura de líneas ≈ 99 % (mínimo exigido en la CI: 90 %). |

Observaciones del Product Goal: verificación de garajes con fotos e insignia ✅, métrica de tiempo de búsqueda a reserva (`searchStartedAt` → reporte admin) ✅, seguridad física percibida (calificación de seguridad en reseñas) ✅.

## Fuera del alcance del backend o pendiente de decisión

- **Autenticación en dos pasos**: aparece en el texto del módulo de pagos del documento, pero no en los RF ni en el plan de sprints. No está implementada.
- **Correo de confirmación al registrarse**: marcado como opcional en el backlog (PB-01). No está implementado; la recuperación de contraseña sí envía correo.
- **Analítica de "desde que abre la app"**: el backend mide desde `searchStartedAt`, que deben enviar los fronts al crear la reserva.
- Pantallas, mapa, accesibilidad (WCAG 2.2) y almacenamiento seguro de tokens son de los fronts web y móvil (sus guías están en [Conexion-Web-y-Movil.md](Conexion-Web-y-Movil.md) y en [`clients/`](../clients/README.md)).
