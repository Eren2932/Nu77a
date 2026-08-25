package club.nuva.app.data.remote

import android.util.Log
import club.nuva.app.data.local.SessionStore
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.math.min
import kotlin.random.Random

/**
 * Persistent realtime channel.
 *
 * Contract with the server (server/internal/ws/protocol.go): every frame is a
 * JSON envelope { type, id?, payload? }. One decoder, one encoder, forever.
 *
 * Reconnection uses exponential backoff with jitter. Without jitter, every
 * phone reconnects at the same second after a server restart and knocks it
 * over again.
 *
 * ---------------------------------------------------------------------------
 * NAMING RULE FOR THIS FILE - do not "clean it up" later.
 *
 * A Ktor `DefaultClientWebSocketSession` has members named `incoming`,
 * `outgoing` and `send`. Inside a `webSocket { }` block the session is an
 * implicit receiver, so any class property with one of those names silently
 * competes with it during name resolution. That produced two build failures in
 * a row (a Flow being iterated as a channel, then a SendChannel being iterated
 * as a ReceiveChannel) and - far worse - a `send(String)` call that was one
 * resolution rule away from writing frames into our own outbox instead of the
 * socket. That version would have compiled and shipped as "messages never
 * arrive".
 *
 * Two rules keep it dead:
 *   1. Our own members are named `events`, `outbox`, `enqueue` - never
 *      `incoming`, `outgoing`, `send`.
 *   2. The `webSocket { }` block does nothing but hand `this` to
 *      `runSession(session)`. Inside that function there is NO implicit
 *      session receiver at all, so every socket access must be written
 *      `session.x` and the compiler cannot guess wrong.
 * ---------------------------------------------------------------------------
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

    /** Decoded frames from the server. Named `events`, never `incoming`. */
    private val _events = MutableSharedFlow<Envelope>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<Envelope> = _events.asSharedFlow()

    /** Frames waiting to be written. Named `outbox`, never `outgoing`. */
    private val outbox = Channel<String>(capacity = 64)

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

    /**
     * Queues a frame. Safe to call while offline: it is written on reconnect.
     * Named `enqueue`, never `send`.
     */
    fun enqueue(type: String, payload: JsonElement? = null, id: String? = null) {
        val frame = json.encodeToString(Envelope.serializer(), Envelope(type, id, payload))
        val result = outbox.trySend(frame)
        if (result.isFailure) {
            Log.w(TAG, "outbox is full, dropping frame of type=$type")
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
                api.client.webSocket(
                    urlString = websocketUrl(),
                    request = { header("Authorization", "Bearer $token") },
                ) {
                    // Deliberately the only two statements in this block.
                    attempt = 0
                    runSession(this)
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

    /**
     * Drives one live connection until it closes. `session` is a plain
     * parameter on purpose - see the naming rule at the top of this file.
     */
    private suspend fun runSession(session: DefaultClientWebSocketSession) {
        _state.value = State.Online
        Log.i(TAG, "realtime channel open")

        coroutineScope {
            val writer = launch {
                for (text in outbox) {
                    session.send(text)
                }
            }

            // Application-level heartbeat so the server never times us out.
            val heartbeat = launch {
                while (isActive) {
                    delay(HEARTBEAT_MS)
                    session.send(json.encodeToString(Envelope.serializer(), Envelope("ping")))
                }
            }

            try {
                for (frame in session.incoming) {
                    if (frame !is Frame.Text) continue
                    val text = frame.readText()
                    val envelope = runCatching {
                        json.decodeFromString(Envelope.serializer(), text)
                    }.getOrNull()
                    if (envelope == null) {
                        Log.w(TAG, "unparsable frame: ${text.take(120)}")
                        continue
                    }
                    _events.emit(envelope)
                }
            } finally {
                heartbeat.cancel()
                writer.cancel()
            }
        }
    }

    private fun websocketUrl(): String =
        api.baseUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/') + "/${NuvaApi.API_PREFIX}/ws"

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
