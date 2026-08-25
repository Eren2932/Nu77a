package club.nuva.app.data.remote

import android.util.Log
import club.nuva.app.data.local.SessionStore
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.random.Random

/**
 * Persistent realtime channel.
 *
 * Contract with the server (server/internal/ws/protocol.go): every frame is a
 * JSON envelope { type, id?, payload? }. One decoder, one encoder, forever.
 *
 * Reconnection uses exponential backoff with jitter. Without jitter, every
 * phone on the planet reconnects at the same second after a server restart and
 * knocks it over again.
 */
class RealtimeClient(
    private val api: NuvaApi,
    private val sessionStore: SessionStore,
    private val scope: CoroutineScope,
) {
    @Serializable
    data class Envelope(
        val type: String,
        val id: String? = null,
        val payload: JsonElement? = null,
    )

    @Serializable
    data class HelloPayload(
        @SerialName("user_id") val userId: String = "",
        @SerialName("heartbeat_secs") val heartbeatSecs: Int = 30,
        @SerialName("api_version") val apiVersion: String = "v1",
    )

    enum class State { Idle, Connecting, Online, Reconnecting }

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<Envelope>(replay = 0, extraBufferCapacity = 64)
    val incoming: SharedFlow<Envelope> = _incoming.asSharedFlow()

    private val outgoing = Channel<String>(capacity = 64)
    private var connectionJob: Job? = null

    fun connect() {
        if (connectionJob?.isActive == true) return
        connectionJob = scope.launch { connectionLoop() }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        _state.value = State.Idle
    }

    /** Queues a frame. Safe to call while offline: it is sent on reconnect. */
    fun send(type: String, payload: JsonElement? = null, id: String? = null) {
        val frame = json.encodeToString(Envelope.serializer(), Envelope(type, id, payload))
        val result = outgoing.trySend(frame)
        if (result.isFailure) {
            Log.w(TAG, "outgoing queue is full, dropping frame of type=$type")
        }
    }

    private suspend fun connectionLoop() {
        var attempt = 0

        while (scope.isActive) {
            val token = sessionStore.currentAccessToken()
            if (token == null) {
                _state.value = State.Idle
                return
            }

            _state.value = if (attempt == 0) State.Connecting else State.Reconnecting

            try {
                val wsUrl = api.baseUrl
                    .replaceFirst("https://", "wss://")
                    .replaceFirst("http://", "ws://")
                    .trimEnd('/') + "/${NuvaApi.API_PREFIX}/ws"

                api.client.webSocket(
                    urlString = wsUrl,
                    request = { header("Authorization", "Bearer $token") },
                ) {
                    attempt = 0
                    _state.value = State.Online
                    Log.i(TAG, "realtime channel open")

                    // Writer pump for this connection.
                    val writer = launch {
                        for (frame in outgoing) {
                            send(frame)
                        }
                    }
                    // Heartbeat so the server never times us out at 90s.
                    val heartbeat = launch {
                        while (isActive) {
                            delay(HEARTBEAT_MS)
                            send(json.encodeToString(Envelope.serializer(), Envelope("ping")))
                        }
                    }

                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val text = frame.readText()
                            val envelope = runCatching {
                                json.decodeFromString(Envelope.serializer(), text)
                            }.getOrNull()
                            if (envelope == null) {
                                Log.w(TAG, "unparsable frame: ${text.take(120)}")
                                continue
                            }
                            _incoming.emit(envelope)
                        }
                    } finally {
                        heartbeat.cancel()
                        writer.cancel()
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "realtime channel dropped: ${t.message}")
            }

            if (!scope.isActive) return

            attempt += 1
            _state.value = State.Reconnecting
            delay(backoffMillis(attempt))
        }
    }

    /** 1s, 2s, 4s ... capped at 30s, plus up to 1s of jitter. */
    private fun backoffMillis(attempt: Int): Long {
        val exponential = min(BASE_BACKOFF_MS shl min(attempt - 1, 5), MAX_BACKOFF_MS)
        return exponential + Random.nextLong(0, JITTER_MS)
    }

    private companion object {
        const val TAG = "NuvaRealtime"
        const val HEARTBEAT_MS = 30_000L
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val JITTER_MS = 1_000L
    }
}
