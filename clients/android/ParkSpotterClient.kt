package co.parkspotter.api

import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Dónde se guarda la sesión. Implementarlo con EncryptedSharedPreferences o DataStore. */
interface TokenStore {
    fun get(): TokenPair?
    fun set(tokens: TokenPair?)
}

/**
 * Arma Retrofit con:
 * - AuthInterceptor: agrega "Authorization: Bearer <accessToken>" si hay sesión.
 * - TokenAuthenticator: ante un 401 renueva el token con el refresh token y repite la petición una vez.
 *   Si no se puede renovar, borra la sesión y llama a onSessionExpired (ir al login).
 *
 * Uso (por ejemplo en un módulo de Hilt o en la clase Application):
 *   val client = ParkSpotterClient("https://api.parkspotter.co/", tokenStore) { goToLogin() }
 *   val api = client.api
 *   val tokens = api.login(LoginRequest(email, password)); tokenStore.set(tokens)
 *
 * En el emulador, el backend local es "http://10.0.2.2:8080/".
 */
class ParkSpotterClient(
    private val baseUrl: String,
    private val tokenStore: TokenStore,
    debug: Boolean = false,
    private val onSessionExpired: () -> Unit = {},
) {
    private val gson = Gson()

    /** Cliente sin autenticación, solo para renovar el token (evita ciclos con el Authenticator). */
    private val refreshApi: ParkSpotterApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(ParkSpotterApi::class.java)

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor())
        .authenticator(TokenAuthenticator())
        .apply {
            if (debug) addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        }
        .build()

    val api: ParkSpotterApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(ParkSpotterApi::class.java)

    /** Mensaje en español que manda el backend (para mostrar en un Snackbar o Toast). */
    fun errorMessage(error: Throwable): String {
        if (error is HttpException) {
            val body = error.response()?.errorBody()?.string()
            val parsed = runCatching { gson.fromJson(body, ApiErrorBody::class.java) }.getOrNull()
            return parsed?.message ?: "Error ${error.code()}"
        }
        return error.message ?: "Error de conexión"
    }

    /**
     * Disponibilidad en tiempo real (SSE). Sin garageId escucha todos los garajes (para el mapa).
     * Cerrar con eventSource.cancel() en onStop/onDestroy. Si la conexión se cae, volver a llamar y pedir /availability.
     */
    fun subscribeAvailability(garageId: String? = null, onChange: (Availability) -> Unit, onFailure: (Throwable?) -> Unit = {}): EventSource {
        val path = if (garageId == null) "api/v1/garages/availability/stream" else "api/v1/garages/$garageId/availability/stream"
        val streamClient = okHttp.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/" + path).header("Accept", "text/event-stream").build()
        return EventSources.createFactory(streamClient).newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (type == "availability") onChange(gson.fromJson(data, Availability::class.java))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) = onFailure(t)
        })
    }

    private inner class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val token = tokenStore.get()?.accessToken ?: return chain.proceed(chain.request())
            return chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token").build())
        }
    }

    private inner class TokenAuthenticator : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            // Solo un reintento por petición.
            if (response.request.header("X-Retry-After-Refresh") != null) return null
            val sentToken = response.request.header("Authorization")?.removePrefix("Bearer ") ?: return null
            synchronized(this) {
                val current = tokenStore.get() ?: return null
                // Otra petición ya renovó el token mientras esta esperaba.
                if (current.accessToken != sentToken) {
                    return retry(response.request, current.accessToken)
                }
                val renewed = runCatching {
                    kotlinx.coroutines.runBlocking { refreshApi.refresh(RefreshRequest(current.refreshToken)) }
                }.getOrNull()
                if (renewed == null) {
                    tokenStore.set(null)
                    onSessionExpired()
                    return null
                }
                tokenStore.set(renewed)
                return retry(response.request, renewed.accessToken)
            }
        }

        private fun retry(request: Request, token: String): Request =
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("X-Retry-After-Refresh", "1")
                .build()
    }
}
