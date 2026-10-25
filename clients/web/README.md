# Cliente web (TypeScript)

Un solo archivo, [`parkspotter-api.ts`](parkspotter-api.ts), sin dependencias. Sirve en React, Vue, Angular, Svelte o JavaScript puro (en JS se puede quitar los tipos o compilarlo con `tsc`).

## 1. Instalar

1. Copiar `parkspotter-api.ts` en el proyecto, por ejemplo `src/api/parkspotter-api.ts`.
2. Definir la URL del backend en el `.env` del front:

   ```bash
   # Vite: .env.development
   VITE_API_URL=http://localhost:8080
   # Vite: .env.production (servidor en Azure; usar la URL real de la app)
   VITE_API_URL=https://parkspotter-api.azurewebsites.net
   # Next.js: lo mismo con NEXT_PUBLIC_API_URL
   ```

   En producción la URL debe ser HTTPS: un front servido por HTTPS no puede llamar a una API por HTTP.

3. Agregar el dominio del front a `APP_CORS_ALLOWED_ORIGINS` en el backend (Application settings de Azure; por ejemplo `https://parkspotter-web.vercel.app,http://localhost:3000`, sin `/` final). Detalles y requisitos en [docs/Conexion-Web-y-Movil.md](../../docs/Conexion-Web-y-Movil.md).

## 2. Crear una sola instancia

```ts
// src/api/index.ts
import { ParkSpotterApi } from './parkspotter-api';

export const api = new ParkSpotterApi({
  baseUrl: import.meta.env.VITE_API_URL,
  onSessionExpired: () => (window.location.href = '/login'),
});
```

La sesión se guarda en `localStorage`. Para guardarla en otro lado, pasar `tokenStore` con `get()` y `set()`.

## 3. Ejemplos

### Login y roles

```ts
import { api } from '@/api';
import { ApiError } from '@/api/parkspotter-api';

try {
  const session = await api.login(email, password);
  if (session.roles.includes('ROLE_OWNER')) navigate('/panel');
  else navigate('/buscar');
} catch (error) {
  if (error instanceof ApiError) setError(error.message); // mensaje en español del backend
}
```

Registro de propietario: `api.register({ email, password, role: 'OWNER', fullName })`.

### Buscar y escuchar disponibilidad en vivo (React)

```tsx
const [garages, setGarages] = useState<GarageSummary[]>([]);

useEffect(() => {
  api.searchGarages({ lat, lng, onlyAvailable: true, minRating: 4 }).then((page) => setGarages(page.content));
  const stop = api.subscribeAvailability((change) =>
    setGarages((list) => list.map((g) => (g.id === change.garageId ? { ...g, availableSpots: change.availableSpots, availability: change.availability } : g))),
  );
  return stop; // cierra la conexión al desmontar
}, [lat, lng]);
```

### Reservar y pagar

```ts
const quote = await api.quote(garageId, 'CAR', start.toISOString(), end.toISOString());
if (!quote.available) return showMessage(quote.unavailableReason);

const reservation = await api.createReservation({
  garageId, vehicleType: 'CAR', vehiclePlate: 'ABC123',
  startTime: start.toISOString(), endTime: end.toISOString(),
  searchStartedAt, // opcional: cuándo abrió la búsqueda (métrica del reporte)
});

const payment = await api.createPayment(reservation.id, `${window.location.origin}/pagos/resultado?reserva=${reservation.id}`);
window.location.href = payment.checkoutUrl!;
```

En la página `/pagos/resultado`, consultar hasta que la reserva quede confirmada:

```ts
async function waitForConfirmation(reservationId: string) {
  for (let i = 0; i < 20; i++) {
    const r = await api.getReservation(reservationId);
    if (r.status === 'CONFIRMED') return r;
    if (r.status === 'EXPIRED' || r.status === 'CANCELLED') throw new Error('La reserva ya no está activa');
    await new Promise((resolve) => setTimeout(resolve, 3000));
  }
  throw new Error('El pago sigue en proceso. Revisa tus reservas en unos minutos.');
}
```

Con la pasarela simulada (desarrollo), en lugar de esperar se puede llamar `api.simulatePayment(payment.id, 'APPROVED')`.

> La `redirectUrl` debe empezar por un prefijo de `PAYMENTS_REDIRECT_PREFIXES` en el backend (por defecto `http://localhost:3000/`). Agreguen el dominio real del front al desplegar.

### Panel del propietario

```ts
const today = new Date().toLocaleDateString('en-CA', { timeZone: 'America/Bogota' }); // "2026-05-10"
const { content } = await api.ownedReservations({ date: today, garageId });
await api.checkIn('PS-7K3MQ2');
await api.checkOut('PS-7K3MQ2');
const report = await api.ownerReport({ from: '2026-05-01', to: '2026-05-31' });
```

### Fotos

```tsx
<input type="file" accept="image/jpeg,image/png,image/webp"
       onChange={(e) => e.target.files?.[0] && api.uploadPhoto(garageId, e.target.files[0], e.target.files[0].name)} />
<img src={api.photoUrl(photo)} alt="" />
```

### Notificaciones

```ts
const { unread } = await api.unreadCount();
const page = await api.notifications({ unreadOnly: false });
await api.markNotificationRead(notification.id);
```

Push en el navegador (opcional): con el SDK web de Firebase, `getToken(messaging, { vapidKey })` y luego `api.registerDevice(token, 'WEB')`.

### Recuperar contraseña

1. Pantalla "Olvidé mi contraseña": `api.forgotPassword(email)` y mostrar siempre "Si el correo existe, te enviamos un enlace".
2. Pantalla `/restablecer-contrasena?token=…` (la URL se configura en `RESET_PASSWORD_URL` del backend): `api.resetPassword(token, nuevaClave)` y enviar al login.

## 4. Fechas

Enviar siempre `fecha.toISOString()` (UTC). Para mostrar: `new Date(r.startTime).toLocaleString('es-CO', { timeZone: 'America/Bogota' })`.
