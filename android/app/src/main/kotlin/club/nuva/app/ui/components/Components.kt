package club.nuva.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import club.nuva.app.ui.theme.NuvaDanger
import club.nuva.app.ui.theme.NuvaMuted
import club.nuva.app.ui.theme.NuvaSuccess

/** Inline, dismissible error. No toasts: they disappear before you read them. */
@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = NuvaDanger.copy(alpha = 0.14f),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = NuvaDanger,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = NuvaDanger)
            }
        }
    }
}

/**
 * Shown exactly once, right after registration. The user cannot skip past it
 * without confirming, because the server cannot reissue this code.
 */
@Composable
fun RecoveryCodeDialog(
    code: String,
    onAcknowledged: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = { /* deliberately not dismissible by tapping outside */ },
        title = { Text("Save your recovery code") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = "This code is the only way back into your account if you " +
                        "forget your password. We cannot restore it for you. " +
                        "Write it down on paper now.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(0.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = code,
                        modifier = Modifier.padding(14.dp),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp,
                    )
                }
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy to clipboard")
                }
            }
        },
        confirmButton = {
            Button(onClick = onAcknowledged) { Text("I wrote it down") }
        },
    )
}

/** Small colored dot plus label, used for the realtime connection state. */
@Composable
fun StatusPill(
    label: String,
    online: Boolean,
    connecting: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val dotColor: Color = when {
        online -> NuvaSuccess
        connecting -> MaterialTheme.colorScheme.primary
        else -> NuvaMuted
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Spacer(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
