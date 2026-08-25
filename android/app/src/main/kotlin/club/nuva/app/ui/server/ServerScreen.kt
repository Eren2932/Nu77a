package club.nuva.app.ui.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import club.nuva.app.ui.components.ErrorBannerHost
import club.nuva.app.ui.design.NuvaButton
import club.nuva.app.ui.design.NuvaCanvas
import club.nuva.app.ui.design.NuvaCard
import club.nuva.app.ui.design.NuvaField
import club.nuva.app.ui.design.NuvaGhostButton
import club.nuva.app.ui.theme.NuvaSpace
import club.nuva.app.ui.theme.NuvaTheme

/**
 * The first screen of the app, and the most important one to get right.
 *
 * It has to teach the whole product in five seconds: there is no "Nuva Inc"
 * server, you pick who hosts you, and that choice is reversible. The address is
 * verified against /v1/meta before it is stored — a typo must never be able to
 * leave the app pointed at nothing.
 */
@Composable
fun ServerScreen(
    viewModel: ServerViewModel,
    onConnected: () -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val p = NuvaTheme.palette
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The ViewModel signals success by setting connectedTo. Navigation lives
    // with the caller, so this screen never needs a NavController.
    LaunchedEffect(state.connectedTo) {
        if (state.connectedTo != null) onConnected()
    }

    NuvaCanvas(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NuvaSpace.gutter),
        ) {
            Spacer(Modifier.height(NuvaSpace.huge))

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(p.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Dns,
                    contentDescription = null,
                    tint = p.accent,
                    modifier = Modifier.size(26.dp),
                )
            }

            Spacer(Modifier.height(NuvaSpace.xl))
            Text(
                text = "Choose your server",
                style = MaterialTheme.typography.headlineLarge,
                color = p.text,
            )
            Spacer(Modifier.height(NuvaSpace.xs))
            Text(
                text = "Nuva has no company behind it. Your account, your messages and " +
                    "your contacts live on the server you pick — ours, a friend's, or " +
                    "one you run yourself.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.textMuted,
            )

            Spacer(Modifier.height(NuvaSpace.xl))
            ErrorBannerHost(
                message = state.errorMessage,
                onDismiss = viewModel::dismissError,
            )

            Spacer(Modifier.height(NuvaSpace.md))
            NuvaField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                label = "Server address",
                placeholder = "nuva.club",
                mono = true,
                enabled = !state.isBusy,
                supporting = "https:// is added for you. The address is checked before it is saved.",
            )

            Spacer(Modifier.height(NuvaSpace.lg))
            NuvaButton(
                text = if (state.isBusy) "Checking…" else "Connect",
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                busy = state.isBusy,
            )

            if (onCancel != null) {
                Spacer(Modifier.height(NuvaSpace.md))
                NuvaGhostButton(text = "Keep current server", onClick = onCancel)
            }

            Spacer(Modifier.height(NuvaSpace.xl))

            NuvaCard {
                Column(Modifier.padding(NuvaSpace.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (state.allowInsecure) Icons.Filled.LockOpen else Icons.Filled.Lock,
                            contentDescription = null,
                            tint = if (state.allowInsecure) p.amber else p.mint,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(NuvaSpace.sm))
                        Text(
                            text = if (state.allowInsecure) {
                                "Debug build: plain HTTP allowed"
                            } else {
                                "HTTPS only"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = p.text,
                        )
                    }
                    Spacer(Modifier.height(NuvaSpace.xs))
                    Text(
                        text = if (state.allowInsecure) {
                            "This build accepts http:// so you can talk to a server on " +
                                "your own machine. Release builds refuse it, because an " +
                                "unencrypted messenger is not a messenger."
                        } else {
                            "Release builds only connect over HTTPS. There is no setting " +
                                "to turn that off."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textMuted,
                    )
                }
            }

            Spacer(Modifier.height(NuvaSpace.md))

            NuvaCard(accented = true) {
                Column(Modifier.padding(NuvaSpace.lg)) {
                    Text(
                        text = "Switching server signs you out",
                        style = MaterialTheme.typography.titleSmall,
                        color = p.text,
                    )
                    Spacer(Modifier.height(NuvaSpace.xs))
                    Text(
                        text = "Accounts are not portable between servers, and tokens from " +
                            "one server are meaningless on another. Changing the address " +
                            "always clears the local session — never a surprise, always " +
                            "stated up front.",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textMuted,
                    )
                }
            }

            Spacer(Modifier.height(NuvaSpace.huge))
        }
    }
}
