package co.parkspotter.api

/*
 * Modelos de la API de ParkSpotter para Android (Retrofit + Gson).
 * Fechas: String ISO-8601 en UTC (ej. "2026-05-10T14:00:00Z"); convertir con java.time.Instant.parse().
 * Montos: Double (pesos colombianos, sin decimales en la práctica).
 * Referencia completa de endpoints: docs/API-Referencia.md
 */

// ---------- comunes ----------

data class Page<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)

/** Forma de todos los errores de la API; details trae el error de cada campo en las validaciones (400). */
data class ApiErrorBody(
    val timestamp: String?,
    val status: Int,
    val error: String?,
    val message: String?,
    val path: String?,
    val details: Map<String, Any?>?,
)

// ---------- enums (los nombres coinciden con el JSON) ----------

enum class AccountType { DRIVER, OWNER }
enum class VehicleType { CAR, MOTORCYCLE, BICYCLE, TRUCK, ELECTRIC, DISABLED }
enum class GarageStatus { ACTIVE, INACTIVE, SUSPENDED }
enum class GarageAvailability { AVAILABLE, FEW_SPOTS, FULL, CLOSED }
enum class SpotStatus { AVAILABLE, OCCUPIED, RESERVED, OUT_OF_SERVICE }
enum class VerificationStatus { UNVERIFIED, PENDING, VERIFIED, REJECTED }
enum class GarageSortOption { DISTANCE, PRICE_ASC, PRICE_DESC, AVAILABILITY, NAME, NEWEST, RATING }
enum class ReservationStatus { PENDING_PAYMENT, CONFIRMED, CHECKED_IN, COMPLETED, CANCELLED, EXPIRED, NO_SHOW }
enum class PaymentStatus { PENDING, APPROVED, DECLINED, VOIDED, ERROR, REFUND_PENDING }
enum class DevicePlatform { ANDROID, IOS, WEB }
enum class ManagedRole { DRIVER, OWNER, ADMIN }
enum class NotificationType {
    RESERVATION_CREATED, RESERVATION_CONFIRMED, RESERVATION_CANCELLED, RESERVATION_EXPIRED,
    RESERVATION_NO_SHOW, RESERVATION_REMINDER, CHECK_IN, CHECK_OUT, PAYMENT_DECLINED,
    NEW_REVIEW, GARAGE_VERIFIED, GARAGE_REJECTED, ACCOUNT,
}

// ---------- autenticación y perfil ----------

data class RegisterRequest(
    val email: String,
    val password: String,
    val role: AccountType = AccountType.DRIVER,
    val fullName: String? = null,
    val phone: String? = null,
)

data class LoginRequest(val email: String, val password: String)
data class RefreshRequest(val refreshToken: String)
data class ForgotPasswordRequest(val email: String)
data class ResetPasswordRequest(val token: String, val newPassword: String)
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

data class TokenPair(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresInSeconds: Long,
    /** ROLE_USER, ROLE_DRIVER, ROLE_OWNER y/o ROLE_ADMIN. */
    val roles: List<String>,
)

data class User(
    val id: String,
    val email: String,
    val fullName: String?,
    val phone: String?,
    val roles: List<String>,
    val enabled: Boolean,
    val createdAt: String,
)

/** null = no cambia; "" = borra el dato. */
data class UpdateProfileRequest(val fullName: String? = null, val phone: String? = null)
data class AddRoleRequest(val role: AccountType)
data class DeleteAccountRequest(val password: String)

// ---------- garajes ----------

data class GarageRequest(
    val name: String,
    val description: String? = null,
    val phone: String? = null,
    val addressLine: String,
    val city: String,
    val state: String? = null,
    val country: String? = null,
    val postalCode: String? = null,
    val latitude: Double,
    val longitude: Double,
    val totalSpots: Int,
    val pricePerHour: Double,
    val open24Hours: Boolean,
    /** "07:00" si open24Hours es false. */
    val openingTime: String? = null,
    val closingTime: String? = null,
)

