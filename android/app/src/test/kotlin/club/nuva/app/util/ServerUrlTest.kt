package club.nuva.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {

    private fun valid(input: String, allowInsecure: Boolean = false): String {
        val outcome = ServerUrl.normalize(input, allowInsecure)
        assertTrue("expected $input to be valid, got $outcome", outcome is ServerUrl.Outcome.Valid)
        return (outcome as ServerUrl.Outcome.Valid).url
    }

    private fun invalid(input: String, allowInsecure: Boolean = false) {
        val outcome = ServerUrl.normalize(input, allowInsecure)
        assertTrue("expected $input to be rejected", outcome is ServerUrl.Outcome.Invalid)
    }

    @Test
    fun `bare host gets https`() {
        assertEquals("https://nuva.example.com", valid("nuva.example.com"))
    }

    @Test
    fun `trailing slash is dropped`() {
        assertEquals("https://nuva.example.com", valid("https://nuva.example.com/"))
    }

    @Test
    fun `path prefix is kept`() {
        assertEquals("https://example.com/nuva", valid("https://example.com/nuva/"))
    }

    @Test
    fun `port is kept`() {
        assertEquals("https://example.com:8443", valid("https://example.com:8443"))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertEquals("https://example.com", valid("  example.com  "))
    }

    @Test
    fun `quick tunnel hostname is accepted`() {
        assertEquals(
            "https://calm-river-1234.trycloudflare.com",
            valid("https://calm-river-1234.trycloudflare.com"),
        )
    }

    @Test
    fun `http is rejected in release builds`() {
        invalid("http://example.com", allowInsecure = false)
    }

    @Test
    fun `http is accepted in debug builds`() {
        assertEquals("http://10.0.2.2:8080", valid("http://10.0.2.2:8080", allowInsecure = true))
    }

    @Test
    fun `garbage is rejected`() {
        invalid("")
        invalid("   ")
        invalid("not a host")
        invalid("ftp://example.com")
        invalid("https://")
        invalid("localhostt")
    }

    @Test
    fun `hostOf strips scheme and path`() {
        assertEquals("api.nuva.club", ServerUrl.hostOf("https://api.nuva.club/v1"))
    }
}
