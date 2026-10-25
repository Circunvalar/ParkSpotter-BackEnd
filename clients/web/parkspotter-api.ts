/**
 * Cliente de la API de ParkSpotter para el front web (TypeScript, sin dependencias: usa fetch).
 *
 * Copia este archivo al proyecto web (por ejemplo src/api/parkspotter-api.ts) y crea una sola instancia:
 *
 *   export const api = new ParkSpotterApi({ baseUrl: import.meta.env.VITE_API_URL });
 *
 * Maneja el access token y el refresh token: si una llamada responde 401, renueva el token una vez y
 * repite la llamada. Si la renovación falla, borra la sesión y llama a onSessionExpired (ir al login).
 * Todas las fechas viajan como texto ISO-8601 en UTC (ej. "2026-05-10T14:00:00Z").
 * Referencia completa de endpoints: docs/API-Referencia.md
 */

// ---------- tipos comunes ----------

export type UUID = string;
/** Fecha y hora ISO-8601 en UTC, ej. "2026-05-10T14:00:00Z". */
export type Instant = string;
/** Fecha sin hora, ej. "2026-05-10". */
export type LocalDate = string;
/** Hora local, ej. "07:00:00". */
export type LocalTime = string;

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/** Forma de todos los errores de la API. details trae el error de cada campo en las validaciones (400). */
export interface ApiErrorBody {
  timestamp: Instant;
  status: number;
  error: string;
  message: string;
  path: string;
  details: Record<string, unknown>;
}

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly body: ApiErrorBody | null) {
    super(body?.message ?? `Error HTTP ${status}`);
  }
}

// ---------- enums ----------

export type AccountType = 'DRIVER' | 'OWNER';
export type RoleName = 'ROLE_USER' | 'ROLE_DRIVER' | 'ROLE_OWNER' | 'ROLE_ADMIN';
export type VehicleType = 'CAR' | 'MOTORCYCLE' | 'BICYCLE' | 'TRUCK' | 'ELECTRIC' | 'DISABLED';
export type GarageStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';
export type GarageAvailability = 'AVAILABLE' | 'FEW_SPOTS' | 'FULL' | 'CLOSED';
export type SpotStatus = 'AVAILABLE' | 'OCCUPIED' | 'RESERVED' | 'OUT_OF_SERVICE';
export type VerificationStatus = 'UNVERIFIED' | 'PENDING' | 'VERIFIED' | 'REJECTED';
export type GarageSortOption = 'DISTANCE' | 'PRICE_ASC' | 'PRICE_DESC' | 'AVAILABILITY' | 'NAME' | 'NEWEST' | 'RATING';
export type ReservationStatus =
  | 'PENDING_PAYMENT' | 'CONFIRMED' | 'CHECKED_IN' | 'COMPLETED' | 'CANCELLED' | 'EXPIRED' | 'NO_SHOW';
export type PaymentStatus = 'PENDING' | 'APPROVED' | 'DECLINED' | 'VOIDED' | 'ERROR' | 'REFUND_PENDING';
export type DevicePlatform = 'ANDROID' | 'IOS' | 'WEB';
export type NotificationType =
  | 'RESERVATION_CREATED' | 'RESERVATION_CONFIRMED' | 'RESERVATION_CANCELLED' | 'RESERVATION_EXPIRED'
  | 'RESERVATION_NO_SHOW' | 'RESERVATION_REMINDER' | 'CHECK_IN' | 'CHECK_OUT' | 'PAYMENT_DECLINED'
  | 'NEW_REVIEW' | 'GARAGE_VERIFIED' | 'GARAGE_REJECTED' | 'ACCOUNT';

// ---------- autenticación y perfil ----------

export interface RegisterRequest {
  email: string;
  password: string;
  /** Por defecto DRIVER. */
  role?: AccountType;
  fullName?: string;
  phone?: string;
}

export interface TokenPair {
  userId: UUID;
  email: string;
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresInSeconds: number;
  roles: RoleName[];
}

export interface User {
  id: UUID;
  email: string;
  fullName: string | null;
  phone: string | null;
  roles: RoleName[];
  enabled: boolean;
  createdAt: Instant;
}

