package club.nuva.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import club.nuva.app.data.local.SessionStore
import club.nuva.app.data.remote.ApiException
import club.nuva.app.data.remote.NuvaApi
import club.nuva.app.data.remote.RealtimeClient
import club.nuva.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/**
 * Sprint 0 home screen state.
 *
 * Its job is to prove the whole vertical slice works end to end: stored
 * session -> authenticated HTTP call -> live WebSocket -> round trip back.
 * Sprint 2 replaces this screen with the conversation list; the plumbing below
 * stays exactly the same.
 */
class HomeViewModel(
    private val authRepository: AuthRepository,
    private val api: NuvaApi,
    private val realtime: RealtimeClient,
) : ViewModel() {

    data class UiState(
        val session: SessionStore.Session? = null,
        val serverVersion: String = "",
        val onlineUsers: Int = 0,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null,
        val log: List<String> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val realtimeState: StateFlow<RealtimeClient.State> = realtime.state

    init {
        viewModelScope.launch {
            authRepository.session.collect { session ->
                _state.update { it.copy(session = session) }
            }
        }
        viewModelScope.launch {
            realtime.events.collect { envelope ->
                appendLog("<- ${envelope.type} ${envelope.payload?.toString()?.take(120).orEmpty()}")
            }
        }

        realtime.connect()
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                authRepository.refreshProfile()
                val meta = api.meta()
                _state.update {
                    it.copy(
                        isRefreshing = false,
                        serverVersion = meta.apiVersion,
                        onlineUsers = meta.onlineUsers,
                    )
                }
                appendLog("server reachable, api=${meta.apiVersion}, online=${meta.onlineUsers}")
            } catch (e: ApiException) {
                _state.update { it.copy(isRefreshing = false, errorMessage = e.message) }
            }
        }
    }

    /** Round-trips a frame through the server to prove the socket is alive. */
    fun sendEcho() {
        val stamp = System.currentTimeMillis()
        realtime.enqueue(type = "echo", payload = JsonPrimitive("hello-$stamp"), id = stamp.toString())
        appendLog("-> echo hello-$stamp")
    }

    fun logout() {
        viewModelScope.launch {
            realtime.disconnect()
            authRepository.logout()
        }
    }

    fun dismissError() = _state.update { it.copy(errorMessage = null) }

    private fun appendLog(line: String) = _state.update {
        // Keep the newest 40 lines: this is a debug aid, not a database.
        it.copy(log = (listOf(line) + it.log).take(40))
    }
}
