package club.nuva.app.data.remote

/**
 * A single exception type for every failed call, so the UI has exactly one
 * thing to catch and one place that turns a failure into human text.
 */
class ApiException(
    val statusCode: Int,
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    val isAuthProblem: Boolean get() = statusCode == 401

    /** True when retrying later can plausibly succeed. */
    val isTransient: Boolean
        get() = statusCode == 0 || statusCode == 429 || statusCode >= 500

    companion object {
        fun network(cause: Throwable) = ApiException(
            statusCode = 0,
            code = "network_unreachable",
            message = "No connection to the Nuva server. Check your internet and try again.",
            cause = cause,
        )

        fun unexpected(cause: Throwable) = ApiException(
            statusCode = -1,
            code = "unexpected",
            message = "Something unexpected happened: ${cause.message ?: cause::class.simpleName}",
            cause = cause,
        )
    }
}