// ---------- garajes ----------

export interface GarageRequest {
  name: string;
  description?: string;
  phone?: string;
  addressLine: string;
  city: string;
  state?: string;
  country?: string;
  postalCode?: string;
  latitude: number;
  longitude: number;
  totalSpots: number;
  pricePerHour: number;
  open24Hours: boolean;
  openingTime?: LocalTime | null;
  closingTime?: LocalTime | null;
}

export type GarageUpdateRequest = Partial<GarageRequest>;

export interface Garage {
  id: UUID;
  name: string;
  description: string | null;
  phone: string | null;
  ownerId: UUID;
  /** Solo llega al dueño o a un administrador. */
  ownerEmail: string | null;
  addressLine: string;
  city: string;
  state: string | null;
  country: string | null;
  postalCode: string | null;
  latitude: number;
  longitude: number;
  totalSpots: number;
  availableSpots: number;
  occupiedSpots: number;
  reservedSpots: number;
  outOfServiceSpots: number;
  pricePerHour: number;
  open24Hours: boolean;
  openingTime: LocalTime | null;
  closingTime: LocalTime | null;
  status: GarageStatus;
  availability: GarageAvailability;
  verificationStatus: VerificationStatus;
  verified: boolean;
  averageRating: number;
  ratingCount: number;
  createdAt: Instant;
  updatedAt: Instant;
}

export interface ParkingSpot {
  id: UUID;
  garageId: UUID;
  code: string;
  floor: number;
  vehicleType: VehicleType;
  status: SpotStatus;
  updatedAt: Instant;
}

export interface ParkingSpotRequest {
  code: string;
  floor: number;
  vehicleType: VehicleType;
}

export interface GaragePhoto {
  id: UUID;
  garageId: UUID;
  contentType: string;
  sizeBytes: number;
  /** Ruta relativa de la imagen: concatenarla con baseUrl (o usar api.photoUrl). */
  url: string;
  createdAt: Instant;
}

export interface Availability {
  garageId: UUID;
  status: GarageStatus;
  availability: GarageAvailability;
  openNow: boolean;
  totalSpots: number;
  availableSpots: number;
  occupiedSpots: number;
  reservedSpots: number;
  outOfServiceSpots: number;
  timestamp: Instant;
}

export interface VehicleTypeAvailability {
  vehicleType: VehicleType;
  totalSpots: number;
  availableSpots: number;
  occupiedSpots: number;
  reservedSpots: number;
  outOfServiceSpots: number;
}

export interface GarageDetail {
  id: UUID;
  name: string;
  description: string | null;
  phone: string | null;
  addressLine: string;
  city: string;
  state: string | null;
  country: string | null;
  postalCode: string | null;
  latitude: number;
  longitude: number;
  pricePerHour: number;
  open24Hours: boolean;
  openingTime: LocalTime | null;
  closingTime: LocalTime | null;
  availability: Availability;
  availabilityByVehicleType: VehicleTypeAvailability[];
  floors: number[];
  /** Vacío salvo que se pida includeSpots=true. */
  spots: ParkingSpot[];
  verificationStatus: VerificationStatus;
  verified: boolean;
  averageRating: number;
  ratingCount: number;
  photos: GaragePhoto[];
  createdAt: Instant;
  updatedAt: Instant;
}

export interface GarageSearchParams {
  q?: string;
  city?: string;
  minPrice?: number;
  maxPrice?: number;
  vehicleType?: VehicleType;
  onlyAvailable?: boolean;
  minAvailableSpots?: number;
  open24Hours?: boolean;
  openNow?: boolean;
  lat?: number;
  lng?: number;
  radiusKm?: number;
  /** 1 a 5. */
  minRating?: number;
  verifiedOnly?: boolean;
  sort?: GarageSortOption;
  page?: number;
  size?: number;
}

