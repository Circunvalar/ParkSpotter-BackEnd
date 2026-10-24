# Cliente Android (Kotlin + Retrofit)

| Archivo | Contenido |
|---|---|
| [`Models.kt`](Models.kt) | Modelos de petición y respuesta (data classes) y enums. |
| [`ParkSpotterApi.kt`](ParkSpotterApi.kt) | Interfaz Retrofit con todos los endpoints (funciones `suspend`). |
| [`ParkSpotterClient.kt`](ParkSpotterClient.kt) | Arma Retrofit y OkHttp: token en cada petición, renovación automática ante un 401, mensajes de error y tiempo real (SSE). |

## 1. Dependencias (`app/build.gradle.kts`)

```kotlin
dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06") // para guardar la sesión cifrada
}
```

Permiso en `AndroidManifest.xml`: `<uses-permission android:name="android.permission.INTERNET" />`. Para probar contra el backend local por HTTP, agregar `android:usesCleartextTraffic="true"` solo en el build de debug.

## 2. Copiar los archivos

Copiar los tres `.kt` en `app/src/main/java/co/parkspotter/api/` (o cambiar el `package` por el del proyecto).

## 3. Guardar la sesión

```kotlin
class EncryptedTokenStore(context: Context) : TokenStore {
    private val prefs = EncryptedSharedPreferences.create(
        context, "parkspotter_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val gson = Gson()

    override fun get(): TokenPair? = prefs.getString("tokens", null)?.let { gson.fromJson(it, TokenPair::class.java) }
    override fun set(tokens: TokenPair?) = prefs.edit().apply {
        if (tokens == null) remove("tokens") else putString("tokens", gson.toJson(tokens))
    }.apply()
}
```

## 4. Crear el cliente una vez

```kotlin
class App : Application() {
    lateinit var client: ParkSpotterClient

    override fun onCreate() {
        super.onCreate()
        client = ParkSpotterClient(
            baseUrl = BuildConfig.API_URL,       // "http://10.0.2.2:8080/" en el emulador; terminar en "/"
            tokenStore = EncryptedTokenStore(this),
            debug = BuildConfig.DEBUG,
        ) { /* sesión vencida: abrir LoginActivity */ }
    }
}
```

`BuildConfig.API_URL` se define con `buildConfigField("String", "API_URL", "\"https://api.tu-dominio.com/\"")` en cada `buildType`.

## 5. Ejemplos (desde un ViewModel)

```kotlin
private val api get() = app.client.api

fun login(email: String, password: String) = viewModelScope.launch {
    try {
        val session = api.login(LoginRequest(email, password))
        tokenStore.set(session)
        if ("ROLE_OWNER" in session.roles) goToOwnerPanel() else goToSearch()
    } catch (e: Exception) {
        error.value = app.client.errorMessage(e) // mensaje en español del backend
    }
}

fun search(lat: Double, lng: Double) = viewModelScope.launch {
    val page = api.searchGarages(mapOf("lat" to "$lat", "lng" to "$lng", "onlyAvailable" to "true", "minRating" to "4"))
    garages.value = page.content
}

fun reserve(garageId: String, start: Instant, end: Instant) = viewModelScope.launch {
    val quote = api.quote(garageId, VehicleType.CAR, start.toString(), end.toString())
    if (!quote.available) { error.value = quote.unavailableReason; return@launch }
    val reservation = api.createReservation(
        ReservationRequest(garageId, VehicleType.CAR, "ABC123", start.toString(), end.toString()),
    )
    val payment = api.createPayment(reservation.id, CreatePaymentRequest("parkspotter://pagos/resultado?reserva=${reservation.id}"))
    openCheckout(payment.checkoutUrl!!) // Custom Tabs
}
```

### Pago con Custom Tabs y deep link

1. Abrir `checkoutUrl` con `CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))`.
2. Declarar el deep link en la actividad que recibe el resultado:

   ```xml
   <intent-filter>
       <action android:name="android.intent.action.VIEW" />
       <category android:name="android.intent.category.DEFAULT" />
       <category android:name="android.intent.category.BROWSABLE" />
       <data android:scheme="parkspotter" android:host="pagos" />
   </intent-filter>
   ```

3. Al volver, consultar `api.getReservation(id)` cada 3 segundos (máximo un minuto) hasta ver `CONFIRMED`.

El prefijo `parkspotter://` ya está permitido en el backend (`PAYMENTS_REDIRECT_PREFIXES`). Con la pasarela simulada se puede aprobar con `api.simulatePayment(payment.id, SandboxResultRequest(PaymentStatus.APPROVED))`.

### Disponibilidad en tiempo real

```kotlin
private var stream: EventSource? = null

override fun onStart() {
    super.onStart()
    stream = app.client.subscribeAvailability(garageId = null, onChange = { change ->
        runOnUiThread { adapter.update(change.garageId, change.availableSpots, change.availability) }
    })
}

override fun onStop() {
    stream?.cancel()
    super.onStop()
}
```

### Notificaciones push (Firebase)

1. Crear el proyecto en Firebase, agregar la app Android y bajar `google-services.json`.
2. Al iniciar sesión y en `FirebaseMessagingService.onNewToken(token)`: `api.registerDevice(DeviceTokenRequest(token))`.
3. Al cerrar sesión: `api.unregisterDevice(token)` y luego `api.logout(RefreshRequest(refreshToken))`.
4. En `onMessageReceived`, `message.data["type"]` y `message.data["referenceId"]` dicen qué pantalla abrir.

El backend envía los push solo si tiene las credenciales de Firebase (`FCM_*`, ver la guía de despliegue). Sin ellas, la bandeja (`api.notifications()`) funciona igual.

### Fotos

```kotlin
val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
val part = MultipartBody.Part.createFormData("file", "foto.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
api.uploadPhoto(garageId, part)

// Mostrar con Coil:
AsyncImage(model = BuildConfig.API_URL.trimEnd('/') + photo.url, contentDescription = null)
```

### Código de reserva como QR

`reservation.code` (ej. `PS-7K3MQ2`) se puede mostrar como QR con ZXing (`BarcodeEncoder().encodeBitmap(code, BarcodeFormat.QR_CODE, 600, 600)`). El propietario lo escanea o lo escribe y llama `api.checkIn(ReservationCodeRequest(code))`.

## 6. Fechas

Enviar `Instant.toString()` (UTC, ej. `2026-05-10T14:00:00Z`). Para mostrar: `Instant.parse(s).atZone(ZoneId.of("America/Bogota"))`. Con `minSdk` menor a 26, activar *core library desugaring*.

> Los tres archivos se compilaron con Kotlin 2.0 contra las versiones de la sección 1. Declaren `okhttp` 4.12 explícitamente: Retrofit 2.11 trae OkHttp 3 como dependencia y `okhttp-sse` necesita la 4.
