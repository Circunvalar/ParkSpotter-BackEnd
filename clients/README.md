# Clientes de la API

Código listo para copiar en los fronts. Cubre todos los endpoints de [docs/API-Referencia.md](../docs/API-Referencia.md), maneja la sesión (access y refresh token) y la disponibilidad en tiempo real.

| Carpeta | Front | Contenido |
|---|---|---|
| [web/](web/README.md) | Web (React, Vue, Angular o JS puro) | `parkspotter-api.ts`: un archivo TypeScript sin dependencias (usa `fetch`). |
| [android/](android/README.md) | App Android (Kotlin) | Modelos, interfaz Retrofit y cliente con renovación automática del token. |

Si el backend cambia, el contrato actualizado siempre está en `GET /v3/api-docs` (OpenAPI) y se puede ver en `/swagger-ui.html`.