export interface GarageSummary {
  id: UUID;
  name: string;
  addressLine: string;
  city: string;
  latitude: number;
  longitude: number;
  pricePerHour: number;
  totalSpots: number;
  availableSpots: number;
  occupiedSpots: number;
  reservedSpots: number;
  availability: GarageAvailability;
  open24Hours: boolean;
  openingTime: LocalTime | null;
  closingTime: LocalTime | null;
  openNow: boolean;
  /** Solo si se enviaron lat y lng. */
  distanceKm: number | null;
  verified: boolean;
  averageRating: number;
  ratingCount: number;
}

export interface MapBounds {
  minLat: number;
  minLng: number;
  maxLat: number;
  maxLng: number;
  /** 1 a 500, por defecto 200. */
  limit?: number;
}

export interface GarageMarker {
  id: UUID;
  name: string;
  latitude: number;
  longitude: number;
  pricePerHour: number;
  availableSpots: number;
  totalSpots: number;
  availability: GarageAvailability;
  openNow: boolean;
  verified: boolean;
  averageRating: number;
}

export interface GarageMap {
  markers: GarageMarker[];
  count: number;
  truncated: boolean;
}

// ---------- reservas y pagos ----------

export interface ReservationQuote {
  garageId: UUID;
  vehicleType: VehicleType;
  startTime: Instant;
  endTime: Instant;
  durationMinutes: number;
  pricePerHour: number;
  totalAmount: number;
  currency: 'COP';
  availableSpots: number;
  available: boolean;
  /** Motivo en español cuando available es false (fuera de horario, sin plazas...). */
  unavailableReason: string | null;
}

export interface ReservationRequest {
  garageId: UUID;
  vehicleType: VehicleType;
  vehiclePlate: string;
  startTime: Instant;
  endTime: Instant;
  /** Opcional: una plaza concreta. Si no se envía, el backend asigna una libre. */
  spotId?: UUID;
  /** Opcional: cuándo empezó a buscar el usuario (métrica de tiempo de búsqueda a reserva). */
  searchStartedAt?: Instant;
}

export interface Reservation {
  id: UUID;
  /** Código para la entrada y la salida, ej. "PS-7K3MQ2". */
  code: string;
  status: ReservationStatus;
  garageId: UUID;
  garageName: string;
  garageAddress: string;
  garageLatitude: number;
  garageLongitude: number;
  spotId: UUID | null;
  spotCode: string | null;
  spotFloor: number | null;
  driverId: UUID;
  driverName: string;
  vehicleType: VehicleType;
  vehiclePlate: string;
  startTime: Instant;
  endTime: Instant;
  durationMinutes: number;
  pricePerHour: number;
  totalAmount: number;
  overtimeMinutes: number;
  overtimeAmount: number;
  currency: 'COP';
  /** Hasta cuándo se puede pagar; después la reserva vence (EXPIRED). */
  paymentDeadline: Instant;
  paid: boolean;
  paidAt: Instant | null;
  checkInAt: Instant | null;
  checkOutAt: Instant | null;
  cancelledAt: Instant | null;
  cancellationReason: string | null;
  cancellable: boolean;
  createdAt: Instant;
}

export interface OwnedReservationsParams {
  /** Día (zona horaria de Bogotá). */
  date?: LocalDate;
  garageId?: UUID;
  status?: ReservationStatus;
  page?: number;
  size?: number;
}

export interface Payment {
  id: UUID;
  reservationId: UUID;
  reference: string;
  amount: number;
  amountInCents: number;
  currency: 'COP';
  status: PaymentStatus;
  provider: 'WOMPI' | 'SANDBOX';
  paymentMethod: string | null;
  /** Abrir esta URL para pagar (Wompi) o, en sandbox, la URL de retorno. */
  checkoutUrl: string | null;
  redirectUrl: string | null;
  /** Solo Wompi, para usar el widget en lugar de redirigir. */
  publicKey: string | null;
  integritySignature: string | null;
  createdAt: Instant;
  approvedAt: Instant | null;
}

export interface Receipt {
  receiptNumber: string;
  issuedAt: Instant;
  reservationId: UUID;
  reservationCode: string;
  reservationStatus: ReservationStatus;
  customerName: string;
  customerEmail: string;
  garageName: string;
  garageAddress: string;
  spotCode: string | null;
  vehicleType: VehicleType;
  vehiclePlate: string;
  startTime: Instant;
  endTime: Instant;
  durationMinutes: number;
  pricePerHour: number;
  amountPaid: number;
  currency: 'COP';
  paymentProvider: string;
  paymentMethod: string | null;
  paymentReference: string;
  providerTransactionId: string | null;
  paidAt: Instant | null;
  paymentStatus: PaymentStatus;
}

