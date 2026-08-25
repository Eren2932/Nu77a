package club.nuva.app.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import club.nuva.app.data.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ServerViewModel(private val repository: ServerRepository) : ViewModel() {

    data class UiState(
        val input: String = "",
        val isBusy: Boolean = false,
        val errorMessage: String? = null,
        /** Set once the address answered; the caller then leaves this screen. */
        val connectedTo: String? = null,
        val serverVersion: String? = null,
        val allowInsecure: Boolean = false,
    ) {
        val canSubmit: Boolean get() = !isBusy && input.isNotBlank()
    }

    private val _state = MutableStateFlow(
        UiState(
            // Prefill whatever is already configured, otherwise the build-time
            // suggestion. Never empty, so the common case is one tap.
            input = repository.baseUrl.value ?: repository.suggestedUrl,
            allowInsecure = repository.allowInsecure,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onInputChange(value: String) = _state.update {
        it.copy(input = value, errorMessage = null)
    }

    fun dismissError() = _state.update { it.copy(errorMessage = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return

        _state.update { it.copy(isBusy = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = repository.connectTo(current.input)) {
                is ServerRepository.Result.Connected -> _state.update {
                    it.copy(
                        isBusy = false,
                        input = result.url,
                        connectedTo = result.url,
                        serverVersion = result.meta.apiVersion,
                    )
                }
                is ServerRepository.Result.Rejected -> _state.update {
                    it.copy(isBusy = false, errorMessage = result.reason)
                }
            }
        }
    }
}
