package club.nuva.app.data.repository

import club.nuva.app.BuildConfig
import club.nuva.app.data.local.ServerStore
import club.nuva.app.data.local.SessionStore
import club.nuva.app.data.remote.MetaDto
import club.nuva.app.data.remote.NuvaApi
import club.nuva.app.data.remote.RealtimeClient
import club.nuva.app.util.ServerUrl
import kotlinx.coroutines.flow.StateFlow

/**
 * The only code path that may change which server the app talks to.
 *
 * Two invariants live here, and both are non-negotiable:
 *
 *  1. A candidate address is never stored before the server behind it answers
 *     /v1/meta. Otherwise a typo leaves the app permanently pointed at nothing
 *     with no screen left to fix it from.
 *  2. Changing server ALWAYS destroys the local session. Tokens are issued by
 *     one specific server and are meaningless - and a privacy leak if replayed
 *     - anywhere else.
 */
class ServerRepository(
    private val serverStore: ServerStore,
    private val sessionStore: SessionStore,
    private val api: NuvaApi,
    private val realtime: RealtimeClient,
) {
    val baseUrl: StateFlow<String?> = serverStore.baseUrl

    /** Prefilled in the picker. Just a suggestion, never enforced. */
    val suggestedUrl: String = BuildConfig.API_BASE_URL

    /** Plain HTTP is tolerated in debug builds only. */
    val allowInsecure: Boolean = BuildConfig.DEBUG

    sealed interface Result {
        data class Connected(val url: String, val meta: MetaDto) : Result
        data class Rejected(val reason: String) : Result
    }

    suspend fun connectTo(input: String): Result {
        val normalized = when (val outcome = ServerUrl.normalize(input, allowInsecure)) {
            is ServerUrl.Outcome.Invalid -> return Result.Rejected(outcome.reason)
            is ServerUrl.Outcome.Valid -> outcome.url
        }

        val meta = try {
            api.probe(normalized)
        } catch (e: Exception) {
            return Result.Rejected(
                e.message ?: "Could not reach that server. Check the address and try again.",
            )
        }

        if (normalized != serverStore.current()) {
            // Order matters: stop talking to the old server before forgetting
            // the credentials the socket is currently authenticated with.
            realtime.disconnect()
            sessionStore.clear()
        }
        serverStore.save(normalized)
        return Result.Connected(normalized, meta)
    }

    /** Full reset: forget the server and the account bound to it. */
    fun forget() {
        realtime.disconnect()
        sessionStore.clear()
        serverStore.clear()
    }
}
