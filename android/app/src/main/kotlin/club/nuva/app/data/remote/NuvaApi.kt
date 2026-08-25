package club.nuva.app.data.remote

import android.util.Log
import club.nuva.app.BuildConfig
import club.nuva.app.data.local.SessionStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * The only place in the app that talks HTTP.
 *
 * Access tokens are attached and refreshed automatically by the Auth plugin:
 * when a call comes back 401, Ktor calls the refresh endpoint once, stores the
 * new pair and replays the original request. Screens never see a 401 caused by
 * a merely expired token.
 */
class NuvaApi(
    private val sessionStore: SessionStore,
    val baseUrl: String = BuildConfig.API_BASE_URL,
) {
    private val json = Json {
        ignoreUnknownKeys = true      // server may add fields; old apps keep working
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }

    /**
     * Bare client used only to refresh tokens. It has no Auth plugin, which
     * makes a refresh loop structurally impossible.
     */
    private val refreshClient: HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 15.seconds.inWholeMilliseconds
            requestTimeoutMillis = 20.seconds.inWholeMilliseconds
        }
        defaultRequest { contentType(ContentType.Application.Json) }
    }

    val client: HttpClient = HttpClient(OkHttp) {
        expectSuccess = false

        install(ContentNegotiation) { json(json) }

        install(HttpTimeout) {
            connectTimeoutMillis = 15.seconds.inWholeMilliseconds
            requestTimeoutMillis = 30.seconds.inWholeMilliseconds
            socketTimeoutMillis = 30.seconds.inWholeMilliseconds
        }

        install(WebSockets) {
            pingInterval = 30.seconds.inWholeMilliseconds
        }

        install(HttpRequestRetry) {
            // Only idempotent GETs are retried automatically. Never replay a
            // POST: it could create a second account or a duplicate message.
            maxRetries = 2
            retryIf { request, response ->
                request.method.value == "GET" && response.status.value >= 500
            }
            retryOnExceptionIf { request, cause ->
                request.method.value == "GET" && cause is IOException
            }
            exponentialDelay(base = 2.0, maxDelayMs = 8_000L)
        }

        install(Auth) {
            bearer {
                loadTokens {
                    val access = sessionStore.currentAccessToken() ?: return@loadTokens null
                    BearerTokens(access, sessionStore.currentRefreshToken().orEmpty())
                }
                refreshTokens {
                    val refresh = sessionStore.currentRefreshToken() ?: return@refreshTokens null
                    val response = try {
                        refreshClient.post("$baseUrl/$API_PREFIX/auth/refresh") {
                            setBody(RefreshRequestDto(refresh))
                        }
                    } catch (t: Throwable) {
                        // Offline: keep the session, the next call will retry.
                        return@refreshTokens null
                    }
                    if (!response.status.isSuccess()) {
                        // Refresh token is dead: the session is over for good.
                        sessionStore.clear()
                        return@refreshTokens null
                    }
                    val fresh = response.body<AuthResponseDto>()
                    sessionStore.updateTokens(fresh.accessToken, fresh.refreshToken)
                    BearerTokens(fresh.accessToken, fresh.refreshToken)
                }
                sendWithoutRequest { request ->
                    // Auth endpoints must not carry a stale token.
                    // `buildString()` is a stable member of URLBuilder in every
                    // Ktor 2.x/3.x release; `encodedPath` is not.
                    !request.url.buildString().contains("/$API_PREFIX/auth/")
                }
            }
        }

        if (BuildConfig.VERBOSE_NETWORK_LOG) {
            install(Logging) {
                level = LogLevel.HEADERS
                logger = object : Logger {
                    // Block body on purpose: Log.d returns Int, and an
                    // expression body would make this override return Int
                    // instead of Unit.
                    override fun log(message: String) {
                        Log.d("NuvaHttp", message)
                    }
                }
            }
        }

        defaultRequest {
            contentType(ContentType.Application.Json)
            header("X-Nuva-Client", "android/${BuildConfig.VERSION_NAME}")
        }

        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, _ ->
                when (cause) {
                    is ApiException -> throw cause
                    is IOException -> throw ApiException.network(cause)
                    else -> throw ApiException.unexpected(cause)
                }
            }
        }
    }

    // ---- Auth -------------------------------------------------------------

    suspend fun register(
        username: String,
        displayName: String,
        password: String,
        deviceName: String,
    ): AuthResponseDto = call {
        client.post("$baseUrl/$API_PREFIX/auth/register") {
            setBody(RegisterRequestDto(username, displayName, password, deviceName))
        }
    }

    suspend fun login(
        username: String,
        password: String,
        deviceName: String,
    ): AuthResponseDto = call {
        client.post("$baseUrl/$API_PREFIX/auth/login") {
            setBody(LoginRequestDto(username, password, deviceName))
        }
    }

    suspend fun logout(refreshToken: String) {
        runCatching {
            client.post("$baseUrl/$API_PREFIX/auth/logout") {
                setBody(RefreshRequestDto(refreshToken))
            }
        }
        // A failed logout must never trap the user in a signed-in state:
        // the caller clears local storage regardless.
    }

    // ---- Profile ----------------------------------------------------------

    suspend fun me(): UserDto = call { client.get("$baseUrl/$API_PREFIX/me") }

    suspend fun updateProfile(displayName: String? = null, bio: String? = null): UserDto = call {
        client.patch("$baseUrl/$API_PREFIX/me") {
            setBody(UpdateProfileRequestDto(displayName = displayName, bio = bio))
        }
    }

    suspend fun meta(): MetaDto = call { client.get("$baseUrl/$API_PREFIX/meta") }

    /** Releases both HTTP clients. Called from Application.onTerminate paths. */
    fun close() {
        client.close()
        refreshClient.close()
    }

    suspend fun health(): Boolean = runCatching {
        client.get("$baseUrl/healthz").status == HttpStatusCode.OK
    }.getOrDefault(false)

    /**
     * Runs a request and converts anything that is not a 2xx into an
     * ApiException carrying the server's error code.
     */
    private suspend inline fun <reified T> call(block: () -> HttpResponse): T {
        val response = try {
            block()
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException.network(e)
        } catch (e: Throwable) {
            throw ApiException.unexpected(e)
        }

        if (response.status.isSuccess()) {
            return response.body()
        }
        throw response.toApiException()
    }

    private suspend fun HttpResponse.toApiException(): ApiException {
        val raw = runCatching { bodyAsText() }.getOrDefault("")
        val parsed = runCatching { json.decodeFromString<ApiErrorDto>(raw) }.getOrNull()
        return ApiException(
            statusCode = status.value,
            code = parsed?.error?.code ?: "http_${status.value}",
            message = parsed?.error?.message?.takeIf { it.isNotBlank() }
                ?: "Server returned ${status.value}",
        )
    }

    companion object {
        const val API_PREFIX = "v1"
    }
}
