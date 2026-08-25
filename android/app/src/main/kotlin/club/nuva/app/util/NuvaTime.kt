package club.nuva.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Time formatting for the chat UI.
 *
 * IMPORTANT, and the reason this file exists instead of java.time:
 * minSdk is 24 and core library desugaring is OFF in app/build.gradle.kts.
 * java.time.* only exists from API 26, so LocalDateTime/Instant would compile
 * fine and then crash with NoClassDefFoundError on a real Android 7 phone.
 * SimpleDateFormat + Calendar work on every supported device.
 *
 * If we ever want java.time, the change is
 * `isCoreLibraryDesugaringEnabled = true` plus the desugar_jdk_libs dependency
 * — a build decision, not something to sneak in from a UI file.
 */
object NuvaTime {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** Clock time inside a bubble: "18:42". */
    fun clock(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

    /** Right-hand stamp in the chat list: time today, weekday this week, else date. */
    fun listStamp(millis: Long, now: Long = System.currentTimeMillis()): String {
        if (millis <= 0L) return ""
        return when {
            isSameDay(millis, now) -> clock(millis)
            now - millis < 7 * DAY -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(millis))
            else -> SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(millis))
        }
    }

    /** Sticky separator between days: "Today", "Yesterday", "14 March". */
    fun dayLabel(millis: Long, now: Long = System.currentTimeMillis()): String = when {
        isSameDay(millis, now) -> "Today"
        isSameDay(millis, now - DAY) -> "Yesterday"
        else -> SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(millis))
    }

    /** Coarse relative text, used for presence lines. */
    fun relative(millis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - millis
        return when {
            diff < MINUTE -> "just now"
            diff < HOUR -> "${diff / MINUTE} min ago"
            diff < DAY -> "${diff / HOUR} h ago"
            diff < 7 * DAY -> "${diff / DAY} d ago"
            else -> SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(millis))
        }
    }

    fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }
}