// ---------- reseñas, notificaciones, admin y reportes ----------

export interface Review {
  id: UUID;
  garageId: UUID;
  reservationId: UUID;
  rating: number;
  comment: string | null;
  /** Solo el primer nombre del autor (o "Usuario"). */
  authorName: string;
  createdAt: Instant;
}

export interface Notification {
  id: UUID;
  type: NotificationType;
  title: string;
  body: string;
  /** Id de la reserva o del garaje relacionado, según el tipo. */
  referenceId: UUID | null;
  read: boolean;
  readAt: Instant | null;
  createdAt: Instant;
}

export interface AdminUser {
  id: UUID;
  email: string;
  fullName: string | null;
  phone: string | null;
  roles: RoleName[];
  enabled: boolean;
  createdAt: Instant;
}

export interface ReportSummary {
  totalReservations: number;
  paidReservations: number;
  completed: number;
  cancelled: number;
  expired: number;
  noShow: number;
  revenue: number;
  overtimeRevenue: number;
  /** De 0 a 1. */
  occupancyRate: number;
  averageRating: number;
}

export interface GarageReport {
  garageId: UUID;
  name: string;
  totalSpots: number;
  reservations: number;
  completed: number;
  cancelled: number;
  noShow: number;
  revenue: number;
  occupancyRate: number;
  averageRating: number;
  ratingCount: number;
}

export interface DailyPoint {
  date: LocalDate;
  reservations: number;
  revenue: number;
}

export interface OwnerReport {
  from: LocalDate;
  to: LocalDate;
  currency: 'COP';
  summary: ReportSummary;
  garages: GarageReport[];
  daily: DailyPoint[];
}

export interface AdminReport {
  from: LocalDate;
  to: LocalDate;
  currency: 'COP';
  users: { total: number; drivers: number; owners: number; admins: number; disabled: number; newInRange: number };
  garages: { total: number; active: number; verified: number; pendingVerification: number };
  summary: ReportSummary;
  averageSearchToReservationSeconds: number | null;
  topGarages: GarageReport[];
  daily: DailyPoint[];
}

// ---------- almacenamiento de la sesión ----------

export interface TokenStore {
  get(): TokenPair | null;
  set(tokens: TokenPair | null): void;
}

/** Guarda la sesión en localStorage. Cámbialo por memoria o cookies si el equipo lo prefiere. */
export class LocalStorageTokenStore implements TokenStore {
  constructor(private readonly key = 'parkspotter.session') {}

  get(): TokenPair | null {
    try {
      const raw = localStorage.getItem(this.key);
      return raw ? (JSON.parse(raw) as TokenPair) : null;
    } catch {
      return null;
    }
  }

  set(tokens: TokenPair | null): void {
    try {
      if (tokens) localStorage.setItem(this.key, JSON.stringify(tokens));
      else localStorage.removeItem(this.key);
    } catch {
      // Navegación privada o almacenamiento bloqueado: la sesión dura lo que la pestaña.
    }
  }
}

export interface ParkSpotterApiOptions {
  /** URL del backend sin barra final, ej. "https://api.parkspotter.co". */
  baseUrl: string;
  tokenStore?: TokenStore;
  /** Se llama cuando la sesión no se pudo renovar (redirigir al login). */
  onSessionExpired?: () => void;
}

type Query = Record<string, string | number | boolean | null | undefined>;

interface RequestOptions {
  query?: Query;
  body?: unknown;
  /** false para endpoints públicos: no envía el token ni intenta renovarlo. */
  auth?: boolean;
}

// ---------- cliente ----------

export class ParkSpotterApi {
  private readonly baseUrl: string;
  private readonly tokens: TokenStore;
  private readonly onSessionExpired?: () => void;
  private refreshing: Promise<boolean> | null = null;

