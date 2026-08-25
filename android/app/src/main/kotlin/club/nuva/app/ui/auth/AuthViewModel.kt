package club.nuva.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import club.nuva.app.data.remote.ApiException
import club.nuva.app.data.repository.AuthRepository
import club.nuva.app.util.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    enum class Mode { SignIn, Register }

    data class UiState(
        val mode: Mode = Mode.SignIn,
        val username: String = "",
        val displayName: String = "",
        val password: String = "",
        val isBusy: Boolean = false,
        val errorMessage: String? = null,
        /** Non-null right after registration: must be shown once, then dismissed. */
        val recoveryCode: String? = null,
    ) {
        val usernameError: String? get() = Validation.usernameError(username)
        val passwordError: String? get() = Validation.passwordError(password)

        val canSubmit: Boolean
            get() = !isBusy &&
                Validation.isUsernameValid(username) &&
                Validation.isPasswordValid(password) &&
                (mode == Mode.SignIn || Validation.isDisplayNameValid(displayName))
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun setMode(mode: Mode) = _state.update { it.copy(mode = mode, errorMessage = null) }

    fun onUsernameChange(value: String) = _state.update {
        // Enforce the server's rule while typing instead of failing on submit.
        it.copy(username = Validation.sanitizeUsername(value), errorMessage = null)
    }

    fun onDisplayNameChange(value: String) = _state.update {
        it.copy(displayName = value.take(Validation.DISPLAY_NAME_MAX), errorMessage = null)
    }

    fun onPasswordChange(value: String) = _state.update {
        it.copy(password = value.take(Validation.PASSWORD_MAX_BYTES), errorMessage = null)
    }

    fun dismissError() = _state.update { it.copy(errorMessage = null) }

    fun dismissRecoveryCode() = _state.update { it.copy(recoveryCode = null) }

    fun submit() {
        val snapshot = _state.value
        if (!snapshot.canSubmit) return

        _state.update { it.copy(isBusy = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                if (snapshot.mode == Mode.Register) {
                    val code = repository.register(
                        username = snapshot.username,
                        displayName = snapshot.displayName,
                        password = snapshot.password,
                    )
                    // Session is already stored; the code screen gates navigation.
                    _state.update { it.copy(isBusy = false, recoveryCode = code, password = "") }
                } else {
                    repository.login(snapshot.username, snapshot.password)
                    _state.update { it.copy(isBusy = false, password = "") }
                }
            } catch (e: ApiException) {
                _state.update { it.copy(isBusy = false, errorMessage = e.message) }
            } catch (e: Throwable) {
                _state.update {
                    it.copy(isBusy = false, errorMessage = e.message ?: "Unknown error")
                }
            }
        }
    }
}