data class Garage(
    val id: String,
    val name: String,
    val description: String?,
    val phone: String?,
    val ownerId: String,
    val ownerEmail: String?,
    val addressLine: String,
    val city: String,
    val state: String?,
    val country: String?,
    val postalCode: String?,
    val latitude: Double,
    val longitude: Double,
    val totalSpots: Int,
    val availableSpots: Int,
    val occupiedSpots: Int,
    val reservedSpots: Int,
    val outOfServiceSpots: Int,
    val pricePerHour: Double,
    val open24Hours: Boolean,
    val openingTime: String?,
    val closingTime: String?,
    val status: GarageStatus,
    val availability: GarageAvailability,
    val verificationStatus: VerificationStatus,
    val verified: Boolean,
    val averageRating: Double,
    val ratingCount: Int,
    val createdAt: String,
    val updatedAt: String,
)

data class ParkingSpot(
    val id: String,
    val garageId: String,
    val code: String,
    val floor: Int,
    val vehicleType: VehicleType,
    val status: SpotStatus,
    val updatedAt: String,
)

data class ParkingSpotRequest(val code: String, val floor: Int, val vehicleType: VehicleType)

data class GaragePhoto(
    val id: String,
    val garageId: String,
    val contentType: String,
    val sizeBytes: Int,
    /** Ruta relativa: BASE_URL.trimEnd('/') + url (pública, sirve para Coil o Glide). */
    val url: String,
    val createdAt: String,
)

data class Availability(
    val garageId: String,
    val status: GarageStatus,
    val availability: GarageAvailability,
    val openNow: Boolean,
    val totalSpots: Int,
    val availableSpots: Int,
    val occupiedSpots: Int,
    val reservedSpots: Int,
    val outOfServiceSpots: Int,
    val timestamp: String,
)

data class VehicleTypeAvailability(
    val vehicleType: VehicleType,
    val totalSpots: Int,
    val availableSpots: Int,
    val occupiedSpots: Int,
    val reservedSpots: Int,
    val outOfServiceSpots: Int,
)

data class GarageDetail(
    val id: String,
    val name: String,
    val description: String?,
    val phone: String?,
    val addressLine: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val pricePerHour: Double,
    val open24Hours: Boolean,
    val openingTime: String?,
    val closingTime: String?,
    val availability: Availability,
    val availabilityByVehicleType: List<VehicleTypeAvailability>,
    val floors: List<Int>,
    val spots: List<ParkingSpot>,
    val verificationStatus: VerificationStatus,
    val verified: Boolean,
    val averageRating: Double,
    val ratingCount: Int,
    val photos: List<GaragePhoto>,
    val createdAt: String,
    val updatedAt: String,
)

data class GarageSummary(
    val id: String,
    val name: String,
    val addressLine: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val pricePerHour: Double,
    val totalSpots: Int,
    val availableSpots: Int,
    val occupiedSpots: Int,
    val reservedSpots: Int,
    val availability: GarageAvailability,
    val open24Hours: Boolean,
    val openingTime: String?,
    val closingTime: String?,
    val openNow: Boolean,
    val distanceKm: Double?,
    val verified: Boolean,
    val averageRating: Double,
    val ratingCount: Int,
)

data class GarageMarker(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val pricePerHour: Double,
    val availableSpots: Int,
    val totalSpots: Int,
    val availability: GarageAvailability,
    val openNow: Boolean,
    val verified: Boolean,
    val averageRating: Double,
)

data class GarageMap(val markers: List<GarageMarker>, val count: Int, val truncated: Boolean)

// ---------- reservas y pagos ----------

data class ReservationQuote(
    val garageId: String,
    val vehicleType: VehicleType,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Long,
    val pricePerHour: Double,
    val totalAmount: Double,
    val currency: String,
    val availableSpots: Int,
    val available: Boolean,
    val unavailableReason: String?,
)

data class ReservationRequest(
    val garageId: String,
    val vehicleType: VehicleType,
    val vehiclePlate: String,
    val startTime: String,
    val endTime: String,
    val spotId: String? = null,
    val searchStartedAt: String? = null,
)

data class Reservation(
    val id: String,
    /** Código de entrada y salida, ej. "PS-7K3MQ2" (mostrarlo como QR o texto). */
    val code: String,
    val status: ReservationStatus,
    val garageId: String,
    val garageName: String,
    val garageAddress: String,
    val garageLatitude: Double,
    val garageLongitude: Double,
    val spotId: String?,
    val spotCode: String?,
    val spotFloor: Int?,
    val driverId: String,
    val driverName: String,
    val vehicleType: VehicleType,
    val vehiclePlate: String,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Long,
    val pricePerHour: Double,
    val totalAmount: Double,
    val overtimeMinutes: Int,
    val overtimeAmount: Double,
    val currency: String,
    val paymentDeadline: String,
    val paid: Boolean,
    val paidAt: String?,
    val checkInAt: String?,
    val checkOutAt: String?,
    val cancelledAt: String?,
    val cancellationReason: String?,
    val cancellable: Boolean,
    val createdAt: String,
)

