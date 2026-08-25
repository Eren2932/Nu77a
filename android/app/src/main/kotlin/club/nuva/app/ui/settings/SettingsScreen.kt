package club.nuva.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import club.nuva.app.BuildConfig
import club.nuva.app.data.local.UiPrefs
import club.nuva.app.di.ServiceLocator
import club.nuva.app.ui.design.NuvaCard
import club.nuva.app.ui.design.NuvaHairline
import club.nuva.app.ui.design.NuvaHeader
import club.nuva.app.ui.design.NuvaMonoValue
import club.nuva.app.ui.design.NuvaRow
import club.nuva.app.ui.design.NuvaSectionLabel
import club.nuva.app.ui.design.NuvaSegmented
import club.nuva.app.ui.design.NuvaSwitchRow
import club.nuva.app.ui.profile.SoonTag
import club.nuva.app.ui.theme.NuvaSpace
import club.nuva.app.ui.theme.NuvaTheme
import club.nuva.app.util.ServerUrl

/**
 * Settings.
 *
 * Grouped by what the user is trying to do, not by which layer of the code
 * owns it. Everything here takes effect immediately and is persisted at the
 * moment of the tap: no Save button, so there is no half-applied state to
 * debug later.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSwitchServer: () -> Unit,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = NuvaTheme.palette
    val prefs = ServiceLocator.uiPrefs
    val ui by prefs.state.collectAsStateWithLifecycle()
    val server by ServiceLocator.serverRepository.baseUrl.collectAsStateWithLifecycle()

    val themeOptions = listOf("System", "Dark", "Light")
    val themeIndex = when (ui.themeMode) {
        UiPrefs.ThemeMode.System -> 0
        UiPrefs.ThemeMode.Dark -> 1
        UiPrefs.ThemeMode.Light -> 2
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        NuvaHeader(title = "Settings", compact = true, onBack = onBack)

        NuvaSectionLabel("Appearance")
        Column(modifier = Modifier.padding(horizontal = NuvaSpace.gutter)) {
            NuvaSegmented(
                options = themeOptions,
                selectedIndex = themeIndex,
                onSelect = { index ->
                    prefs.setThemeMode(
                        when (index) {
                            1 -> UiPrefs.ThemeMode.Dark
                            2 -> UiPrefs.ThemeMode.Light
                            else -> UiPrefs.ThemeMode.System
                        },
                    )
                },
            )
        }
        Spacer(Modifier.height(NuvaSpace.md))
        NuvaCard(modifier = Modifier.padding(horizontal = NuvaSpace.gutter)) {
            Column {
                NuvaSwitchRow(
                    title = "Compact chat list",
                    subtitle = "Fit more conversations on screen",
                    icon = Icons.Filled.ViewCompact,
                    checked = ui.compactChats,
                    onCheckedChange = prefs::setCompactChats,
                )
                NuvaHairline()
                NuvaSwitchRow(
                    title = "Enter sends the message",
                    subtitle = "Off means Enter starts a new line",
                    icon = Icons.Filled.Keyboard,
                    checked = ui.sendOnEnter,
                    onCheckedChange = prefs::setSendOnEnter,
                )
            }
        }

        NuvaSectionLabel("Server")
        NuvaCard(modifier = Modifier.padding(horizontal = NuvaSpace.gutter)) {
            Column {
                NuvaRow(
                    title = "Connected to",
                    subtitle = "The only place your account and messages live",
                    icon = Icons.Filled.Dns,
                    showChevron = false,
                    trailing = { NuvaMonoValue(ServerUrl.hostOf(server.orEmpty())) },
                )
                NuvaHairline()
                NuvaRow(
                    title = "Switch server",
                    subtitle = "Signs you out of this one first",
                    icon = Icons.Filled.SwapHoriz,
                    tint = p.amber,
                    onClick = onSwitchServer,
                )
                NuvaHairline()
                NuvaRow(
                    title = "Diagnostics",
                    subtitle = "Socket state, server version, raw frames",
                    icon = Icons.Filled.BugReport,
                    onClick = onDiagnostics,
                )
            }
        }

        NuvaSectionLabel("Notifications")
        NuvaCard(modifier = Modifier.padding(horizontal = NuvaSpace.gutter)) {
            NuvaRow(
                title = "Push notifications",
                subtitle = "Sprint 4: self-hosted, no Google account required",
                icon = Icons.Filled.Notifications,
                showChevron = false,
                trailing = { SoonTag() },
            )
        }

        NuvaSectionLabel("About")
        NuvaCard(modifier = Modifier.padding(horizontal = NuvaSpace.gutter)) {
            Column {
                NuvaRow(
                    title = "Version",
                    showChevron = false,
                    trailing = {
                        NuvaMonoValue("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    },
                )
                NuvaHairline()
                NuvaRow(
                    title = "License",
                    subtitle = "AGPL-3.0. Run your own server, change anything.",
                    icon = Icons.Filled.Gavel,
                    showChevron = false,
                )
            }
        }

        Spacer(Modifier.height(NuvaSpace.xl))
        Text(
            text = "Your words. Your rules.",
            style = MaterialTheme.typography.labelMedium,
            color = p.textFaint,
            textAlign = TextAlign.Center,
            // fillMaxWidth, never fillMaxSize: this Text lives inside a
            // verticalScroll, where an unbounded height is a crash waiting.
            modifier = Modifier
                .padding(horizontal = NuvaSpace.gutter)
                .fillMaxWidth(),
        )
        Spacer(Modifier.height(NuvaSpace.huge))
    }
}
