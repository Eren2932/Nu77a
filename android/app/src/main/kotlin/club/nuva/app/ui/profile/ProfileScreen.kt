package club.nuva.app.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import club.nuva.app.di.ServiceLocator
import club.nuva.app.ui.design.NuvaAvatar
import club.nuva.app.ui.design.NuvaCard
import club.nuva.app.ui.design.NuvaGhostButton
import club.nuva.app.ui.design.NuvaHairline
import club.nuva.app.ui.design.NuvaHeader
import club.nuva.app.ui.design.NuvaMonoValue
import club.nuva.app.ui.design.NuvaRow
import club.nuva.app.ui.design.NuvaSectionLabel
import club.nuva.app.ui.theme.NuvaSpace
import club.nuva.app.ui.theme.NuvaTheme
import club.nuva.app.util.ServerUrl
import kotlinx.coroutines.launch

/**
 * "Me".
 *
 * Shows only what the app actually knows without a network call: the cached
 * session. No spinner on open, no empty avatar frame waiting for a request that
 * may never come back — offline, this screen is still complete.
 */
@Composable
fun ProfileScreen(
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val p = NuvaTheme.palette
    val session by ServiceLocator.authRepository.session.collectAsStateWithLifecycle()
    val server by ServiceLocator.serverRepository.baseUrl.collectAsStateWithLifecycle()
    var confirmingSignOut by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        NuvaHeader(
            title = "Me",
            actions = {
                IconButton(onClick = onSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = p.textMuted,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )

        // Identity block
        Column(
            modifier = Modifier
                .padding(horizontal = NuvaSpace.gutter)
                .padding(top = NuvaSpace.sm, bottom = NuvaSpace.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NuvaAvatar(
                name = session?.displayName?.ifBlank { session?.username.orEmpty() } ?: "?",
                seed = session?.userId.orEmpty(),
                size = 104.dp,
            )
            Spacer(Modifier.height(NuvaSpace.md))
            Text(
                text = session?.displayName?.ifBlank { session?.username.orEmpty() } ?: "Signed out",
                style = MaterialTheme.typography.headlineSmall,
                color = p.text,
            )
            Text(
                text = "@${session?.username.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                color = p.textMuted,
            )
            Spacer(Modifier.height(NuvaSpace.md))
            NuvaGhostButton(
                text = "Edit profile",
                icon = Icons.Filled.Edit,
                onClick = onEdit,
                modifier = Modifier.padding(horizontal = NuvaSpace.huge),
            )
        }

        NuvaSectionLabel("Identity")
        NuvaCard(modifier = Modifier.padding(horizontal = NuvaSpace.gutter)) {
            Column {
                NuvaRow(
                    title = "User id",
                    subtitle = "Permanent. Survives every rename.",
                    showChevron = false,
                    trailing = { NuvaMonoValue(session?.userId?.take(8).orEmpty()) },
                )
                NuvaHairline()
                NuvaRow(
                    title = "Server",
                    subtitle = "Your account exists only here",
                    showChevron = false,
                    trailing = { NuvaMonoValue(ServerUrl.hostOf(server.orEmpty())) },
                )
            }
        }

        NuvaSectionLabel("Security")
        NuvaCard(modifier = Modifier.padding(horizontal = NuvaSpace.gutter)) {
            Column {
                NuvaRow(
                    title = "Devices and sessions",
                    subtitle = "Sprint 1: see and revoke every signed-in device",
                    icon = Icons.Filled.Devices,
                    onClick = null,
                    showChevron = false,
                    trailing = { SoonTag() },
                )
                NuvaHairline()
                NuvaRow(
                    title = "Recovery code",
                    subtitle = "Sprint 1: sign in again after a forgotten password",
                    icon = Icons.Filled.Shield,
                    onClick = null,
                    showChevron = false,
                    trailing = { SoonTag() },
                )
                NuvaHairline()
                NuvaRow(
                    title = "Diagnostics",
                    subtitle = "Live socket, server version, raw frames",
                    icon = Icons.Filled.BugReport,
                    onClick = onSettings,
                )
            }
        }

        Spacer(Modifier.height(NuvaSpace.xl))

        val scope = androidx.compose.runtime.rememberCoroutineScope()
        NuvaGhostButton(
            text = if (confirmingSignOut) "Tap again to sign out" else "Sign out",
            icon = Icons.AutoMirrored.Filled.Logout,
            danger = true,
            onClick = {
                if (confirmingSignOut) {
                    // Application scope on purpose: signing out must finish even
                    // though this composition is about to be torn down.
                    ServiceLocator.applicationScope.launch {
                        ServiceLocator.realtime.disconnect()
                        ServiceLocator.authRepository.logout()
                    }
                } else {
                    confirmingSignOut = true
                    scope.launch {
                        kotlinx.coroutines.delay(4_000)
                        confirmingSignOut = false
                    }
                }
            },
            modifier = Modifier.padding(horizontal = NuvaSpace.gutter),
        )
        Text(
            text = "Signing out keeps your account on the server. Your recovery code " +
                "and password get you back in.",
            style = MaterialTheme.typography.bodySmall,
            color = p.textFaint,
            modifier = Modifier.padding(horizontal = NuvaSpace.gutter, vertical = NuvaSpace.sm),
        )

        Spacer(Modifier.height(bottomInset + NuvaSpace.huge))
    }
}

/** Marks a real, planned feature. Never used to hide something we will not build. */
@Composable
internal fun SoonTag() {
    val p = NuvaTheme.palette
    Text(
        text = "soon",
        style = MaterialTheme.typography.labelSmall,
        color = p.amber,
        modifier = Modifier
            .androidxClip()
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Small helper so the tag styling stays in one place. */
@Composable
private fun Modifier.androidxClip(): Modifier {
    val p = NuvaTheme.palette
    return this
        .androidx.compose.ui.draw.clip(androidx.compose.foundation.shape.CircleShape)
        .androidx.compose.foundation.background(p.amber.copy(alpha = 0.14f))
}
