package co.parkspotter.api

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * Endpoints de ParkSpotter para Retrofit (funciones suspend, usar desde corrutinas).
 * Las rutas públicas funcionan sin sesión; el resto necesita el header Authorization que agrega
 * AuthInterceptor (ver ParkSpotterClient.kt). Errores: retrofit2.HttpException con el cuerpo ApiErrorBody.
 */
interface ParkSpotterApi {

    // ---------- autenticación ----------

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): TokenPair

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): TokenPair

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): TokenPair

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body request: RefreshRequest): Response<Unit>

    /** 202 siempre (exista o no el correo). */
    @POST("api/v1/auth/password/forgot")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<Unit>

    /** El token llega por correo (enlace o código para pegar en la app). */
    @POST("api/v1/auth/password/reset")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<Unit>

    @PATCH("api/v1/auth/password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<Unit>

    // ---------- perfil ----------

    @GET("api/v1/users/me")
    suspend fun me(): User

    @PATCH("api/v1/users/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): User

    @POST("api/v1/users/me/roles")
    suspend fun addRole(@Body request: AddRoleRequest): User

    @POST("api/v1/users/me/delete")
    suspend fun deleteAccount(@Body request: DeleteAccountRequest): Response<Unit>

    // ---------- búsqueda y garajes (públicos) ----------

    /**
     * Filtros como mapa para no repetir 17 parámetros, ej.
     * mapOf("lat" to "4.71", "lng" to "-74.07", "onlyAvailable" to "true", "minRating" to "4").
     */
    @GET("api/v1/garages/search")
    suspend fun searchGarages(@QueryMap filters: Map<String, String> = emptyMap()): Page<GarageSummary>

    @GET("api/v1/garages/map")
    suspend fun mapMarkers(
        @Query("minLat") minLat: Double,
        @Query("minLng") minLng: Double,
        @Query("maxLat") maxLat: Double,
        @Query("maxLng") maxLng: Double,
        @QueryMap filters: Map<String, String> = emptyMap(),
    ): GarageMap

    @GET("api/v1/garages/{id}/detail")
    suspend fun garageDetail(@Path("id") garageId: String, @Query("includeSpots") includeSpots: Boolean = false): GarageDetail

    @GET("api/v1/garages/{id}/availability")
    suspend fun availability(@Path("id") garageId: String): Availability

    @GET("api/v1/garages/{id}/reviews")
    suspend fun garageReviews(@Path("id") garageId: String, @Query("page") page: Int = 0, @Query("size") size: Int = 10): Page<Review>

    @GET("api/v1/garages/{id}/photos")
    suspend fun garagePhotos(@Path("id") garageId: String): List<GaragePhoto>

    @GET("api/v1/garages/{id}/reservation-quote")
    suspend fun quote(
        @Path("id") garageId: String,
        @Query("vehicleType") vehicleType: VehicleType,
        @Query("startTime") startTime: String,
        @Query("endTime") endTime: String,
    ): ReservationQuote

    // ---------- garajes del propietario ----------

    @GET("api/v1/garages/mine")
    suspend fun myGarages(): List<Garage>

    @GET("api/v1/garages/{id}")
    suspend fun getGarage(@Path("id") garageId: String): Garage

    @POST("api/v1/garages")
    suspend fun createGarage(@Body request: GarageRequest): Garage

    @PUT("api/v1/garages/{id}")
    suspend fun updateGarage(@Path("id") garageId: String, @Body request: GarageRequest): Garage

    @PATCH("api/v1/garages/{id}/status")
    suspend fun changeGarageStatus(@Path("id") garageId: String, @Query("status") status: GarageStatus): Garage

    @DELETE("api/v1/garages/{id}")
    suspend fun deleteGarage(@Path("id") garageId: String): Response<Unit>

    @GET("api/v1/garages/{id}/spots")
    suspend fun listSpots(
        @Path("id") garageId: String,
        @Query("status") status: SpotStatus? = null,
        @Query("vehicleType") vehicleType: VehicleType? = null,
    ): List<ParkingSpot>

    @POST("api/v1/garages/{id}/spots")
    suspend fun addSpot(@Path("id") garageId: String, @Body request: ParkingSpotRequest): ParkingSpot

    @PUT("api/v1/garages/{id}/spots/{spotId}")
    suspend fun updateSpot(@Path("id") garageId: String, @Path("spotId") spotId: String, @Body request: ParkingSpotRequest): ParkingSpot

    @PATCH("api/v1/garages/{id}/spots/{spotId}/status")
    suspend fun changeSpotStatus(@Path("id") garageId: String, @Path("spotId") spotId: String, @Query("status") status: SpotStatus): ParkingSpot

    @DELETE("api/v1/garages/{id}/spots/{spotId}")
    suspend fun deleteSpot(@Path("id") garageId: String, @Path("spotId") spotId: String): Response<Unit>

    /** Parte "file": MultipartBody.Part.createFormData("file", "foto.jpg", bytes.toRequestBody("image/jpeg".toMediaType())). */
    @Multipart
    @POST("api/v1/garages/{id}/photos")
    suspend fun uploadPhoto(@Path("id") garageId: String, @Part file: MultipartBody.Part): GaragePhoto

    @DELETE("api/v1/garages/{id}/photos/{photoId}")
    suspend fun deletePhoto(@Path("id") garageId: String, @Path("photoId") photoId: String): Response<Unit>

    @POST("api/v1/garages/{id}/verification-request")
    suspend fun requestVerification(@Path("id") garageId: String): Garage

    // ---------- reservas ----------

    @POST("api/v1/reservations")
    suspend fun createReservation(@Body request: ReservationRequest): Reservation

    @GET("api/v1/reservations/mine")
    suspend fun myReservations(
        @Query("status") status: ReservationStatus? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Page<Reservation>

    /** date en formato "2026-05-10" (día en hora de Bogotá). */
    @GET("api/v1/reservations/owned")
    suspend fun ownedReservations(
        @Query("date") date: String? = null,
        @Query("garageId") garageId: String? = null,
        @Query("status") status: ReservationStatus? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
    ): Page<Reservation>

    @GET("api/v1/reservations/{id}")
    suspend fun getReservation(@Path("id") reservationId: String): Reservation

    @POST("api/v1/reservations/{id}/cancel")
    suspend fun cancelReservation(@Path("id") reservationId: String, @Body request: CancelReservationRequest = CancelReservationRequest()): Reservation

    @POST("api/v1/reservations/check-in")
    suspend fun checkIn(@Body request: ReservationCodeRequest): Reservation

    @POST("api/v1/reservations/check-out")
    suspend fun checkOut(@Body request: ReservationCodeRequest): Reservation

    // ---------- pagos ----------

    @POST("api/v1/reservations/{id}/payments")
    suspend fun createPayment(@Path("id") reservationId: String, @Body request: CreatePaymentRequest = CreatePaymentRequest()): Payment

    @GET("api/v1/reservations/{id}/payments")
    suspend fun reservationPayments(@Path("id") reservationId: String): List<Payment>

    @GET("api/v1/payments/{id}")
    suspend fun getPayment(@Path("id") paymentId: String): Payment

    /** Solo pasarela simulada (desarrollo). */
    @POST("api/v1/payments/{id}/sandbox-result")
    suspend fun simulatePayment(@Path("id") paymentId: String, @Body request: SandboxResultRequest): Payment

    @GET("api/v1/reservations/{id}/receipt")
    suspend fun receipt(@Path("id") reservationId: String): Receipt

    // ---------- reseñas ----------

    @POST("api/v1/reservations/{id}/review")
    suspend fun reviewReservation(@Path("id") reservationId: String, @Body request: ReviewRequest): Review

    @DELETE("api/v1/reviews/{id}")
    suspend fun deleteReview(@Path("id") reviewId: String): Response<Unit>

    // ---------- notificaciones ----------

    @GET("api/v1/notifications")
    suspend fun notifications(
        @Query("unreadOnly") unreadOnly: Boolean = false,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Page<Notification>

    @GET("api/v1/notifications/unread-count")
    suspend fun unreadCount(): UnreadCount

    @PATCH("api/v1/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") notificationId: String): Notification

    @POST("api/v1/notifications/read-all")
    suspend fun markAllNotificationsRead(): Response<Unit>

    /** Token de FirebaseMessaging.getInstance().token; llamarlo al iniciar sesión y en onNewToken. */
    @POST("api/v1/notifications/devices")
    suspend fun registerDevice(@Body request: DeviceTokenRequest): Response<Unit>

    @DELETE("api/v1/notifications/devices")
    suspend fun unregisterDevice(@Query("token") token: String): Response<Unit>

    // ---------- reportes ----------

    @GET("api/v1/reports/owner")
    suspend fun ownerReport(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("garageId") garageId: String? = null,
    ): OwnerReport

    @GET("api/v1/reports/admin")
    suspend fun adminReport(@Query("from") from: String? = null, @Query("to") to: String? = null): AdminReport

    // ---------- administración (rol ADMIN) ----------

    @GET("api/v1/admin/users")
    suspend fun adminUsers(
        @Query("q") q: String? = null,
        @Query("role") role: ManagedRole? = null,
        @Query("enabled") enabled: Boolean? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Page<AdminUser>

    @PATCH("api/v1/admin/users/{id}/status")
    suspend fun adminSetUserEnabled(@Path("id") userId: String, @Body request: UserStatusRequest): AdminUser

    @PUT("api/v1/admin/users/{id}/roles")
    suspend fun adminSetUserRoles(@Path("id") userId: String, @Body request: UserRolesRequest): AdminUser

    @GET("api/v1/admin/garages")
    suspend fun adminGarages(
        @Query("verificationStatus") verificationStatus: VerificationStatus = VerificationStatus.PENDING,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Page<Garage>

    @POST("api/v1/admin/garages/{id}/verification")
    suspend fun adminDecideVerification(@Path("id") garageId: String, @Body request: VerificationDecisionRequest): Garage
}
