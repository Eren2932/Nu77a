package club.nuva.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import club.nuva.app.di.ServiceLocator
import club.nuva.app.ui.auth.AuthScreen
import club.nuva.app.ui.auth.AuthViewModel
import club.nuva.app.ui.server.ServerScreen
import club.nuva.app.ui.server.ServerViewModel
import club.nuva.app.ui.shell.NuvaShell
import club.nuva.app.ui.onboarding.WelcomeScreen
import club.nuva.app.ui.theme.NuvaTheme
import club.nuva.app.util.ServerUrl

/**
 * Root of the UI.
 *
 * Navigation is driven by two facts only: is a server configured, and is there
 * a stored session. There is no back stack that could show a chat list to a
 * signed-out user, and no way to reach the sign-in form without a server that
 * has actually answered. Unchanged from sprint 0 on purpose — this part was
 * proven on a real device, so the restyle does not touch it.
 */
@Composable
fun NuvaApp() {
    val server by ServiceLocator.serverRepository.baseUrl.collectAsStateWithLifecycle()
    val session by ServiceLocator.authRepository.session.collectAsStateWithLifecycle()

    // Set only when the user asks to switch servers while one is configured.
    var switchingServer by rememberSaveable { mutableStateOf(false) }

    val prefs by ServiceLocator.uiPrefs.state.collectAsStateWithLifecycle()

    val destination = when {
        // New installs only: someone already signed in must never be shown an
        // intro again after an app update.
        !prefs.welcomeSeen && session == null -> Destination.Welcome
        server == null || switchingServer -> Destination.Server
        session != null -> Destination.Home
        else -> Destination.Auth
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = NuvaTheme.palette.canvas,
    ) {
        Crossfade(
            targetState = destination,
            animationSpec = tween(220),
            label = "root",
        ) { current ->
            when (current) {
                Destination.Welcome -> {
                    WelcomeScreen(onDone = { ServiceLocator.uiPrefs.setWelcomeSeen() })
                }

                Destination.Server -> {
                    val serverViewModel: ServerViewModel = viewModel(
                        // Keyed by the server being replaced, so reopening the
                        // picker never shows a stale probe result.
                        key = "server-${server.orEmpty()}",
                        factory = viewModelFactory {
                            initializer { ServerViewModel(ServiceLocator.serverRepository) }
                        },
                    )
                    ServerScreen(
                        viewModel = serverViewModel,
                        onConnected = { switchingServer = false },
                        onCancel = if (server != null) {
                            { switchingServer = false }
                        } else {
                            null
                        },
                    )
                }

                Destination.Auth -> {
                    val authViewModel: AuthViewModel = viewModel(
                        key = "auth-${server.orEmpty()}",
                        factory = viewModelFactory {
                            initializer { AuthViewModel(ServiceLocator.authRepository) }
                        },
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
                        AuthScreen(viewModel = authViewModel)
                        ServerFooter(
                            host = ServerUrl.hostOf(server.orEmpty()),
                            onChange = { switchingServer = true },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),
                        )
                    }
                }

                Destination.Home -> {
                    NuvaShell(onSwitchServer = { switchingServer = true })
                }
            }
        }
    }
}

private enum class Destination { Welcome, Server, Auth, Home }

/** Always visible on the sign-in screen: you should never wonder who you are trusting. */
@Composable
private fun ServerFooter(
    host: String,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(onClick = onChange) {
            Text(
                text = "Server: $host  ·  change",
                style = MaterialTheme.typography.labelMedium,
                color = NuvaTheme.palette.textMuted,
            )
        }
    }
}