  constructor(options: ParkSpotterApiOptions) {
    this.baseUrl = options.baseUrl.replace(/\/+$/, '');
    this.tokens = options.tokenStore ?? new LocalStorageTokenStore();
    this.onSessionExpired = options.onSessionExpired;
  }

  /** Sesión actual (o null). Útil para saber los roles: session?.roles.includes('ROLE_OWNER'). */
  get session(): TokenPair | null {
    return this.tokens.get();
  }

  hasRole(role: RoleName): boolean {
    return this.session?.roles.includes(role) ?? false;
  }

  // ----- autenticación -----

  async register(request: RegisterRequest): Promise<TokenPair> {
    return this.saveSession(await this.request<TokenPair>('POST', '/api/v1/auth/register', { body: request, auth: false }));
  }

  async login(email: string, password: string): Promise<TokenPair> {
    return this.saveSession(await this.request<TokenPair>('POST', '/api/v1/auth/login', { body: { email, password }, auth: false }));
  }

  async logout(): Promise<void> {
    const refreshToken = this.session?.refreshToken;
    this.tokens.set(null);
    if (refreshToken) {
      await this.request<void>('POST', '/api/v1/auth/logout', { body: { refreshToken }, auth: false }).catch(() => undefined);
    }
  }

  /** Siempre responde igual (exista o no el correo). El correo trae un enlace con ?token=... */
  forgotPassword(email: string): Promise<void> {
    return this.request('POST', '/api/v1/auth/password/forgot', { body: { email }, auth: false });
  }

  /** El token llega en el enlace del correo. Cierra todas las sesiones del usuario. */
  resetPassword(token: string, newPassword: string): Promise<void> {
    return this.request('POST', '/api/v1/auth/password/reset', { body: { token, newPassword }, auth: false });
  }

  changePassword(currentPassword: string, newPassword: string): Promise<void> {
    return this.request('PATCH', '/api/v1/auth/password', { body: { currentPassword, newPassword } });
  }

  // ----- perfil -----

  me(): Promise<User> {
    return this.request('GET', '/api/v1/users/me');
  }

  /** Campos null no cambian; texto vacío borra el dato. */
  updateProfile(changes: { fullName?: string | null; phone?: string | null }): Promise<User> {
    return this.request('PATCH', '/api/v1/users/me', { body: changes });
  }

  /** Activa el otro perfil (un conductor que también quiere publicar su garaje, o al revés). */
  async addRole(role: AccountType): Promise<User> {
    const user = await this.request<User>('POST', '/api/v1/users/me/roles', { body: { role } });
    // Los roles del token se recalculan en cada petición; se actualizan también en la sesión guardada.
    const session = this.session;
    if (session) this.tokens.set({ ...session, roles: user.roles });
    return user;
  }

  async deleteAccount(password: string): Promise<void> {
    await this.request('POST', '/api/v1/users/me/delete', { body: { password } });
    this.tokens.set(null);
  }

  // ----- búsqueda pública -----

  searchGarages(params: GarageSearchParams = {}): Promise<Page<GarageSummary>> {
    return this.request('GET', '/api/v1/garages/search', { query: { ...params }, auth: false });
  }

  mapMarkers(bounds: MapBounds, filters: GarageSearchParams = {}): Promise<GarageMap> {
    return this.request('GET', '/api/v1/garages/map', { query: { ...filters, ...bounds }, auth: false });
  }

  garageDetail(garageId: UUID, includeSpots = false): Promise<GarageDetail> {
    return this.request('GET', `/api/v1/garages/${garageId}/detail`, { query: { includeSpots }, auth: false });
  }

  availability(garageId: UUID): Promise<Availability> {
    return this.request('GET', `/api/v1/garages/${garageId}/availability`, { auth: false });
  }

  /**
   * Disponibilidad en tiempo real (SSE). Sin garageId escucha todos los garajes (para el mapa).
   * Devuelve una función para cerrar la conexión (llamarla al desmontar el componente).
   */
  subscribeAvailability(onChange: (availability: Availability) => void, garageId?: UUID): () => void {
    const path = garageId ? `/api/v1/garages/${garageId}/availability/stream` : '/api/v1/garages/availability/stream';
    const source = new EventSource(this.baseUrl + path);
    source.addEventListener('availability', (event) => onChange(JSON.parse((event as MessageEvent).data)));
    return () => source.close();
  }

