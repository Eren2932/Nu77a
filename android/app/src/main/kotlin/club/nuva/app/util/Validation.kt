package club.nuva.app.util

/**
 * Client-side mirror of the server's validation rules
 * (see server/internal/api/handlers_auth.go).
 *
 * The point is not to trust the client: the server validates again. The point
 * is to tell the user what is wrong while they type, instead of after a round
 * trip and a red banner.
 */
object Validation {

    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 24
    const val PASSWORD_MIN = 8

    /** bcrypt truncates at 72 bytes, so the server rejects anything longer. */
    const val PASSWORD_MAX_BYTES = 72
    const val DISPLAY_NAME_MAX = 48

    private val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{$USERNAME_MIN,$USERNAME_MAX}$")

    /** Strips characters the server would reject, as the user types. */
    fun sanitizeUsername(input: String): String =
        input.filter { it.isLetterOrDigit() && it.code < 128 || it == '_' }.take(USERNAME_MAX)

    fun usernameError(username: String): String? = when {
        username.isEmpty() -> null
        username.length < USERNAME_MIN -> "At least $USERNAME_MIN characters"
        !USERNAME_REGEX.matches(username) -> "Only a-z, 0-9 and _"
        else -> null
    }

    fun isUsernameValid(username: String): Boolean = USERNAME_REGEX.matches(username)

    fun passwordError(password: String): String? = when {
        password.isEmpty() -> null
        password.length < PASSWORD_MIN -> "At least $PASSWORD_MIN characters"
        password.toByteArray(Charsets.UTF_8).size > PASSWORD_MAX_BYTES ->
            "Too long, maximum $PASSWORD_MAX_BYTES bytes"
        else -> null
    }

    fun isPasswordValid(password: String): Boolean =
        password.length >= PASSWORD_MIN &&
            password.toByteArray(Charsets.UTF_8).size <= PASSWORD_MAX_BYTES

    fun isDisplayNameValid(displayName: String): Boolean =
        displayName.isBlank() || displayName.trim().length <= DISPLAY_NAME_MAX
}
