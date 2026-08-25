package club.nuva.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These rules must stay identical to server/internal/api/handlers_auth.go.
 * If a test here starts failing, one of the two sides changed alone.
 */
class ValidationTest {

    @Test
    fun `accepts a normal username`() {
        assertTrue(Validation.isUsernameValid("kolya_2026"))
        assertNull(Validation.usernameError("kolya_2026"))
    }

    @Test
    fun `rejects a username that is too short`() {
        assertFalse(Validation.isUsernameValid("ab"))
        assertEquals("At least 3 characters", Validation.usernameError("ab"))
    }

    @Test
    fun `rejects a username that is too long`() {
        assertFalse(Validation.isUsernameValid("a".repeat(25)))
    }

    @Test
    fun `rejects non-latin and punctuation in usernames`() {
        assertFalse(Validation.isUsernameValid("коля"))
        assertFalse(Validation.isUsernameValid("ko lya"))
        assertFalse(Validation.isUsernameValid("ko-lya"))
        assertFalse(Validation.isUsernameValid("ko.lya"))
    }

    @Test
    fun `sanitizing strips forbidden characters and caps the length`() {
        assertEquals("kolya2026", Validation.sanitizeUsername("ko lya-2026!"))
        assertEquals("kolya", Validation.sanitizeUsername("коляkolya"))
        assertEquals(24, Validation.sanitizeUsername("z".repeat(60)).length)
    }

    @Test
    fun `empty input shows no error yet`() {
        assertNull(Validation.usernameError(""))
        assertNull(Validation.passwordError(""))
    }

    @Test
    fun `password must be at least eight characters`() {
        assertFalse(Validation.isPasswordValid("short12"))
        assertTrue(Validation.isPasswordValid("password123"))
    }

    @Test
    fun `password is measured in bytes because bcrypt truncates at 72`() {
        // 30 Cyrillic characters are 60 bytes in UTF-8: still fine.
        assertTrue(Validation.isPasswordValid("п".repeat(30)))
        // 40 Cyrillic characters are 80 bytes: the server would silently
        // truncate them, so we reject up front.
        assertFalse(Validation.isPasswordValid("п".repeat(40)))
    }

    @Test
    fun `display name is optional but bounded`() {
        assertTrue(Validation.isDisplayNameValid(""))
        assertTrue(Validation.isDisplayNameValid("Коля"))
        assertFalse(Validation.isDisplayNameValid("x".repeat(49)))
    }
}