data class CancelReservationRequest(val reason: String? = null)
data class ReservationCodeRequest(val code: String)

/** redirectUrl para la app: un deep link, ej. "parkspotter://pagos/resultado". */
data class CreatePaymentRequest(val redirectUrl: String? = null)
data class SandboxResultRequest(val status: PaymentStatus)

data class Payment(
    val id: String,
    val reservationId: String,
    val reference: String,
    val amount: Double,
    val amountInCents: Long,
    val currency: String,
    val status: PaymentStatus,
    val provider: String,
    val paymentMethod: String?,
    val checkoutUrl: String?,
    val redirectUrl: String?,
    val publicKey: String?,
    val integritySignature: String?,
    val createdAt: String,
    val approvedAt: String?,
)

data class Receipt(
    val receiptNumber: String,
    val issuedAt: String,
    val reservationId: String,
    val reservationCode: String,
    val reservationStatus: ReservationStatus,
    val customerName: String,
    val customerEmail: String,
    val garageName: String,
    val garageAddress: String,
    val spotCode: String?,
    val vehicleType: VehicleType,
    val vehiclePlate: String,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Long,
    val pricePerHour: Double,
    val amountPaid: Double,
    val currency: String,
    val paymentProvider: String,
    val paymentMethod: String?,
    val paymentReference: String,
    val providerTransactionId: String?,
    val paidAt: String?,
    val paymentStatus: PaymentStatus,
)

// ---------- reseñas, notificaciones, admin y reportes ----------

data class ReviewRequest(val rating: Int, val comment: String? = null)

data class Review(
    val id: String,
    val garageId: String,
    val reservationId: String,
    val rating: Int,
    val comment: String?,
    val authorName: String,
    val createdAt: String,
)

data class Notification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val referenceId: String?,
    val read: Boolean,
    val readAt: String?,
    val createdAt: String,
)

data class UnreadCount(val unread: Long)
data class DeviceTokenRequest(val token: String, val platform: DevicePlatform = DevicePlatform.ANDROID)

data class AdminUser(
    val id: String,
    val email: String,
    val fullName: String?,
    val phone: String?,
    val roles: List<String>,
    val enabled: Boolean,
    val createdAt: String,
)

data class UserStatusRequest(val enabled: Boolean)
data class UserRolesRequest(val roles: Set<ManagedRole>)
data class VerificationDecisionRequest(val approved: Boolean, val notes: String? = null)

data class ReportSummary(
    val totalReservations: Long,
    val paidReservations: Long,
    val completed: Long,
    val cancelled: Long,
    val expired: Long,
    val noShow: Long,
    val revenue: Double,
    val overtimeRevenue: Double,
    val occupancyRate: Double,
    val averageRating: Double,
)

data class GarageReport(
    val garageId: String,
    val name: String,
    val totalSpots: Int,
    val reservations: Long,
    val completed: Long,
    val cancelled: Long,
    val noShow: Long,
    val revenue: Double,
    val occupancyRate: Double,
    val averageRating: Double,
    val ratingCount: Int,
)

data class DailyPoint(val date: String, val reservations: Long, val revenue: Double)

data class OwnerReport(
    val from: String,
    val to: String,
    val currency: String,
    val summary: ReportSummary,
    val garages: List<GarageReport>,
    val daily: List<DailyPoint>,
)

data class AdminReport(
    val from: String,
    val to: String,
    val currency: String,
    val users: Users,
    val garages: Garages,
    val summary: ReportSummary,
    val averageSearchToReservationSeconds: Double?,
    val topGarages: List<GarageReport>,
    val daily: List<DailyPoint>,
) {
    data class Users(val total: Long, val drivers: Long, val owners: Long, val admins: Long, val disabled: Long, val newInRange: Long)
    data class Garages(val total: Long, val active: Long, val verified: Long, val pendingVerification: Long)
}
