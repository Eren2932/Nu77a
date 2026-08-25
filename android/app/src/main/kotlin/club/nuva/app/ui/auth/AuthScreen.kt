package club.nuva.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import club.nuva.app.ui.components.ErrorBannerHost
import club.nuva.app.ui.components.RecoveryCodeDialog
import club.nuva.app.ui.design.NuvaButton
import club.nuva.app.ui.design.NuvaCanvas
import club.nuva.app.ui.design.NuvaField
import club.nuva.app.ui.design.NuvaSegmented
import club.nuva.app.ui.theme.NuvaSpace
import club.nuva.app.ui.theme.NuvaTheme

/**
 * Sign in / create account.
 *
 * One screen, one segmented control, no separate registration flow to keep in
 * sync. Errors appear inline above the form and stay until dismissed. The
 * recovery-code dialog is the only modal in the whole app, because it is the
 * only moment where losing the information loses the account.
 *
 * The ViewModel contract is untouched from sprint 0 — this is a restyle, and
 * auth logic that already worked on a real device does not get rewritten for
 * cosmetics.
 */
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
) {
    val p = NuvaTheme.palette
    val state by viewModel.state.collectAsStateWithLifecycle()
    val registering = state.mode == AuthViewModel.Mode.Register

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

            // Wordmark. The rounded square holding a single letter is the same
            // mark as the launcher icon, so the app is recognisable from the
            // first frame after the icon is tapped.
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(p.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "N",
                    style = MaterialTheme.typography.headlineLarge,
                    color = p.accentInk,
                )
            }

            Spacer(Modifier.height(NuvaSpace.xl))
            Text(
                text = if (registering) "Create an account" else "Welcome back",
                style = MaterialTheme.typography.headlineLarge,
                color = p.text,
            )
            Spacer(Modifier.height(NuvaSpace.xs))
            Text(
                text = if (registering) {
                    "No phone number, no email. A username and a password are enough, " +
                        "and they exist only on this server."
                } else {
                    "Sign in with the username you created on this server."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = p.textMuted,
            )

            Spacer(Modifier.height(NuvaSpace.xl))
            NuvaSegmented(
                options = listOf("Sign in", "Create account"),
                selectedIndex = if (registering) 1 else 0,
                onSelect = { index ->
                    viewModel.setMode(
                        if (index == 1) AuthViewModel.Mode.Register else AuthViewModel.Mode.SignIn,
                    )
                },
            )

            Spacer(Modifier.height(NuvaSpace.lg))
            ErrorBannerHost(
                message = state.errorMessage,
                onDismiss = viewModel::dismissError,
            )

            Spacer(Modifier.height(NuvaSpace.md))
            NuvaField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = "Username",
                placeholder = "lowercase, digits, underscore",
                leadingIcon = Icons.Filled.AlternateEmail,
                mono = true,
                enabled = !state.isBusy,
                // Only complain once there is something to complain about.
                errorText = state.username.takeIf { it.isNotEmpty() }?.let { state.usernameError },
                supporting = if (registering) "Permanent. Choose one you will still like." else null,
            )

            AnimatedVisibility(
                visible = registering,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(120)),
            ) {
                Column {
                    Spacer(Modifier.height(NuvaSpace.md))
                    NuvaField(
                        value = state.displayName,
                        onValueChange = viewModel::onDisplayNameChange,
                        label = "Display name",
                        placeholder = "How people see you",
                        leadingIcon = Icons.Filled.Badge,
                        enabled = !state.isBusy,
                        supporting = "You can change this any time.",
                    )
                }
            }

            Spacer(Modifier.height(NuvaSpace.md))
            NuvaField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Password",
                leadingIcon = Icons.Filled.Lock,
                isPassword = true,
                enabled = !state.isBusy,
                errorText = state.password.takeIf { it.isNotEmpty() }?.let { state.passwordError },
                supporting = if (registering) {
                    "Nobody can reset this for you. That is the trade for having no email."
                } else {
                    null
                },
            )

            Spacer(Modifier.height(NuvaSpace.xl))
            NuvaButton(
                text = if (registering) "Create account" else "Sign in",
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                busy = state.isBusy,
            )

            Spacer(Modifier.height(NuvaSpace.lg))
            Text(
                text = "Nuva never asks for a phone number, and there is no central " +
                    "directory. Whoever runs this server is the only party you trust.",
                style = MaterialTheme.typography.bodySmall,
                color = p.textFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(NuvaSpace.huge))
        }
    }

    val code = state.recoveryCode
    if (code != null) {
        RecoveryCodeDialog(
            code = code,
            onAcknowledged = viewModel::dismissRecoveryCode,
        )
    }
}