  garageReviews(garageId: UUID, page = 0, size = 10): Promise<Page<Review>> {
    return this.request('GET', `/api/v1/garages/${garageId}/reviews`, { query: { page, size }, auth: false });
  }

  garagePhotos(garageId: UUID): Promise<GaragePhoto[]> {
    return this.request('GET', `/api/v1/garages/${garageId}/photos`, { auth: false });
  }

  /** URL absoluta para usar en <img src>. */
  photoUrl(photo: GaragePhoto): string {
    return this.baseUrl + photo.url;
  }

  // ----- garajes del propietario -----

  myGarages(): Promise<Garage[]> {
    return this.request('GET', '/api/v1/garages/mine');
  }

  getGarage(garageId: UUID): Promise<Garage> {
    return this.request('GET', `/api/v1/garages/${garageId}`, { auth: false });
  }

  /** Requiere rol OWNER o ADMIN. */
  createGarage(request: GarageRequest): Promise<Garage> {
    return this.request('POST', '/api/v1/garages', { body: request });
  }

  updateGarage(garageId: UUID, request: GarageUpdateRequest): Promise<Garage> {
    return this.request('PUT', `/api/v1/garages/${garageId}`, { body: request });
  }

  changeGarageStatus(garageId: UUID, status: GarageStatus): Promise<Garage> {
    return this.request('PATCH', `/api/v1/garages/${garageId}/status`, { query: { status } });
  }

  /** 409 si el garaje tiene reservas activas. */
  deleteGarage(garageId: UUID): Promise<void> {
    return this.request('DELETE', `/api/v1/garages/${garageId}`);
  }

  listSpots(garageId: UUID, filters: { status?: SpotStatus; vehicleType?: VehicleType } = {}): Promise<ParkingSpot[]> {
    return this.request('GET', `/api/v1/garages/${garageId}/spots`, { query: { ...filters }, auth: false });
  }

  addSpot(garageId: UUID, request: ParkingSpotRequest): Promise<ParkingSpot> {
    return this.request('POST', `/api/v1/garages/${garageId}/spots`, { body: request });
  }

  updateSpot(garageId: UUID, spotId: UUID, request: ParkingSpotRequest): Promise<ParkingSpot> {
    return this.request('PUT', `/api/v1/garages/${garageId}/spots/${spotId}`, { body: request });
  }

  changeSpotStatus(garageId: UUID, spotId: UUID, status: SpotStatus): Promise<ParkingSpot> {
    return this.request('PATCH', `/api/v1/garages/${garageId}/spots/${spotId}/status`, { query: { status } });
  }

  deleteSpot(garageId: UUID, spotId: UUID): Promise<void> {
    return this.request('DELETE', `/api/v1/garages/${garageId}/spots/${spotId}`);
  }

  /** JPG, PNG o WEBP de máximo 5 MB; hasta 8 fotos por garaje. */
  uploadPhoto(garageId: UUID, file: Blob, fileName = 'foto.jpg'): Promise<GaragePhoto> {
    const form = new FormData();
    form.append('file', file, fileName);
    return this.request('POST', `/api/v1/garages/${garageId}/photos`, { body: form });
  }

  deletePhoto(garageId: UUID, photoId: UUID): Promise<void> {
    return this.request('DELETE', `/api/v1/garages/${garageId}/photos/${photoId}`);
  }

  /** Pide la insignia de verificado. Requiere al menos una foto. */
  requestVerification(garageId: UUID): Promise<Garage> {
    return this.request('POST', `/api/v1/garages/${garageId}/verification-request`);
  }

  // ----- reservas -----

  /** Público: precio y disponibilidad antes de reservar. */
  quote(garageId: UUID, vehicleType: VehicleType, startTime: Instant, endTime: Instant): Promise<ReservationQuote> {
    return this.request('GET', `/api/v1/garages/${garageId}/reservation-quote`, {
      query: { vehicleType, startTime, endTime },
      auth: false,
    });
  }

