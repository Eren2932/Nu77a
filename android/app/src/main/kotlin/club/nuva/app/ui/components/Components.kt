package club.nuva.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import club.nuva.app.ui.design.NuvaButton
import club.nuva.app.ui.design.NuvaDot
import club.nuva.app.ui.design.NuvaGhostButton
import club.nuva.app.ui.theme.NuvaMonoFamily
import club.nuva.app.ui.theme.NuvaSpace
import club.nuva.app.ui.theme.NuvaTheme

/**
 * Cross-screen widgets that carry product rules, not just styling.
 * Pure visual atoms live in ui/design/Primitives.kt.
 */

/**
 * Inline, dismissible error. No toasts: they vanish before they are read, and
 * a network error is exactly the message a user needs to re-read twice.
 */
@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = NuvaTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(p.coral.copy(alpha = if (p.isDark) 0.14f else 0.10f))
            .border(1.dp, p.coral.copy(alpha = 0.30f), MaterialTheme.shapes.medium)
            .padding(start = NuvaSpace.lg, end = NuvaSpace.xs, top = NuvaSpace.md, bottom = NuvaSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = p.coral,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(NuvaSpace.md))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = p.coral,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = p.coral)
        }
    }
}

/** Same banner, but it animates itself in and out from a nullable message. */
@Composable
fun ErrorBannerHost(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { -it / 3 },
        exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 3 },
        modifier = modifier,
    ) {
        ErrorBanner(message = message.orEmpty(), onDismiss = onDismiss)
    }
}

/**
 * Shown exactly once, right after registration.
 *
 * Product rule encoded here: the dialog cannot be dismissed by tapping outside
 * and the confirm button stays disabled until the code has been copied or the
 * user has ticked "written down". The server hashes this code and physically
 * cannot reissue it — losing it means losing the account.
 */
@Composable
fun RecoveryCodeDialog(
    code: String,
    onAcknowledged: () -> Unit,
) {
    val p = NuvaTheme.palette
    val clipboard = LocalClipboardManager.current
    var acknowledged by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { /* deliberately not dismissible */ },
        containerColor = p.surface,
        titleContentColor = p.text,
        textContentColor = p.textMuted,
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            Icon(Icons.Filled.Key, contentDescription = null, tint = p.amber)
        },
        title = { Text("Save your recovery code", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    text = "This is the only way back into your account if you forget " +
                        "your password. It is stored on the server as a hash, so nobody " +
                        "can send it to you again. Write it on paper now.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(NuvaSpace.lg))
                Text(
                    text = code,
                    fontFamily = NuvaMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.2.sp,
                    color = p.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(p.surfaceAlt)
                        .border(1.dp, p.hairline, MaterialTheme.shapes.medium)
                        .padding(NuvaSpace.lg),
                )
                Spacer(Modifier.height(NuvaSpace.md))
                NuvaGhostButton(
                    text = if (copied) "Copied" else "Copy to clipboard",
                    icon = Icons.Filled.ContentCopy,
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                        acknowledged = true
                    },
                )
                Spacer(Modifier.height(NuvaSpace.md))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (acknowledged) p.mint.copy(alpha = 0.12f) else Color.Transparent)
                        .padding(NuvaSpace.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    NuvaDot(if (acknowledged) p.mint else p.textFaint, size = 9.dp)
                    Spacer(Modifier.width(NuvaSpace.sm))
                    Text(
                        text = if (acknowledged) {
                            "Good. Keep that paper somewhere you will find it."
                        } else {
                            "Copy the code to continue."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (acknowledged) p.mint else p.textMuted,
                    )
                }
            }
        },
        confirmButton = {
            NuvaButton(
                text = "I wrote it down",
                onClick = onAcknowledged,
                enabled = acknowledged,
                modifier = Modifier.width(200.dp),
            )
        },
    )
}

/** Realtime connection state, worded for humans. */
@Composable
fun StatusPill(
    label: String,
    online: Boolean,
    connecting: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val p = NuvaTheme.palette
    val color: Color = when {
        online -> p.mint
        connecting -> p.amber
        else -> p.textFaint
    }
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(p.surfaceAlt)
            .padding(horizontal = NuvaSpace.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NuvaDot(color)
        Spacer(Modifier.width(NuvaSpace.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = p.textMuted,
        )
    }
}
