package club.nuva.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import club.nuva.app.di.ServiceLocator
import club.nuva.app.ui.auth.AuthScreen
import club.nuva.app.ui.auth.AuthViewModel
import club.nuva.app.ui.home.HomeScreen
import club.nuva.app.ui.home.HomeViewModel

/**
 * Root of the UI.
 *
 * Navigation is driven by one fact only: is there a stored session? That makes
 * an expired session impossible to get stuck in, and there is no back stack
 * that could ever show the chat list to a signed-out user.
 */
@Composable
fun NuvaApp() {
    val session by ServiceLocator.authRepository.session.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Crossfade(
            targetState = session != null,
            animationSpec = tween(220),
            label = "root",
        ) { signedIn ->
            if (signedIn) {
                val homeViewModel: HomeViewModel = viewModel(
                    // Keyed by user id: switching accounts builds a fresh graph
                    // instead of leaking the previous user's state.
                    key = "home-${session?.userId}",
                    factory = viewModelFactory {
                        initializer {
                            HomeViewModel(
                                authRepository = ServiceLocator.authRepository,
                                api = ServiceLocator.api,
                                realtime = ServiceLocator.realtime,
                            )
                        }
                    },
                )
                HomeScreen(viewModel = homeViewModel)
            } else {
                val authViewModel: AuthViewModel = viewModel(
                    key = "auth",
                    factory = viewModelFactory {
                        initializer { AuthViewModel(ServiceLocator.authRepository) }
                    },
                )
                AuthScreen(viewModel = authViewModel)
            }
        }
    }
}