  /** Requiere rol DRIVER. La reserva queda PENDING_PAYMENT hasta paymentDeadline. */
  createReservation(request: ReservationRequest): Promise<Reservation> {
    return this.request('POST', '/api/v1/reservations', { body: request });
  }

  myReservations(params: { status?: ReservationStatus; page?: number; size?: number } = {}): Promise<Page<Reservation>> {
    return this.request('GET', '/api/v1/reservations/mine', { query: { ...params } });
  }

  /** Panel del propietario. Requiere rol OWNER. */
  ownedReservations(params: OwnedReservationsParams = {}): Promise<Page<Reservation>> {
    return this.request('GET', '/api/v1/reservations/owned', { query: { ...params } });
  }

  getReservation(reservationId: UUID): Promise<Reservation> {
    return this.request('GET', `/api/v1/reservations/${reservationId}`);
  }

  cancelReservation(reservationId: UUID, reason?: string): Promise<Reservation> {
    return this.request('POST', `/api/v1/reservations/${reservationId}/cancel`, { body: { reason } });
  }

  /** Propietario: registra la entrada con el código que muestra el conductor (con o sin "PS-"). */
  checkIn(code: string): Promise<Reservation> {
    return this.request('POST', '/api/v1/reservations/check-in', { body: { code } });
  }

  checkOut(code: string): Promise<Reservation> {
    return this.request('POST', '/api/v1/reservations/check-out', { body: { code } });
  }

  // ----- pagos -----

  /**
   * Crea el pago y devuelve checkoutUrl. Flujo web: window.location.href = payment.checkoutUrl;
   * al volver a redirectUrl, consultar getReservation hasta ver CONFIRMED (el webhook confirma el pago).
   */
  createPayment(reservationId: UUID, redirectUrl?: string): Promise<Payment> {
    return this.request('POST', `/api/v1/reservations/${reservationId}/payments`, { body: { redirectUrl } });
  }

  reservationPayments(reservationId: UUID): Promise<Payment[]> {
    return this.request('GET', `/api/v1/reservations/${reservationId}/payments`);
  }

  getPayment(paymentId: UUID): Promise<Payment> {
    return this.request('GET', `/api/v1/payments/${paymentId}`);
  }

  /** Solo con la pasarela simulada (desarrollo): aprueba o rechaza el pago. */
  simulatePayment(paymentId: UUID, status: 'APPROVED' | 'DECLINED' | 'ERROR'): Promise<Payment> {
    return this.request('POST', `/api/v1/payments/${paymentId}/sandbox-result`, { body: { status } });
  }

  receipt(reservationId: UUID): Promise<Receipt> {
    return this.request('GET', `/api/v1/reservations/${reservationId}/receipt`);
  }

  // ----- reseñas -----

  /** Solo reservas COMPLETED, una por reserva. rating de 1 a 5. */
  reviewReservation(reservationId: UUID, rating: number, comment?: string): Promise<Review> {
    return this.request('POST', `/api/v1/reservations/${reservationId}/review`, { body: { rating, comment } });
  }

  deleteReview(reviewId: UUID): Promise<void> {
    return this.request('DELETE', `/api/v1/reviews/${reviewId}`);
  }

  // ----- notificaciones -----

  notifications(params: { unreadOnly?: boolean; page?: number; size?: number } = {}): Promise<Page<Notification>> {
    return this.request('GET', '/api/v1/notifications', { query: { ...params } });
  }

  unreadCount(): Promise<{ unread: number }> {
    return this.request('GET', '/api/v1/notifications/unread-count');
  }

  markNotificationRead(notificationId: UUID): Promise<Notification> {
    return this.request('PATCH', `/api/v1/notifications/${notificationId}/read`);
  }

  markAllNotificationsRead(): Promise<void> {
    return this.request('POST', '/api/v1/notifications/read-all');
  }

  /** Token de Firebase Cloud Messaging del navegador (getToken del SDK web de Firebase). */
  registerDevice(token: string, platform: DevicePlatform = 'WEB'): Promise<void> {
    return this.request('POST', '/api/v1/notifications/devices', { body: { token, platform } });
  }

