package club.nuva.app.util

import java.net.URI

/**
 * Parsing and normalising a user-typed server address.
 *
 * Deliberately free of any Android class (no android.net.Uri), so it runs in
 * plain JVM unit tests on every CI build. A bug here means the app cannot reach
 * any server at all, so it is the single most test-worthy function in the app.
 */
object ServerUrl {

    sealed interface Outcome {
        data class Valid(val url: String) : Outcome
        data class Invalid(val reason: String) : Outcome
    }

    /**
     * Turns what a human types into a canonical base URL, or explains why it
     * cannot.
     *
     * Accepted:  nuva.example.com      -> https://nuva.example.com
     *            https://a.b/nuva/     -> https://a.b/nuva
     *            http://192.168.1.10:8080 (only when [allowInsecure])
     *
     * @param allowInsecure true only in debug builds. A release build must
     *   never talk plain HTTP: tokens would travel in clear text.
     */
    fun normalize(input: String, allowInsecure: Boolean): Outcome {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Outcome.Invalid("Enter a server address")
        if (trimmed.any { it.isWhitespace() }) return Outcome.Invalid("Address cannot contain spaces")

        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"

        val uri = try {
            URI(withScheme)
        } catch (t: Throwable) {
            return Outcome.Invalid("This is not a valid address")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return Outcome.Invalid("Address must start with https://")
        }
        if (scheme == "http" && !allowInsecure) {
            return Outcome.Invalid("Only https:// is allowed: http would send your password in clear text")
        }

        val host = uri.host
        if (host.isNullOrBlank()) return Outcome.Invalid("Missing server name")
        if (!host.contains('.') && host != "localhost") {
            return Outcome.Invalid("Server name looks incomplete")
        }

        val port = if (uri.port > 0) ":${uri.port}" else ""
        val path = uri.path.orEmpty().trimEnd('/')

        return Outcome.Valid("$scheme://$host$port$path")
    }

    /** Short label for the UI: just the host, no scheme, no path. */
    fun hostOf(url: String): String = try {
        URI(url).host ?: url
    } catch (t: Throwable) {
        url
    }
}
