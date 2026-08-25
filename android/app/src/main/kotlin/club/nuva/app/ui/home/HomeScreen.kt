package club.nuva.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BoltOutlined
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import club.nuva.app.data.remote.RealtimeClient
import club.nuva.app.ui.components.ErrorBannerHost
import club.nuva.app.ui.components.StatusPill
import club.nuva.app.ui.design.NuvaCard
import club.nuva.app.ui.design.NuvaGhostButton
import club.nuva.app.ui.design.NuvaHairline
import club.nuva.app.ui.design.NuvaHeader
import club.nuva.app.ui.design.NuvaMonoValue
import club.nuva.app.ui.design.NuvaRow
import club.nuva.app.ui.design.NuvaSectionLabel
import club.nuva.app.ui.theme.NuvaMonoStyle
import club.nuva.app.ui.theme.NuvaSpace
import club.nuva.app.ui.theme.NuvaTheme

/**
 * DIAGNOSTICS — the old sprint-0 home screen, kept on purpose.
 *
 * It proves the vertical slice on a real device in ten seconds: stored session
 * -> authenticated HTTP call -> live WebSocket -> echo round trip. That is the
 * single most useful tool we have when a build "does nothing" on someone's
 * phone, so it moved to Settings instead of being deleted when the chat list
 * took over the home screen.
 *
 * Reachable at Me -> Settings -> Diagnostics.
 */
@Composable
fun DiagnosticsScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = NuvaTheme.palette
    val state by viewModel.state.collectAsStateWithLifecycle()
    val realtimeState by viewModel.realtimeState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        NuvaHeader(
            title = "Diagnostics",
            subtitle = "For when the app looks fine and is not",
            compact = true,
            onBack = onBack,
            actions = {
                IconButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = if (state.isRefreshing) p.textFaint else p.textMuted,
                    )
                }
            },
        )

        Row(
            modifier = Modifier.padding(horizontal = NuvaSpace.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(
                label = when (realtimeState) {
                    RealtimeClient.State.Online -> "Realtime online"
                    RealtimeClient.State.Connecting -> "Connecting…"
                    RealtimeClient.State.Reconnecting -> "Reconnecting…"
                    RealtimeClient.State.Idle -> "Offline"
                },
                online = realtimeState == RealtimeClient.State.Online,
                connecting = realtimeState == RealtimeClient.State.Connecting ||
                    realtimeState == RealtimeClient.State.Reconnecting,
            )
            Spacer(Modifier.width(NuvaSpace.sm))
            StatusPill(
                label = "${state.onlineUsers} online",
                online = state.onlineUsers > 0,
            )
        }

        Spacer(Modifier.height(NuvaSpace.md))
        ErrorBannerHost(
            message = state.errorMessage,
            onDismiss = viewModel::dismissError,
            modifier = Modifier.padding(horizontal = NuvaSpace.gutter),
        )

        NuvaSectionLabel("Session")
        NuvaCard(modifier = Modifier.padding(horizontal = NuvaSpace.gutter)) {
            Column {
                NuvaRow(
                    title = "Username",
                    showChevron = false,
                    trailing = { NuvaMonoValue(state.session?.username.orEmpty()) },
                )
                NuvaHairline()
                NuvaRow(
                    title = "User id",
                    showChevron = false,
                    trailing = { NuvaMonoValue(state.session?.userId.orEmpty()) },
                )
                NuvaHairline()
                NuvaRow(
                    title = "Server API",
                    showChevron = false,
                    trailing = { NuvaMonoValue(state.serverVersion.ifBlank { "unknown" }) },
                )
            }
        }

        NuvaSectionLabel("Socket")
        Column(modifier = Modifier.padding(horizontal = NuvaSpace.gutter)) {
            NuvaGhostButton(
                text = "Send echo frame",
                icon = Icons.Filled.Bolt,
                onClick = viewModel::sendEcho,
                enabled = realtimeState == RealtimeClient.State.Online,
            )
            Spacer(Modifier.height(NuvaSpace.xs))
            Text(
                text = if (realtimeState == RealtimeClient.State.Online) {
                    "A frame goes out and must come back. If it does, the socket is real."
                } else {
                    "Enabled once the socket is online."
                },
                style = MaterialTheme.typography.bodySmall,
                color = p.textFaint,
            )
        }

        NuvaSectionLabel("Frame log")
        Column(
            modifier = Modifier
                .padding(horizontal = NuvaSpace.gutter)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(p.surfaceSunken)
                .padding(NuvaSpace.md),
        ) {
            if (state.log.isEmpty()) {
                Text(
                    text = "No frames yet.",
                    style = NuvaMonoStyle,
                    color = p.textFaint,
                )
            } else {
                state.log.forEach { line ->
                    Text(
                        text = line,
                        style = NuvaMonoStyle,
                        color = if (line.startsWith("->")) p.accentSoft else p.textMuted,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(NuvaSpace.huge))
    }
}