  unregisterDevice(token: string): Promise<void> {
    return this.request('DELETE', '/api/v1/notifications/devices', { query: { token } });
  }

  // ----- reportes -----

  ownerReport(params: { from?: LocalDate; to?: LocalDate; garageId?: UUID } = {}): Promise<OwnerReport> {
    return this.request('GET', '/api/v1/reports/owner', { query: { ...params } });
  }

  adminReport(params: { from?: LocalDate; to?: LocalDate } = {}): Promise<AdminReport> {
    return this.request('GET', '/api/v1/reports/admin', { query: { ...params } });
  }

  // ----- administración (rol ADMIN) -----

  adminUsers(params: { q?: string; role?: 'DRIVER' | 'OWNER' | 'ADMIN'; enabled?: boolean; page?: number; size?: number } = {}): Promise<Page<AdminUser>> {
    return this.request('GET', '/api/v1/admin/users', { query: { ...params } });
  }

  adminSetUserEnabled(userId: UUID, enabled: boolean): Promise<AdminUser> {
    return this.request('PATCH', `/api/v1/admin/users/${userId}/status`, { body: { enabled } });
  }

  /** Roles finales de la cuenta (ROLE_USER se conserva siempre). */
  adminSetUserRoles(userId: UUID, roles: Array<'DRIVER' | 'OWNER' | 'ADMIN'>): Promise<AdminUser> {
    return this.request('PUT', `/api/v1/admin/users/${userId}/roles`, { body: { roles } });
  }

  adminGarages(verificationStatus: VerificationStatus = 'PENDING', page = 0, size = 20): Promise<Page<Garage>> {
    return this.request('GET', '/api/v1/admin/garages', { query: { verificationStatus, page, size } });
  }

  adminDecideVerification(garageId: UUID, approved: boolean, notes?: string): Promise<Garage> {
    return this.request('POST', `/api/v1/admin/garages/${garageId}/verification`, { body: { approved, notes } });
  }

  // ---------- infraestructura ----------

  private saveSession(tokens: TokenPair): TokenPair {
    this.tokens.set(tokens);
    return tokens;
  }

  private async request<T>(method: string, path: string, options: RequestOptions = {}, retried = false): Promise<T> {
    const useAuth = options.auth !== false;
    const headers: Record<string, string> = { Accept: 'application/json' };
    const session = this.tokens.get();
    if (useAuth && session) headers.Authorization = `Bearer ${session.accessToken}`;

    let body: BodyInit | undefined;
    if (options.body instanceof FormData) {
      body = options.body;
    } else if (options.body !== undefined) {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(options.body);
    }

    const response = await fetch(this.baseUrl + path + toQueryString(options.query), { method, headers, body });

    if (response.status === 401 && useAuth && session && !retried) {
      if (await this.refresh()) return this.request<T>(method, path, options, true);
      this.tokens.set(null);
      this.onSessionExpired?.();
    }
    if (!response.ok) {
      let errorBody: ApiErrorBody | null = null;
      try {
        errorBody = (await response.json()) as ApiErrorBody;
      } catch {
        // Respuesta sin JSON (por ejemplo un proxy caído).
      }
      throw new ApiError(response.status, errorBody);
    }
    if (response.status === 204 || response.status === 202) return undefined as T;
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }

  /** Renueva el access token; varias llamadas simultáneas comparten una sola renovación. */
  private refresh(): Promise<boolean> {
    if (!this.refreshing) {
      this.refreshing = (async () => {
        const refreshToken = this.tokens.get()?.refreshToken;
        if (!refreshToken) return false;
        try {
          const response = await fetch(this.baseUrl + '/api/v1/auth/refresh', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
            body: JSON.stringify({ refreshToken }),
          });
          if (!response.ok) return false;
          this.tokens.set((await response.json()) as TokenPair);
          return true;
        } catch {
          return false;
        }
      })().finally(() => {
        this.refreshing = null;
      });
    }
    return this.refreshing;
  }
}

function toQueryString(query?: Query): string {
  if (!query) return '';
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.append(key, String(value));
  });
  const text = params.toString();
  return text ? `?${text}` : '';
}
